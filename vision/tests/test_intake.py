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

def photo():
    b=BytesIO();Image.new('RGB',(48,32),'white').save(b,'JPEG');return b.getvalue()

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
        return self.client.post('/intake',headers=UPLOAD,data={'display_name':name,'hand':'left','consent':'true',**fields},files={'top_image':('top.jpg',photo(),'image/jpeg'),'side_image':('side.jpg',photo(),'image/jpeg')})
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
        for url in ('/','/operator','/demo','/intake.js','/operator.js','/intake.css'):
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
