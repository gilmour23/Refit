import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import base64
from html.parser import HTMLParser
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('laptop',Path(__file__).parents[2]/'scripts/laptop.py')
laptop=importlib.util.module_from_spec(spec);spec.loader.exec_module(laptop)
class LaptopSetup(unittest.TestCase):
 def test_qr_is_scannable_and_connection_details_are_not_default_visible(self):
  class Page(HTMLParser):
   def __init__(self):super().__init__();self.depth=0;self.text=[];self.images=[]
   def handle_starttag(self,tag,attrs):
    attrs=dict(attrs)
    if tag=='details':self.depth+=1
    if tag=='img':self.images.append((attrs.get('alt',''),attrs['src']))
   def handle_endtag(self,tag):
    if tag=='details':self.depth-=1
   def handle_data(self,data):
    if not self.depth:self.text.append(data)
  with tempfile.TemporaryDirectory() as d,patch.object(laptop,'ROOT',Path(d)):
   page=Page();page.feed(laptop.join_page('https://example.trycloudflare.com','U'*48).read_text('utf8'))
   self.assertNotIn('U'*48,''.join(page.text))
   qr=next(src for alt,src in page.images if 'QR'in alt)
   self.assertTrue(all(src.startswith('data:image/')for _,src in page.images))
   import cv2,numpy as np
   image=cv2.imdecode(np.frombuffer(base64.b64decode(qr.split(',',1)[1]),np.uint8),cv2.IMREAD_COLOR)
   decoded,_,_=cv2.QRCodeDetector().detectAndDecode(image)
   self.assertEqual(decoded,'https://example.trycloudflare.com/#key='+'U'*48)
 def test_keys_are_distinct_persistent_and_never_bundled_as_defaults(self):
  with tempfile.TemporaryDirectory() as d:
   a=laptop.credentials(Path(d));b=laptop.credentials(Path(d))
   self.assertEqual(a,b);self.assertNotEqual(a['upload'],a['admin']);self.assertGreaterEqual(len(a['upload']),32)
 def test_runtime_does_not_inherit_original_photo_retention_or_tunnel_credentials(self):
  with patch.dict(os.environ,{'REFIT_VISION_KEEP_ORIGINALS':'1','TUNNEL_TOKEN':'secret','REFIT_VISION_OTHER':'unsafe'}):
   env=laptop.child_environment({'upload':'A'*48,'admin':'B'*48})
  self.assertNotIn('TUNNEL_TOKEN',env);self.assertNotIn('REFIT_VISION_OTHER',env)
  self.assertEqual(env['REFIT_VISION_KEEP_ORIGINALS'],'0')
 def test_participant_qr_never_contains_admin_key_and_operator_page_is_separate(self):
  with tempfile.TemporaryDirectory() as d,patch.object(laptop,'ROOT',Path(d)):
   participant=laptop.join_page('https://example.trycloudflare.com','U'*48).read_text('utf8')
   self.assertNotIn('M'*48,participant)
   operator=laptop.operator_page('https://example.trycloudflare.com','M'*48).read_text('utf8')
   self.assertIn('/operator',operator);self.assertIn('M'*48,operator)
   self.assertNotIn('#key=',operator)
