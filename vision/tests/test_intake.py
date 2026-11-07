import os
import tempfile
import time
import unittest
from io import BytesIO
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
from PIL import Image
import server

UPLOAD={'Authorization':'Bearer '+'A'*48}
ADMIN={'Authorization':'Bearer '+'B'*48}

def photo(color='white'):
    b=BytesIO();Image.new('RGB',(48,32),color).save(b,'JPEG');return b.getvalue()

class IntakeFlow(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.env=patch.dict(os.environ,{'REFIT_VISION_TOKEN':'A'*48,'REFIT_VISION_ADMIN_TOKEN':'B'*48})
        self.env.start()
        self.path=patch.object(server,'LOG_DIR',Path(self.temp.name));self.path.start()
        self.compute=patch.object(server,'process_intake',self.failed_measurement,create=True);self.compute.start()
        self.client=TestClient(server.app);self.client.__enter__()
    def tearDown(self):
        self.client.__exit__(None,None,None);self.compute.stop();self.path.stop();self.env.stop();self.temp.cleanup()
    @staticmethod
    async def failed_measurement(directory,session):
        return {'ok':False,'session_id':session,'message':'카드가 보이지 않아요.'}
    def upload(self,name='참가자 가',**fields):
        return self.client.post('/intake',headers=UPLOAD,data={'display_name':name,'hand':'left','consent':'true',**fields},files={'top_image':('top.jpg',photo(),'image/jpeg'),'side_image':('side.jpg',photo('gray'),'image/jpeg')})
    def wait(self,id):
        for _ in range(100):
            r=self.client.get('/admin/intakes/'+id,headers=ADMIN)
            if r.status_code!=200 or r.json()['status'] not in ('queued','processing'):return r
            time.sleep(.01)
        self.fail('worker did not complete')
    def test_upload_retains_bytes_and_list_survives_restart_with_duplicate_names(self):
        a=self.upload();self.assertEqual(a.status_code,202,a.text)
        b=self.upload();self.assertEqual(b.status_code,202,b.text)
        self.assertNotEqual(a.json()['id'],b.json()['id'])
        for r in (a,b):self.assertEqual(self.wait(r.json()['id']).json()['status'],'failed')
        listed=self.client.get('/admin/intakes',headers=ADMIN).json()
        self.assertEqual(listed['count'],2)
        asset=self.client.get('/admin/intakes/'+a.json()['id']+'/assets/top_original',headers=ADMIN)
        self.assertEqual(asset.content,photo())
        self.client.__exit__(None,None,None);self.client=TestClient(server.app);self.client.__enter__()
        self.assertEqual(self.client.get('/admin/intakes',headers=ADMIN).json()['count'],2)
    def test_upload_key_cannot_read_or_delete_anyones_record(self):
        r=self.upload();self.assertEqual(r.status_code,202,r.text);id=r.json()['id'];self.wait(id)
        for url in ('/admin/intakes','/admin/intakes/'+id,'/admin/intakes/'+id+'/assets/top_original'):
            self.assertEqual(self.client.get(url,headers=UPLOAD).status_code,401)
        self.assertEqual(self.client.delete('/admin/intakes/'+id,headers=UPLOAD).status_code,401)
        self.assertEqual(self.client.post('/intake/status',headers=UPLOAD,json={'id':id,'receipt':'wrong'}).status_code,404)
        state=self.client.post('/intake/status',headers=UPLOAD,json={'id':id,'receipt':r.json()['receipt']})
        self.assertEqual(state.status_code,200);self.assertEqual(state.json()['status'],'failed')
        self.assertNotIn('display_name',state.json());self.assertNotIn('result',state.json())
    def test_consent_identity_and_invalid_images_are_rejected_without_record(self):
        for fields in ({'consent':'false'},{'display_name':'  '},{'hand':'other'}):
            self.assertEqual(self.upload(**fields).status_code,400)
        self.assertEqual(self.client.get('/admin/intakes',headers=ADMIN).json()['count'],0)
    def test_failed_measurement_has_no_fabricated_values_and_can_be_deleted(self):
        r=self.upload();self.assertEqual(r.status_code,202,r.text);id=r.json()['id']
        record=self.wait(id).json();self.assertEqual(record['status'],'failed');self.assertNotIn('dims_mm',record['result'])
        self.assertIn('카드',record['message'])
        self.assertEqual(self.client.get('/admin/intakes/'+id+'/assets/report',headers=ADMIN).status_code,404)
        self.assertEqual(self.client.delete('/admin/intakes/'+id,headers=ADMIN).status_code,200)
        self.assertEqual(self.client.get('/admin/intakes/'+id,headers=ADMIN).status_code,404)
        self.assertFalse((Path(self.temp.name)/'participants'/id).exists())
    def test_invalid_photo_does_not_leave_a_record(self):
        r=self.client.post('/intake',headers=UPLOAD,data={'display_name':'가','hand':'right','consent':'true'},files={'top_image':('a.jpg',b'not a photo'),'side_image':('b.jpg',photo())})
        self.assertEqual(r.status_code,415)
        self.assertEqual(self.client.get('/admin/intakes',headers=ADMIN).json()['count'],0)
    def test_restart_marks_pending_records_failed_and_busy_records_cannot_be_deleted(self):
        store=self.client.app.state.intake.store
        row=store.create('이름','right',photo(),photo())
        id=row['id']
        self.assertEqual(self.client.delete('/admin/intakes/'+id,headers=ADMIN).status_code,409)
        from intake import IntakeStore
        restored=IntakeStore(store.root)
        self.assertEqual(restored.get(id)['status'],'failed')
        self.assertIn('재시작',restored.get(id)['message'])
    def test_queue_limit_rejects_without_partial_record(self):
        store=self.client.app.state.intake.store
        with patch('intake.MAX_PENDING',1):
            row=store.create('먼저 접수','left',photo(),photo())
            self.assertEqual(self.upload().status_code,429)
        self.assertEqual(store.listing()['count'],1)
    def test_completion_contains_real_pipeline_outputs_and_all_eleven_values(self):
        # Only native inference is replaced: exercise real decoder, file storage and output gate.
        self.compute.stop()
        dims={str(i):float(i+20) for i in range(1,12)}
        import numpy as np
        import cv2
        async def run(directory,session):
            return await server.process_intake(directory,session)
        self.compute=patch.object(server,'process_intake',server.process_intake)
        self.compute.start()
        with patch.object(server,'measure_brace',return_value=({'ok':True,'session_id':'test','dims_mm':dims,'dims':[]},np.zeros((20,20,3),dtype=np.uint8),np.zeros((20,20,3),dtype=np.uint8),np.zeros((40,40,3),dtype=np.uint8))):
            r=self.upload();self.assertEqual(r.status_code,202,r.text);record=self.wait(r.json()['id']).json()
        self.assertEqual(record['status'],'complete');self.assertEqual(record['result']['dims_mm'],dims)
        self.assertIn('report',record['assets']);self.assertIn('csv',record['assets'])
        image=self.client.get('/admin/intakes/'+r.json()['id']+'/assets/report',headers=ADMIN)
        self.assertEqual(image.status_code,200);self.assertEqual(Image.open(BytesIO(image.content)).format,'JPEG')
    def test_missing_dimension_cannot_become_successful(self):
        self.compute.stop();self.compute=patch.object(server,'process_intake',server.process_intake);self.compute.start()
        import numpy as np
        with patch.object(server,'measure_brace',return_value=({'ok':True,'session_id':'test','dims_mm':{'1':4}},None,None,np.zeros((10,10,3),dtype=np.uint8))):
            r=self.upload();self.assertEqual(r.status_code,202,r.text);record=self.wait(r.json()['id']).json()
        self.assertEqual(record['status'],'failed');self.assertNotIn('report',record['assets'])
    def test_shells_are_public_but_unknown_assets_and_private_paths_are_not(self):
        for url in ('/','/operator','/demo','/intake.js','/operator.js','/intake.css','/layout.css','/maker.css','/workbench.mjs','/fabrication.mjs'):
            self.assertEqual(self.client.get(url).status_code,200,url)
        self.assertEqual(self.client.get('/admin/intakes').status_code,401)
        self.assertEqual(self.client.get('/admin/intakes/not-an-id',headers=ADMIN).status_code,400)
        self.assertEqual(self.client.get('/participants/intake.sqlite').status_code,401)
    def test_public_capture_examples_do_not_expose_camera_metadata_or_private_uploads(self):
        for view in ('top', 'side'):
            response = self.client.get('/guide-'+view+'.webp')
            self.assertEqual(response.status_code, 200)
            image = Image.open(BytesIO(response.content))
            self.assertEqual(image.format, 'WEBP')
            self.assertFalse(image.getexif())
            self.assertNotIn('xmp', image.info)
        self.assertEqual(self.client.get('/guide-private.webp').status_code, 401)
        self.assertEqual(self.client.post('/guide-top.webp').status_code, 401)
        self.assertEqual(self.client.get('/admin/intakes').status_code, 401)

if __name__=='__main__':unittest.main()

class RevisionFlow(unittest.TestCase):
    setUp=IntakeFlow.setUp
    tearDown=IntakeFlow.tearDown
    failed_measurement=staticmethod(IntakeFlow.failed_measurement)
    upload=IntakeFlow.upload
    wait=IntakeFlow.wait
    def test_corrections_are_private_versioned_and_conflicts_preserve_previous_files(self):
        r=self.upload();id=r.json()['id'];self.wait(id)
        base='/admin/intakes/'+id
        payload={'revision':1,'overrides':{'wrist_top':[12,13], 'card_top':[[1,1],[30,1],[30,20],[1,20]]}}
        self.assertEqual(self.client.post(base+'/recalculate',headers=UPLOAD,json=payload).status_code,401)
        changed=self.client.post(base+'/recalculate',headers=ADMIN,json=payload)
        self.assertEqual(changed.status_code,202,changed.text)
        record=self.wait(id).json();self.assertEqual(record['revision'],2)
        self.assertEqual(record['review_status'],'unreviewed');self.assertEqual(len(record['history']),2)
        self.assertEqual(self.client.post(base+'/recalculate',headers=ADMIN,json=payload).status_code,409)
        old=self.client.get(base+'?revision=1',headers=ADMIN).json()
        self.assertEqual(old['revision'],1);self.assertEqual(old['result']['message'],'카드가 보이지 않아요.')
        self.assertEqual(self.client.get(base+'/assets/top_original?revision=1',headers=ADMIN).content,photo())
        self.assertEqual(self.client.get(base+'/assets/top_original?revision=2',headers=ADMIN).content,photo())
        for bad in ({'wrist_top':[48,2]},{'wrist_top':[float('inf'),2]},
                    {'card_top':[[1,1],[2,2],[3,3],[4,4]]},{'not_allowed':[1,2]}):
            if bad.get('wrist_top')==[float('inf'),2]:continue
            response=self.client.post(base+'/recalculate',headers=ADMIN,json={'revision':2,'overrides':bad})
            self.assertEqual(response.status_code,400,response.text)
    def test_review_requires_current_complete_revision_and_acknowledgement(self):
        r=self.upload();id=r.json()['id'];self.wait(id);base='/admin/intakes/'+id
        self.assertEqual(self.client.post(base+'/review',headers=ADMIN,json={'revision':1,'acknowledged':True}).status_code,409)
        store=self.client.app.state.intake.store
        result={'ok':True,'dims_mm':{str(i):20.+i for i in range(1,12)}}
        store.update(id,'complete','done',result)
        self.assertEqual(self.client.post(base+'/review',headers=ADMIN,json={'revision':1,'acknowledged':False}).status_code,400)
        self.assertEqual(self.client.post(base+'/review',headers=ADMIN,json={'revision':1,'acknowledged':True}).status_code,200)
        self.assertEqual(self.client.get(base,headers=ADMIN).json()['review_status'],'reviewed')
        self.assertEqual(self.client.post(base+'/recalculate',headers=ADMIN,json={'revision':1,'overrides':{}}).status_code,202)
        self.wait(id)
        self.assertEqual(self.client.get(base,headers=ADMIN).json()['review_status'],'unreviewed')
        self.assertEqual(self.client.post(base+'/review',headers=ADMIN,json={'revision':1,'acknowledged':True}).status_code,409)
    def test_receipt_authorized_partial_retake_preserves_other_original_and_old_version(self):
        r=self.upload();receipt={'id':r.json()['id'],'receipt':r.json()['receipt']};id=receipt['id'];self.wait(id)
        base='/admin/intakes/'+id
        req={'revision':1,'views':['side'],'reason':'엄지를 손바닥에 붙여 주세요.'}
        self.assertEqual(self.client.post(base+'/retake-request',headers=ADMIN,json=req).status_code,200)
        state=self.client.post('/intake/status',headers=UPLOAD,json=receipt).json()
        self.assertEqual(state['retake_views'],['side']);self.assertEqual(state['review_status'],'retake_requested')
        image=BytesIO();Image.new('RGB',(32,48),'blue').save(image,'JPEG');new=image.getvalue()
        data={**receipt,'revision':'1','consent':'true'}
        files={'side_image':('side.jpg',new,'image/jpeg')}
        self.assertEqual(self.client.post('/intake/retake',headers=UPLOAD,data={**data,'receipt':'wrong'},files=files).status_code,404)
        self.assertEqual(self.client.post('/intake/retake',headers=UPLOAD,data=data,files=files).status_code,202)
        record=self.wait(id).json();self.assertEqual(record['revision'],2)
        self.assertEqual(self.client.get(base+'/assets/top_original?revision=2',headers=ADMIN).content,photo())
        self.assertEqual(self.client.get(base+'/assets/side_original?revision=2',headers=ADMIN).content,new)
        self.assertEqual(self.client.get(base+'/assets/side_original?revision=1',headers=ADMIN).content,photo('gray'))
        self.assertEqual(self.client.post('/intake/retake',headers=UPLOAD,data=data,files=files).status_code,409)
    def test_duplicate_views_are_rejected(self):
        # Same bytes are not two different camera views.
        r=self.client.post('/intake',headers=UPLOAD,data={'display_name':'동일사진','hand':'left','consent':'true'},files={'top_image':('top.jpg',photo()),'side_image':('side.jpg',photo())})
        self.assertEqual(r.status_code,400)

class RevisionPersistence(unittest.TestCase):
    def test_legacy_database_migrates_without_overwriting_existing_result_or_original(self):
        import sqlite3
        from intake import IntakeStore
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);id='c'*32;(root/id).mkdir();(root/id/'top.original').write_bytes(photo())
            with sqlite3.connect(root/'intake.sqlite') as c:
                c.execute('CREATE TABLE records (id TEXT PRIMARY KEY, display_name TEXT, hand TEXT, created_at TEXT, updated_at TEXT, status TEXT,receipt_hash TEXT,message TEXT,result TEXT,top_type TEXT,side_type TEXT)')
                c.execute('INSERT INTO records VALUES (?,?,?,?,?,?,?,?,?,?,?)',(id,'예전 사용자','left','then','then','complete','hash','old','{"ok":true}', 'image/jpeg','image/jpeg'))
            store=IntakeStore(root);record=store.version(id,1)
            self.assertEqual(record['result'],{'ok':True});self.assertEqual(record['revision'],1)
            self.assertEqual(record['review_status'],'unreviewed');self.assertEqual(len(record['history']),1)
            self.assertEqual((store.directory(id,1)/'top.original').read_bytes(),photo())
    def test_nonfinite_and_boolean_coordinates_never_reach_native_pipeline(self):
        from intake import checked_overrides
        from fastapi import HTTPException
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);(path/'top.original').write_bytes(photo())
            for value in (float('nan'),float('inf'),True,-1,48):
                with self.assertRaises(HTTPException):checked_overrides({'wrist_top':[value,2]},path)

