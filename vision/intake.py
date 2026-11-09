"""Private participant intake storage and a bounded, serial photo-measurement queue."""
import asyncio
import hashlib
import hmac
import json
import math
import re
import secrets
import shutil
import sqlite3
import uuid
from datetime import datetime, timezone
from contextlib import contextmanager
from pathlib import Path
from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from security import read_photo, decode_photo

ASSETS = {
    'top_original': ('top.original', None), 'side_original': ('side.original', None),
    'top': ('top.jpg', 'image/jpeg'), 'side': ('side.jpg', 'image/jpeg'),
    'top_overlay': ('top_overlay.jpg', 'image/jpeg'), 'side_overlay': ('side_overlay.jpg', 'image/jpeg'),
    'report': ('report.jpg', 'image/jpeg'), 'csv': ('dims.csv', 'text/csv; charset=utf-8'),
}
MAX_PENDING = 20
MAX_RECORDS = 500
MAX_REVISIONS = 20

def now():
    return datetime.now(timezone.utc).isoformat()

def validate_id(value):
    if not re.fullmatch(r'[a-f0-9]{32}', value):
        raise HTTPException(400, '잘못된 접수번호예요.')
    return value

class IntakeStore:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.db = self.root / 'intake.sqlite'
        with self.connect() as c:
            c.execute('''CREATE TABLE IF NOT EXISTS records (
                id TEXT PRIMARY KEY, display_name TEXT NOT NULL, hand TEXT NOT NULL,
                created_at TEXT NOT NULL, updated_at TEXT NOT NULL, status TEXT NOT NULL,
                receipt_hash TEXT NOT NULL, message TEXT NOT NULL, result TEXT,
                top_type TEXT NOT NULL, side_type TEXT NOT NULL)''')
            columns={row[1] for row in c.execute('PRAGMA table_info(records)')}
            for name, declaration in {'revision':'INTEGER NOT NULL DEFAULT 1',
                    'review_status':"TEXT NOT NULL DEFAULT 'unreviewed'",
                    'reviewed_at':'TEXT', 'retake_views':"TEXT NOT NULL DEFAULT '[]'",
                    'retake_reason':"TEXT NOT NULL DEFAULT ''"}.items():
                if name not in columns:c.execute(f'ALTER TABLE records ADD COLUMN {name} {declaration}')
            c.execute('''CREATE TABLE IF NOT EXISTS revisions (
                id TEXT NOT NULL, revision INTEGER NOT NULL, created_at TEXT NOT NULL,
                source TEXT NOT NULL, overrides TEXT NOT NULL, status TEXT NOT NULL,
                message TEXT NOT NULL, result TEXT, review_status TEXT NOT NULL DEFAULT 'unreviewed',
                reviewed_at TEXT, PRIMARY KEY(id,revision))''')
            c.execute('''INSERT OR IGNORE INTO revisions
                (id,revision,created_at,source,overrides,status,message,result,review_status,reviewed_at)
                SELECT id,revision,created_at,'upload','{}',status,message,result,review_status,reviewed_at FROM records''')
            c.execute("UPDATE revisions SET status='failed',message=? WHERE status IN ('queued','processing')",
                      ('서버가 재시작되어 분석이 중단됐어요. 운영자에게 알려 주세요.',))
            c.execute("UPDATE records SET status='failed', message=?, updated_at=? WHERE status IN ('queued','processing')",
                      ('서버가 재시작되어 분석이 중단됐어요. 사진을 다시 제출해 주세요.', now()))
        self.db.chmod(0o600)
        # A crash can leave staged files whose SQLite transaction never committed.
        # Keep those bytes privately quarantined; never replace a committed revision.
        with self.connect() as c:
            committed={}
            for id,revision in c.execute('SELECT id,revision FROM revisions'):
                committed.setdefault(id,set()).add(revision)
        for id,versions in committed.items():
            for directory in (self.root/id/'revisions').glob('*'):
                if directory.is_dir() and directory.name.isdecimal() and int(directory.name) not in versions:
                    self.quarantine(directory)
    @staticmethod
    def quarantine(directory):
        directory.rename(directory.with_name('uncommitted-'+directory.name+'-'+uuid.uuid4().hex))
    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.db, timeout=10)
        try:
            with connection:
                yield connection
        finally:
            connection.close()
    def get(self, id):
        validate_id(id)
        with self.connect() as c:
            c.row_factory=sqlite3.Row
            row=c.execute('SELECT * FROM records WHERE id=?',(id,)).fetchone()
        if row is None: raise HTTPException(404, '접수 내역을 찾을 수 없어요.')
        record=dict(row)
        record['result']=json.loads(record['result']) if record['result'] else None
        record['retake_views']=json.loads(record['retake_views'])
        return record
    def public_record(self, record):
        return {k:v for k,v in record.items() if k not in ('receipt_hash','top_type','side_type')}
    def listing(self, q='', limit=100, offset=0, review=''):
        with self.connect() as c:
            c.row_factory=sqlite3.Row
            query='%' + q.replace('\\','\\\\').replace('%','\\%').replace('_','\\_') + '%'
            where="WHERE (display_name LIKE ? ESCAPE '\\' OR id LIKE ? ESCAPE '\\')"
            args=[query,query]
            if review:
                where+=' AND review_status=?';args.append(review)
            count=c.execute('SELECT COUNT(*) FROM records '+where,args).fetchone()[0]
            rows=c.execute('SELECT id,display_name,hand,created_at,updated_at,status,message,revision,review_status FROM records '+where+
                           ' ORDER BY created_at DESC LIMIT ? OFFSET ?',(*args,limit,offset)).fetchall()
        return {'count':count,'items':[dict(row) for row in rows]}
    def pending_count(self):
        with self.connect() as c:
            return c.execute("SELECT COUNT(*) FROM records WHERE status IN ('queued','processing')").fetchone()[0]
    def create(self, display_name, hand, top, side):
        from io import BytesIO
        from PIL import Image
        id=uuid.uuid4().hex;receipt=secrets.token_urlsafe(32);directory=self.root/id
        types=[]
        for data in (top,side):
            with Image.open(BytesIO(data)) as image:
                types.append('image/heic' if image.format=='HEIF' else 'image/png' if image.format=='PNG' else 'image/jpeg')
        directory.mkdir(mode=0o700)
        try:
            for name,data in (('top.original',top),('side.original',side)):
                p=directory/name;p.write_bytes(data);p.chmod(0o600)
            with self.connect() as c:
                c.execute('BEGIN IMMEDIATE')
                if c.execute('SELECT COUNT(*) FROM records').fetchone()[0]>=MAX_RECORDS:
                    raise HTTPException(429,'접수 보관 한도에 도달했어요. 운영자에게 문의해 주세요.')
                if c.execute("SELECT COUNT(*) FROM records WHERE status IN ('queued','processing')").fetchone()[0]>=MAX_PENDING:
                    raise HTTPException(429,'분석 대기가 많아요. 잠시 뒤 다시 제출해 주세요.')
                stamp=now()
                c.execute('''INSERT INTO records
                    (id,display_name,hand,created_at,updated_at,status,receipt_hash,message,result,top_type,side_type)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)''',
                          (id,display_name,hand,stamp,stamp,'queued',hashlib.sha256(receipt.encode()).hexdigest(),
                           '사진이 접수됐어요. 순서대로 분석하고 있어요.',None,*types))
                c.execute("INSERT INTO revisions (id,revision,created_at,source,overrides,status,message) VALUES (?,1,?,'upload','{}','queued',?)",
                          (id,stamp,'사진이 접수됐어요.'))
        except BaseException:
            shutil.rmtree(directory,ignore_errors=True)
            raise
        return {'id':id,'receipt':receipt,'status':'queued','revision':1}
    def update(self,id,status,message,result=None):
        with self.connect() as c:
            c.execute('UPDATE records SET status=?,message=?,result=?,updated_at=? WHERE id=?',
                      (status,message,json.dumps(result,ensure_ascii=False,allow_nan=False) if result else None,now(),id))
            c.execute('UPDATE revisions SET status=?,message=?,result=? WHERE id=? AND revision=(SELECT revision FROM records WHERE id=?)',
                      (status,message,json.dumps(result,ensure_ascii=False,allow_nan=False) if result else None,id,id))
    def directory(self, id, revision=None):
        record=self.get(id)
        rev=record['revision'] if revision is None else revision
        if not isinstance(rev,int) or not 1<=rev<=record['revision']:
            raise HTTPException(404,'결과 버전을 찾을 수 없어요.')
        return self.root/id if rev==1 else self.root/id/'revisions'/str(rev)
    def history(self,id):
        with self.connect() as c:
            c.row_factory=sqlite3.Row
            rows=c.execute('SELECT revision,created_at,source,status,review_status,reviewed_at FROM revisions WHERE id=? ORDER BY revision DESC',(id,)).fetchall()
        return [dict(row) for row in rows]
    def version(self,id,revision=None):
        record=self.get(id);record['current_revision']=record['revision']
        if revision is not None and revision!=record['revision']:
            with self.connect() as c:
                c.row_factory=sqlite3.Row
                row=c.execute('SELECT revision,status,message,result,review_status,reviewed_at FROM revisions WHERE id=? AND revision=?',(id,revision)).fetchone()
            if row is None:raise HTTPException(404,'결과 버전을 찾을 수 없어요.')
            record.update(dict(row));record['result']=json.loads(record['result']) if record['result'] else None
        record['history']=self.history(id)
        with self.connect() as c:
            row=c.execute('SELECT overrides FROM revisions WHERE id=? AND revision=?',(id,record['revision'])).fetchone()
        record['overrides']=json.loads(row[0])
        return record
    def begin_revision(self,id,expected,overrides,source,replacements=None):
        replacements=replacements or {}
        with self.connect() as c:
            c.execute('BEGIN IMMEDIATE')
            c.row_factory=sqlite3.Row
            row=c.execute('SELECT * FROM records WHERE id=?',(id,)).fetchone()
            if row is None:raise HTTPException(404,'접수 내역을 찾을 수 없어요.')
            self.check_current(dict(row),expected)
            if source=='retake' and (row['review_status']!='retake_requested' or set(json.loads(row['retake_views']))!=set(replacements)):
                raise HTTPException(409,'재촬영 요청이 변경됐어요. 상태를 다시 확인해 주세요.')
            if expected>=MAX_REVISIONS:raise HTTPException(409,'접수당 결과 버전 한도(20개)에 도달했어요.')
            if c.execute("SELECT COUNT(*) FROM records WHERE status IN ('queued','processing')").fetchone()[0]>=MAX_PENDING:
                raise HTTPException(429,'분석 대기가 많아요. 잠시 뒤 다시 시도해 주세요.')
            rev=expected+1;old=self.directory(id,expected);directory=self.root/id/'revisions'/str(rev)
            if directory.exists():self.quarantine(directory)
            directory.mkdir(parents=True,exist_ok=False,mode=0o700)
            try:
                types={v:row[v+'_type'] for v in ('top','side')}
                from io import BytesIO
                from PIL import Image
                for view in ('top','side'):
                    path=directory/(view+'.original')
                    if view in replacements:
                        path.write_bytes(replacements[view])
                        with Image.open(BytesIO(replacements[view])) as image:
                            types[view]='image/heic' if image.format=='HEIF' else 'image/png' if image.format=='PNG' else 'image/jpeg'
                    else:shutil.copyfile(old/(view+'.original'),path)
                    path.chmod(0o600)
                (directory/'corrections.json').write_text(json.dumps(overrides,allow_nan=False),encoding='utf-8')
                stamp=now();message='사진을 접수했어요. 다시 계산하고 있어요.'
                c.execute("INSERT INTO revisions (id,revision,created_at,source,overrides,status,message) VALUES (?,?,?,?,?,'queued',?)",
                          (id,rev,stamp,source,json.dumps(overrides),message))
                c.execute("""UPDATE records SET revision=?,status='queued',message=?,result=NULL,updated_at=?,
                    review_status='unreviewed',reviewed_at=NULL,retake_views='[]',retake_reason='',top_type=?,side_type=? WHERE id=?""",
                          (rev,message,stamp,types['top'],types['side'],id))
            except BaseException:
                shutil.rmtree(directory,ignore_errors=True);raise
        return rev
    @staticmethod
    def check_current(record,expected):
        if record['revision']!=expected:raise HTTPException(409,'새 결과가 있어요. 기록을 새로 불러온 뒤 시도해 주세요.')
        if record['status'] in ('queued','processing'):raise HTTPException(409,'분석이 끝난 뒤 시도해 주세요.')
    def set_review(self,id,expected,state,views=None,reason=''):
        with self.connect() as c:
            c.execute('BEGIN IMMEDIATE');c.row_factory=sqlite3.Row
            row=c.execute('SELECT * FROM records WHERE id=?',(id,)).fetchone()
            if row is None:raise HTTPException(404,'접수 내역을 찾을 수 없어요.')
            record=dict(row);self.check_current(record,expected)
            if state=='reviewed':
                result=json.loads(record['result'] or '{}');dims=result.get('dims_mm') or {}
                if record['status']!='complete' or result.get('ok') is not True or not all(
                        isinstance(dims.get(str(i)),(int,float)) and not isinstance(dims[str(i)],bool)
                        and math.isfinite(dims[str(i)]) and dims[str(i)]>0 for i in range(1,12)):
                    raise HTTPException(409,'11개 치수가 모두 계산된 현재 결과만 검토 완료할 수 있어요.')
            stamp=now();reviewed=stamp if state=='reviewed' else None
            c.execute('UPDATE records SET review_status=?,reviewed_at=?,retake_views=?,retake_reason=?,updated_at=? WHERE id=?',
                      (state,reviewed,json.dumps(views or []),reason,stamp,id))
            c.execute('UPDATE revisions SET review_status=?,reviewed_at=? WHERE id=? AND revision=?',(state,reviewed,id,expected))
    def delete(self,id):
        record=self.get(id)
        if record['status'] in ('queued','processing'):
            raise HTTPException(409,'분석 대기 또는 진행 중인 접수는 완료 후 삭제해 주세요.')
        # Delete files first; a filesystem failure keeps the DB row visible for a retry.
        directory=self.root/id
        if directory.exists():shutil.rmtree(directory)
        with self.connect() as c:
            c.execute('DELETE FROM revisions WHERE id=?',(id,))
            c.execute('DELETE FROM records WHERE id=?',(id,))

