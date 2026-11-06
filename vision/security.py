"""Authentication and bounded request bodies for the development measurement API."""
import hmac
import os
import re
from starlette.exceptions import HTTPException
from starlette.responses import JSONResponse
try:
    from pillow_heif import register_heif_opener
    register_heif_opener(thumbnails=False)
except ImportError:
    pass

MAX_IMAGE_BYTES = 20 * 1024 * 1024
MAX_IMAGE_PIXELS = 24_000_000
PUBLIC_WEB_PATHS = frozenset({'/', '/app.js', '/core.mjs', '/bluetooth.mjs', '/style.css', '/favicon.svg', '/operator', '/demo', '/intake.js', '/operator.js', '/intake.css'})


class VisionSecurityMiddleware:
    def __init__(self, app, max_body_bytes=42 * 1024 * 1024):
        self.app = app
        self.max_body_bytes = max_body_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        path, method = scope.get("path", ""), scope.get("method", "")
        headers = scope.get("headers", [])
        public = (path == '/health' and method == 'GET') or (path in PUBLIC_WEB_PATHS and method in ('GET', 'HEAD'))
        if not public:
            # A measurement/upload key cannot list or download other people's reports.
            admin = path.startswith("/admin/") or method in ("GET", "HEAD")
            expected = os.environ.get("REFIT_VISION_ADMIN_TOKEN" if admin else "REFIT_VISION_TOKEN", "")
            if not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", expected):
                return await JSONResponse({"detail": "측정 서버 연결키 설정이 필요합니다."}, status_code=503)(scope, receive, send)
            values = [v for k, v in headers if k.lower() == b"authorization"]
            correct = ("Bearer " + expected).encode("ascii")
            if len(values) != 1 or not hmac.compare_digest(values[0], correct):
                return await JSONResponse({"detail": "측정 서버 연결키를 확인해 주세요."}, status_code=401,
                                          headers={"WWW-Authenticate": "Bearer", "Cache-Control": "no-store"})(scope, receive, send)
        lengths = [v for k, v in headers if k.lower() == b"content-length"]
        try:
            if len(lengths) > 1 or lengths and int(lengths[0]) < 0:
                raise ValueError()
            if lengths and int(lengths[0]) > self.max_body_bytes:
                return await JSONResponse({"detail": "업로드할 사진이 너무 큽니다."}, status_code=413)(scope, receive, send)
        except ValueError:
            return await JSONResponse({"detail": "잘못된 요청 길이입니다."}, status_code=400)(scope, receive, send)
        received = 0
        async def limited_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_body_bytes:
                    raise HTTPException(413, "업로드할 사진이 너무 큽니다.")
            return message
        async def private_send(message):
            if message["type"] == "http.response.start":
                message = dict(message)
                response_headers = [(k, v) for k, v in message.get("headers", []) if k.lower() != b"cache-control"]
                response_headers.extend([(b"cache-control", b"no-store"), (b"x-content-type-options", b"nosniff")])
                message["headers"] = response_headers
            await send(message)
        await self.app(scope, limited_receive, private_send)


async def read_photo(upload):
    """Bound bytes and dimensions before OpenCV allocates a decoded bitmap."""
    from io import BytesIO
    from PIL import Image, UnidentifiedImageError
    if upload.size is not None and upload.size > MAX_IMAGE_BYTES:
        raise HTTPException(413, "사진 한 장은 20MB 이하여야 합니다.")
    data = await upload.read(MAX_IMAGE_BYTES + 1)
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "사진이 비어 있거나 너무 큽니다.")
    try:
        with Image.open(BytesIO(data)) as photo:
            # Galaxy Motion Photos can be identified as MPO although the primary image is JPEG.
            jpeg_container = photo.format == 'MPO' and data.startswith(b'\xff\xd8\xff')
            if photo.format not in ("JPEG", "PNG", "HEIF") and not jpeg_container:
                raise HTTPException(415, "JPG, PNG 또는 HEIC 원본을 선택해 주세요.")
            if photo.width <= 0 or photo.height <= 0 or photo.width * photo.height > MAX_IMAGE_PIXELS:
                raise HTTPException(413, "사진 해상도가 너무 큽니다 (최대 2400만 화소).")
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        raise HTTPException(415, "사진 파일을 읽을 수 없습니다.")
    return data

def decode_photo(data):
    import cv2
    import numpy as np
    from io import BytesIO
    from PIL import Image
    if data.startswith(b'\xff\xd8\xff') or data.startswith(b'\x89PNG'):
        return cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    with Image.open(BytesIO(data)) as image:
        if image.format != 'HEIF' or image.width * image.height > MAX_IMAGE_PIXELS:
            raise HTTPException(415, '사진 파일을 읽을 수 없습니다.')
        return cv2.cvtColor(np.asarray(image.convert('RGB')), cv2.COLOR_RGB2BGR)

