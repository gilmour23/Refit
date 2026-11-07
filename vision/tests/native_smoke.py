"""Real native inference on an image without a hand must reject rather than invent dimensions."""
from io import BytesIO
import os,secrets,tempfile
from pathlib import Path
from PIL import Image
from fastapi.testclient import TestClient
import server
from measure import find_hand
import numpy as np
assert find_hand(np.zeros((480,640,3),dtype=np.uint8)) is None
os.environ['REFIT_VISION_TOKEN']=secrets.token_urlsafe(48)
os.environ['REFIT_VISION_ADMIN_TOKEN']=secrets.token_urlsafe(48)
with tempfile.TemporaryDirectory() as d:
 server.LOG_DIR=Path(d)
 data=BytesIO();Image.new('RGB',(640,480),'white').save(data,'JPEG');data=data.getvalue()
 side=BytesIO();Image.new('RGB',(640,480),'gray').save(side,'JPEG');side=side.getvalue()
 with TestClient(server.app) as c:
  assert c.get('/').status_code==200
  maker=c.get('/operator')
  assert maker.status_code==200 and '/maker.css' in maker.text
  assert c.get('/maker.css').status_code==200
  assert c.post('/demo/check',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']}).json()['model_ready']
  response=c.post('/measure_brace',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']},files={'top_image':('top.jpg',data,'image/jpeg'),'side_image':('side.jpg',side,'image/jpeg')},data={'return_report':'true'})
  body=response.json()
  assert response.status_code==200,body
  assert body['ok'] is False,body
  assert '오류가 났어요' not in body.get('message',''),body
  assert not body.get('dims_mm') and not body.get('report_jpg_base64'),body
  duplicate=c.post('/intake',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']},files={'top_image':('top.jpg',data,'image/jpeg'),'side_image':('side.jpg',data,'image/jpeg')},data={'display_name':'CI duplicate test','hand':'left','consent':'true'})
  assert duplicate.status_code==400,duplicate.text
  response=c.post('/intake',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']},files={'top_image':('top.jpg',data,'image/jpeg'),'side_image':('side.jpg',side,'image/jpeg')},data={'display_name':'CI invalid photo test','hand':'left','consent':'true'})
  assert response.status_code==202,response.text
  import time
  id=response.json()['id']
  for _ in range(200):
   record=c.get('/admin/intakes/'+id,headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_ADMIN_TOKEN']}).json()
   if record['status'] not in ('queued','processing'):break
   time.sleep(.05)
  assert record['status']=='failed',record
  assert '오류' not in record['message'],record
  assert not record['result'].get('dims_mm'),record
  auth={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']}
  assert c.get('/admin/intakes/'+id,headers=auth).status_code==401
  status=c.post('/intake/status',headers=auth,json={'id':id,'receipt':response.json()['receipt']})
  assert status.status_code==200 and status.json()['status']=='failed',status.text
  assert not {'display_name','result','dims_mm'}.intersection(status.json()),status.text
  originals=c.get('/admin/intakes/'+id+'/assets/top_original',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_ADMIN_TOKEN']})
  assert originals.status_code==200 and originals.content==data
 print('Native inference initialized; distinct invalid photographs rejected without invented values; maker page, receipt and protected originals OK')