class IntakeService:
    def __init__(self,root,measure):
        self.store=IntakeStore(root)
        self.measure=measure
        self.queue=asyncio.Queue(maxsize=MAX_PENDING)
        self.task=asyncio.create_task(self.work())
    async def close(self):
        self.task.cancel()
        try:await self.task
        except asyncio.CancelledError:pass
    async def work(self):
        while True:
            id=await self.queue.get()
            try:
                self.store.update(id,'processing','사진에서 손과 카드를 분석하고 있어요.')
                result=await self.measure(self.store.directory(id),id)
                status='complete' if result.get('ok') is True else 'failed'
                self.store.update(id,status,'측정이 완료됐어요. 운영자가 결과를 확인할 수 있어요.' if status=='complete'
                                  else str(result.get('message') or '측정하지 못했어요. 촬영 안내를 보고 다시 제출해 주세요.'),result)
            except asyncio.CancelledError:
                self.store.update(id,'failed','서버 종료로 분석이 중단됐어요. 다시 제출해 주세요.')
                raise
            except Exception:
                self.store.update(id,'failed','사진 분석 중 오류가 발생했어요. 운영자에게 접수번호를 알려 주세요.')
            finally:self.queue.task_done()

router=APIRouter()
def service(request):return request.app.state.intake

@router.post('/intake',status_code=202)
async def submit(request:Request,display_name:str=Form(...),hand:str=Form(...),consent:bool=Form(False),
                 top_image:UploadFile=File(...),side_image:UploadFile=File(...)):
    name=display_name.strip()
    if not consent:raise HTTPException(400,'사진과 측정 정보 보관에 동의한 뒤 제출해 주세요.')
    if not name or len(name)>40 or any(ord(c)<32 or ord(c)==127 for c in name):
        raise HTTPException(400,'이름 또는 별명을 1~40자로 입력해 주세요.')
    if hand not in ('left','right'):raise HTTPException(400,'측정할 손을 선택해 주세요.')
    s=service(request)
    if s.queue.full() or s.store.pending_count()>=MAX_PENDING:
        raise HTTPException(429,'분석 대기가 많아요. 잠시 뒤 다시 제출해 주세요.')
    top=await read_photo(top_image);side=await read_photo(side_image)
    if top==side:raise HTTPException(400,'손등과 측면에 같은 사진을 선택했어요. 서로 다른 두 사진을 제출해 주세요.')
    record=s.store.create(name,hand,top,side)
    # No await between committing the queued record and enqueueing it.
    s.queue.put_nowait(record['id'])
    return record

