"""Real native inference on an image without a hand must reject rather than invent dimensions."""
from io import BytesIO
import os,secrets,tempfile
from pathlib import Path
from PIL import Image
from fastapi.testclient import TestClient
import server
os.environ['REFIT_VISION_TOKEN']=secrets.token_urlsafe(48)
os.environ['REFIT_VISION_ADMIN_TOKEN']=secrets.token_urlsafe(48)
with tempfile.TemporaryDirectory() as d:
 server.LOG_DIR=Path(d)
 data=BytesIO();Image.new('RGB',(640,480),'white').save(data,'JPEG');data=data.getvalue()
 with TestClient(server.app) as c:
  assert c.get('/').status_code==200
  assert c.post('/demo/check',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']}).json()['model_ready']
  response=c.post('/measure_brace',headers={'Authorization':'Bearer '+os.environ['REFIT_VISION_TOKEN']},files={'top_image':('top.jpg',data,'image/jpeg'),'side_image':('side.jpg',data,'image/jpeg')},data={'return_report':'true'})
  body=response.json()
  assert response.status_code==200,body
  assert body['ok'] is False,body
  assert '오류가 났어요' not in body.get('message',''),body
  assert not body.get('dims_mm') and not body.get('report_jpg_base64'),body
 print('Native inference initialized; invalid photos honestly rejected; web/API auth OK')