class RevisionRecovery(unittest.TestCase):
    def test_interrupted_file_staging_is_quarantined_and_next_revision_can_run(self):
        from intake import IntakeStore
        with tempfile.TemporaryDirectory() as directory:
            store=IntakeStore(directory);id=store.create('복구','left',photo(),photo('gray'))['id'];store.update(id,'failed','retry')
            orphan=Path(directory)/id/'revisions'/'2';orphan.mkdir(parents=True);(orphan/'top.original').write_bytes(b'uncommitted')
            restored=IntakeStore(directory)
            self.assertFalse(orphan.exists())
            self.assertEqual(restored.begin_revision(id,1,{},'correction'),2)
            self.assertEqual((restored.directory(id)/'top.original').read_bytes(),photo())
            self.assertEqual(len(list((Path(directory)/id/'revisions').glob('uncommitted-*'))),1)
    def test_retake_transaction_rejects_revoked_or_changed_request(self):
        from intake import IntakeStore
        from fastapi import HTTPException
        with tempfile.TemporaryDirectory() as directory:
            store=IntakeStore(directory);id=store.create('재촬영','left',photo(),photo('gray'))['id'];store.update(id,'failed','retry')
            for views in (None,['top']):
                if views:store.set_review(id,1,'retake_requested',views,'changed')
                with self.assertRaises(HTTPException) as error:store.begin_revision(id,1,{},'retake',{'side':photo('blue')})
                self.assertEqual(error.exception.status_code,409)
                self.assertEqual(store.get(id)['revision'],1)
    def test_partial_retake_preserves_only_unchanged_view_overrides(self):
        from intake import IntakeStore
        with tempfile.TemporaryDirectory() as directory:
            store=IntakeStore(directory);id=store.create('보정','left',photo(),photo('gray'))['id'];store.update(id,'failed','retry')
            corrections={'wrist_top':[12,13],'wrist_side':[10,11]}
            store.begin_revision(id,1,corrections,'correction');store.update(id,'failed','retry')
            store.set_review(id,2,'retake_requested',['side'],'retry side')
            kept={k:v for k,v in store.version(id)['overrides'].items() if k.endswith('_top')}
            store.begin_revision(id,2,kept,'retake',{'side':photo('blue')})
            self.assertEqual(store.version(id)['overrides'],{'wrist_top':[12,13]})
    def test_failed_database_commit_does_not_block_retry(self):
        from intake import IntakeStore
        from contextlib import contextmanager
        import sqlite3
        with tempfile.TemporaryDirectory() as directory:
            store=IntakeStore(directory);id=store.create('재시도','left',photo(),photo('gray'))['id'];store.update(id,'failed','retry')
            original=store.connect
            @contextmanager
            def fail_commit():
                with original() as c:
                    yield c
                    if c.total_changes:raise sqlite3.OperationalError('simulated commit failure')
            with patch.object(store,'connect',fail_commit):
                with self.assertRaises(sqlite3.OperationalError):store.begin_revision(id,1,{},'correction')
            self.assertEqual(store.get(id)['revision'],1)
            self.assertEqual(store.begin_revision(id,1,{},'correction'),2)