class Receipt(BaseModel):
    id:str=Field(max_length=64)
    receipt:str=Field(max_length=256)

@router.post('/intake/status')
def status(request:Request,receipt:Receipt):
    record=service(request).store.get(receipt.id)
    digest=hashlib.sha256(receipt.receipt.encode()).hexdigest()
    if not hmac.compare_digest(record['receipt_hash'],digest):
        raise HTTPException(404,'접수 내역을 찾을 수 없어요.')
    return {k:record[k] for k in ('id','status','message','updated_at','revision','review_status','retake_views','retake_reason')}

@router.get('/admin/intakes')
def listing(request:Request,q:str='',limit:int=100,offset:int=0,review:str=''):
    if review not in ('','unreviewed','reviewed','retake_requested'):raise HTTPException(400,'잘못된 검토 상태예요.')
    return service(request).store.listing(q[:80],max(1,min(limit,200)),max(0,offset),review)

@router.get('/admin/intakes/{id}')
def detail(request:Request,id:str,revision:int|None=None):
    store=service(request).store
    record=store.public_record(store.version(id,revision))
    record['assets']=[key for key,(filename,_) in ASSETS.items() if (store.directory(id,record['revision'])/filename).is_file()]
    return record

@router.get('/admin/intakes/{id}/assets/{asset}')
def asset(request:Request,id:str,asset:str,revision:int|None=None):
    store=service(request).store;record=store.get(id)
    if asset not in ASSETS:raise HTTPException(404,'파일을 찾을 수 없어요.')
    name,mime=ASSETS[asset];path=store.directory(id,revision)/name
    if not path.is_file():raise HTTPException(404,'파일을 찾을 수 없어요.')
    if mime is None:
        from PIL import Image
        with Image.open(path) as image:
            mime='image/heic' if image.format=='HEIF' else 'image/png' if image.format=='PNG' else 'image/jpeg'
    extension={'image/heic':'.heic','image/png':'.png','image/jpeg':'.jpg'}.get(mime,'.csv')
    return FileResponse(path,media_type=mime,filename=id+'_'+asset+extension,content_disposition_type='inline')

