"""Private participant intake storage and a bounded, serial photo-measurement queue."""
import asyncio
import hashlib
import hmac
import json
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
from security import read_photo

ASSETS = {
    'top_original': ('top.original', None), 'side_original': ('side.original', None),
    'top': ('top.jpg', 'image/jpeg'), 'side': ('side.jpg', 'image/jpeg'),
    'top_overlay': ('top_overlay.jpg', 'image/jpeg'), 'side_overlay': ('side_overlay.jpg', 'image/jpeg'),
    'report': ('report.jpg', 'image/jpeg'), 'csv': ('dims.csv', 'text/csv; charset=utf-8'),
}
MAX_PENDING = 20
MAX_RECORDS = 500

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
            c.execute("UPDATE records SET status='failed', message=?, updated_at=? WHERE status IN ('queued','processing')",
                      ('서버가 재시작되어 분석이 중단됐어요. 사진을 다시 제출해 주세요.', now()))
        self.db.chmod(0o600)
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
        return record
    def public_record(self, record):
        return {k:v for k,v in record.items() if k not in ('receipt_hash','top_type','side_type')}
    def listing(self, q='', limit=100, offset=0):
        with self.connect() as c:
            c.row_factory=sqlite3.Row
            query='%' + q.replace('\\','\\\\').replace('%','\\%').replace('_','\\_') + '%'
            where="WHERE display_name LIKE ? ESCAPE '\\' OR id LIKE ? ESCAPE '\\'"
            count=c.execute('SELECT COUNT(*) FROM records '+where,(query,query)).fetchone()[0]
            rows=c.execute('SELECT id,display_name,hand,created_at,updated_at,status,message FROM records '+where+
                           ' ORDER BY created_at DESC LIMIT ? OFFSET ?',(query,query,limit,offset)).fetchall()
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
                c.execute('INSERT INTO records VALUES (?,?,?,?,?,?,?,?,?,?,?)',
                          (id,display_name,hand,stamp,stamp,'queued',hashlib.sha256(receipt.encode()).hexdigest(),
                           '사진이 접수됐어요. 순서대로 분석하고 있어요.',None,*types))
        except BaseException:
            shutil.rmtree(directory,ignore_errors=True)
            raise
        return {'id':id,'receipt':receipt,'status':'queued'}
    def update(self,id,status,message,result=None):
        with self.connect() as c:
            c.execute('UPDATE records SET status=?,message=?,result=?,updated_at=? WHERE id=?',
                      (status,message,json.dumps(result,ensure_ascii=False,allow_nan=False) if result else None,now(),id))
    def delete(self,id):
        record=self.get(id)
        if record['status'] in ('queued','processing'):
            raise HTTPException(409,'분석 대기 또는 진행 중인 접수는 완료 후 삭제해 주세요.')
        # Delete files first; a filesystem failure keeps the DB row visible for a retry.
        directory=self.root/id
        if directory.exists():shutil.rmtree(directory)
        with self.connect() as c:c.execute('DELETE FROM records WHERE id=?',(id,))

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
                result=await self.measure(self.store.root/id,id)
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
    return {k:record[k] for k in ('id','status','message','updated_at')}

@router.get('/admin/intakes')
def listing(request:Request,q:str='',limit:int=100,offset:int=0):
    return service(request).store.listing(q[:80],max(1,min(limit,200)),max(0,offset))

@router.get('/admin/intakes/{id}')
def detail(request:Request,id:str):
    store=service(request).store
    record=store.public_record(store.get(id))
    record['assets']=[key for key,(filename,_) in ASSETS.items() if (store.root/id/filename).is_file()]
    return record

@router.get('/admin/intakes/{id}/assets/{asset}')
def asset(request:Request,id:str,asset:str):
    store=service(request).store;record=store.get(id)
    if asset not in ASSETS:raise HTTPException(404,'파일을 찾을 수 없어요.')
    name,mime=ASSETS[asset];path=store.root/id/name
    if not path.is_file():raise HTTPException(404,'파일을 찾을 수 없어요.')
    mime=mime or record['top_type' if asset.startswith('top') else 'side_type']
    extension={'image/heic':'.heic','image/png':'.png','image/jpeg':'.jpg'}.get(mime,'.csv')
    return FileResponse(path,media_type=mime,filename=id+'_'+asset+extension,content_disposition_type='inline')

@router.delete('/admin/intakes/{id}')
def delete(request:Request,id:str):
    service(request).store.delete(id)
    return {'ok':True}
