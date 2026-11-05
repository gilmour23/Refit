import asyncio
from io import BytesIO
from PIL import Image
from starlette.datastructures import UploadFile
from starlette.exceptions import HTTPException
import os
from unittest import TestCase
from unittest.mock import patch
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from starlette.responses import JSONResponse
from security import VisionSecurityMiddleware, read_photo

ACCESS = "a" * 48
ADMIN = "b" * 48


class ServerSecurityTests(TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {"REFIT_VISION_TOKEN": ACCESS, "REFIT_VISION_ADMIN_TOKEN": ADMIN})
        self.env.start()
        app = FastAPI()
        app.add_middleware(VisionSecurityMiddleware, max_body_bytes=256)
        @app.get("/health")
        def health(): return {"ok": True}
        @app.get("/brace_sessions")
        def sessions(): return {"items": []}
        @app.post("/measure_brace")
        async def measure(request: Request):
            body = await request.body()
            return {"size": len(body)}
        self.client = TestClient(app)

    def tearDown(self): self.env.stop()

    def test_health_public_but_photos_and_reports_need_right_key(self):
        self.assertEqual(200, self.client.get("/health").status_code)
        self.assertEqual(401, self.client.post("/measure_brace").status_code)
        self.assertEqual(401, self.client.post("/measure_brace", headers={"Authorization": "Bearer wrong"}).status_code)
        response = self.client.post("/measure_brace", content=b"photo", headers={"Authorization": "Bearer " + ACCESS})
        self.assertEqual(200, response.status_code)
        self.assertEqual("no-store", response.headers["cache-control"])
        self.assertEqual(401, self.client.get("/brace_sessions", headers={"Authorization": "Bearer " + ACCESS}).status_code)
        self.assertEqual(200, self.client.get("/brace_sessions", headers={"Authorization": "Bearer " + ADMIN}).status_code)

    def test_missing_server_key_fails_closed(self):
        with patch.dict(os.environ, {"REFIT_VISION_TOKEN": ""}):
            self.assertEqual(503, self.client.post("/measure_brace").status_code)

    def test_oversized_fixed_or_chunked_upload_is_rejected(self):
        auth = {"Authorization": "Bearer " + ACCESS}
        self.assertEqual(413, self.client.post("/measure_brace", content=b"x" * 257, headers=auth).status_code)
        self.assertEqual(413, self.client.post("/measure_brace", content=iter([b"x" * 128, b"x" * 129]), headers=auth).status_code)


class PhotoInputTests(TestCase):
    def test_valid_png_keeps_original_bytes(self):
        image = BytesIO()
        Image.new("RGB", (10, 10)).save(image, format="PNG")
        data = image.getvalue()
        upload = UploadFile(BytesIO(data), size=len(data))
        self.assertEqual(data, asyncio.run(read_photo(upload)))

    def test_bad_or_oversized_images_are_rejected_before_cv_decode(self):
        with self.assertRaises(HTTPException) as failure:
            asyncio.run(read_photo(UploadFile(BytesIO(b"not an image"), size=12)))
        self.assertEqual(415, failure.exception.status_code)
        with self.assertRaises(HTTPException) as failure:
            asyncio.run(read_photo(UploadFile(BytesIO(b""), size=21 * 1024 * 1024)))
        self.assertEqual(413, failure.exception.status_code)
        image = BytesIO()
        Image.new("L", (6000, 5000)).save(image, format="PNG")
        data = image.getvalue()
        with self.assertRaises(HTTPException) as failure:
            asyncio.run(read_photo(UploadFile(BytesIO(data), size=len(data))))
        self.assertEqual(413, failure.exception.status_code)
