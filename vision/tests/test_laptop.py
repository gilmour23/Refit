import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('laptop',Path(__file__).parents[2]/'scripts/laptop.py')
laptop=importlib.util.module_from_spec(spec);spec.loader.exec_module(laptop)
class LaptopSetup(unittest.TestCase):
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