@router.delete('/admin/intakes/{id}')
def delete(request:Request,id:str):
    service(request).store.delete(id)
    return {'ok':True}

class RevisionInput(BaseModel):
    revision:int=Field(ge=1,le=MAX_REVISIONS,strict=True)

class CorrectionInput(RevisionInput):
    overrides:dict=Field(default_factory=dict)

class ReviewInput(RevisionInput):
    acknowledged:bool=False

class RetakeInput(RevisionInput):
    views:list[str]=Field(min_length=1,max_length=2)
    reason:str=Field(min_length=1,max_length=300)

def checked_overrides(overrides,directory):
    """Only finite, in-frame wrist/card coordinates may reach native measurement."""
    allowed={kind+'_'+view for kind in ('card','wrist') for view in ('top','side')}
    if set(overrides)-allowed:raise HTTPException(400,'카드 모서리와 손목 위치만 보정할 수 있어요.')
    for key,points in overrides.items():
        if points is None:continue
        image=decode_photo((directory/(key.split('_')[1]+'.original')).read_bytes())
        if image is None:raise HTTPException(400,'사진을 읽지 못했어요.')
        h,w=image.shape[:2]
        pairs=points if key.startswith('card') else [points]
        if not isinstance(pairs,list) or len(pairs)!=(4 if key.startswith('card') else 1):
            raise HTTPException(400,'카드는 네 모서리, 손목은 한 점을 선택해 주세요.')
        for p in pairs:
            if not isinstance(p,list) or len(p)!=2 or not all(type(v) in (int,float) and math.isfinite(v) for v in p) or not (0<=p[0]<w and 0<=p[1]<h):
                raise HTTPException(400,'사진 안의 올바른 좌표를 선택해 주세요.')
        if key.startswith('card'):
            import numpy as np
            import cv2
            a=np.asarray(pairs,dtype=np.float32)
            hull=cv2.convexHull(a)
            if len(hull)!=4 or cv2.contourArea(hull)<4:
                raise HTTPException(400,'카드의 서로 다른 네 모서리를 선택해 주세요.')
    return {key:value for key,value in overrides.items() if value is not None}

