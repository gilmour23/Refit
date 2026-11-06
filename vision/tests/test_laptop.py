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
