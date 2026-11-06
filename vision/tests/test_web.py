import asyncio
from io import BytesIO
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from PIL import Image
from starlette.datastructures import UploadFile
from fastapi.testclient import TestClient
from fastapi import FastAPI
from security import read_photo, VisionSecurityMiddleware

class WebBoundaries(unittest.TestCase):
    def test_galaxy_motion_jpeg_is_accepted_without_reencoding(self):
        buf = BytesIO()
        Image.new('RGB', (12, 12), 'white').save(buf, 'MPO', save_all=True,
                                                append_images=[Image.new('RGB', (12, 12))])
        data = buf.getvalue()
        self.assertEqual(Image.open(BytesIO(data)).format, 'MPO')
        photo = UploadFile(file=BytesIO(data), filename='galaxy.jpg')
        self.assertEqual(asyncio.run(read_photo(photo)), data)

    def test_iphone_heif_preserves_original_bytes_and_decodes_primary_image(self):
        from pillow_heif import register_heif_opener
        register_heif_opener(thumbnails=False)
        buf=BytesIO();Image.new('RGB',(20,16),'red').save(buf,'HEIF')
        data=buf.getvalue()
        self.assertEqual(asyncio.run(read_photo(UploadFile(file=BytesIO(data),filename='iphone.heic'))),data)
        from security import decode_photo
        self.assertEqual(decode_photo(data).shape,(16,20,3))

    def test_static_allowlist_does_not_open_reports_or_unknown_js(self):
        app=FastAPI()
        app.add_middleware(VisionSecurityMiddleware)
        @app.get('/')
        def home(): return {'screen': 'demo'}
        @app.get('/secret.js')
        def secret(): return {'private': True}
        with patch.dict(os.environ, {'REFIT_VISION_TOKEN':'A'*48,'REFIT_VISION_ADMIN_TOKEN':'B'*48}), TestClient(app) as c:
            self.assertEqual(c.get('/').status_code, 200)
            self.assertEqual(c.get('/secret.js').status_code, 401)
            self.assertEqual(c.get('/brace_sessions',headers={'Authorization':'Bearer '+'A'*48}).status_code,401)

if __name__=='__main__': unittest.main()