@router.post('/admin/intakes/{id}/recalculate',status_code=202)
async def recalculate(request:Request,id:str,body:CorrectionInput):
    s=service(request);record=s.store.get(id);s.store.check_current(record,body.revision)
    if s.queue.full():raise HTTPException(429,'분석 대기가 많아요.')
    corrections=checked_overrides(body.overrides,s.store.directory(id))
    revision=s.store.begin_revision(id,body.revision,corrections,'correction')
    s.queue.put_nowait(id)
    return {'id':id,'revision':revision,'status':'queued'}

@router.post('/admin/intakes/{id}/review')
def review(request:Request,id:str,body:ReviewInput):
    if not body.acknowledged:raise HTTPException(400,'사진과 측정선을 대조했다는 항목에 체크해 주세요.')
    service(request).store.set_review(id,body.revision,'reviewed')
    return {'ok':True,'revision':body.revision}

@router.post('/admin/intakes/{id}/retake-request')
def request_retake(request:Request,id:str,body:RetakeInput):
    views=list(dict.fromkeys(body.views));reason=body.reason.strip()
    if any(v not in ('top','side') for v in views) or not reason or any(ord(c)<32 for c in reason):
        raise HTTPException(400,'재촬영할 사진과 이유를 입력해 주세요.')
    service(request).store.set_review(id,body.revision,'retake_requested',views,reason)
    return {'ok':True,'revision':body.revision}

@router.post('/intake/retake',status_code=202)
async def retake(request:Request,id:str=Form(...),receipt:str=Form(...),revision:int=Form(...),
                 consent:bool=Form(False),top_image:UploadFile|None=File(None),side_image:UploadFile|None=File(None)):
    s=service(request);record=s.store.get(id)
    if not hmac.compare_digest(record['receipt_hash'],hashlib.sha256(receipt.encode()).hexdigest()):
        raise HTTPException(404,'접수 내역을 찾을 수 없어요.')
    s.store.check_current(record,revision)
    if not consent:raise HTTPException(400,'사진 보관에 동의해 주세요.')
    if record['review_status']!='retake_requested':raise HTTPException(409,'재촬영 요청이 없어요. 운영자에게 확인해 주세요.')
    uploads={'top':top_image,'side':side_image}
    if {v for v,photo in uploads.items() if photo is not None}!=set(record['retake_views']):
        raise HTTPException(400,'운영자가 요청한 사진만 모두 제출해 주세요.')
    replacements={v:await read_photo(uploads[v]) for v in record['retake_views']}
    if s.queue.full():raise HTTPException(429,'분석 대기가 많아요.')
    old=s.store.directory(id)
    pair=[replacements.get(v) or (old/(v+'.original')).read_bytes() for v in ('top','side')]
    if pair[0]==pair[1]:raise HTTPException(400,'손등과 측면은 서로 다른 사진이어야 해요.')
    previous=s.store.version(id)['overrides']
    corrections={k:v for k,v in previous.items() if k.split('_')[1] not in replacements}
    new_revision=s.store.begin_revision(id,revision,corrections,'retake',replacements)
    s.queue.put_nowait(id)
    return {'id':id,'revision':new_revision,'status':'queued'}
