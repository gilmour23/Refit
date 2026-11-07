r"""전완 치수 측정 API 서버 (v2) — 앱이 위·옆 사진 2장을 보내면 전완 치수 JSON을 돌려준다.

실행:
    .\.venv\Scripts\python.exe -m uvicorn server:app --host 127.0.0.1 --port 8000

확인:
    브라우저에서 http://localhost:8000/docs  → 사진 2장 올려서 바로 테스트 가능

v1(손가락 측정)은 server_v1_backup.py 에 보관.
"""
import base64
import json
import os
import re
import uuid
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool
import asyncio
import math
from contextlib import asynccontextmanager
from intake import IntakeService, router as intake_router

from security import VisionSecurityMiddleware, read_photo, decode_photo
from brace_report import dims_csv
from measure import imwrite
from measure_arm import exif_focal35, measure_arm, parse_quad
from measure_brace import FIXED_MM, measure_brace

@asynccontextmanager
async def lifespan(app):
    global measurement_lock
    measurement_lock = asyncio.Lock()
    app.state.intake = IntakeService(LOG_DIR / 'participants', lambda directory, session: process_intake(directory, session))
    try:
        yield
    finally:
        await app.state.intake.close()

app = FastAPI(lifespan=lifespan, title="Re:Fit 치수 측정 API (손목 보조기 /measure_brace + 전완 /measure)", version="2.2")
app.add_middleware(VisionSecurityMiddleware)
measurement_lock = asyncio.Lock()

LOG_DIR = Path(__file__).parent / "out" / "server"  # 받은 사진·결과를 남겨서 나중에 줄자 실측과 비교


def validated_session(value: str, prefix: str) -> str:
    session = value.strip() or prefix + uuid.uuid4().hex
    if not re.fullmatch(r"[\w-]{1,80}", session):
        raise HTTPException(400, "측정 ID에는 문자·숫자·밑줄·하이픈만 사용할 수 있어요")
    LOG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        (LOG_DIR / session).mkdir(exist_ok=False, mode=0o700)
    except FileExistsError:
        raise HTTPException(409, "이미 사용한 측정 ID예요. 새 ID로 다시 측정해 주세요")
    return session


def parse_xy(s):
    """'x,y' 문자열 → [x, y]. 비어 있으면 None. 형식이 틀리면 ValueError."""
    if s is None or not s.strip():
        return None
    x, y = (float(v) for v in s.split(","))
    return [x, y]


@app.get("/health")
def health():
    return {"ok": True, "version": "2.2"}


@app.post("/measure")
async def measure_endpoint(
    top_image: UploadFile = File(..., description="위에서 찍은 사진 (손바닥/손등이 위, 팔 전체 + 카드)"),
    side_image: UploadFile = File(..., description="옆에서 찍은 사진 (새끼손가락 쪽이 바닥, 팔 전체 + 카드)"),
    session_id: str = Form("", description="측정 구분용 이름. 비우면 서버 시간으로 자동 생성"),
    elbow_top: str = Form("", description="위 사진의 팔꿈치 좌표 'x,y' (원본 사진 픽셀). 비우면 자동 추정"),
    elbow_side: str = Form("", description="옆 사진의 팔꿈치 좌표 'x,y'. 비우면 자동 추정"),
    wrist_top: str = Form("", description="위 사진의 손목 주름 좌표 'x,y'. 비우면 자동(손 인식 + 보정)"),
    wrist_side: str = Form("", description="옆 사진의 손목 주름 좌표 'x,y'. 비우면 자동. 탭할 거면 두 사진 모두"),
    marker_mm: float = Form(50.0, description="ArUco 마커를 쓸 때 한 변 길이(mm). 카드만 쓰면 무시됨"),
    k: float = Form(1.0, description="둘레 보정 계수 (줄자 실측 ÷ 사진 추정)"),
    shell_thickness_mm: float = Form(3.0, description="보조기 껍데기 두께(mm), CAD로 그대로 전달"),
    clearance_mm: float = Form(2.0, description="팔과 보조기 사이 여유(mm), CAD로 그대로 전달"),
    return_overlay: bool = Form(False, description="true면 확인용 그림(윤곽·단면선) 2장을 base64 jpg로 함께 반환"),
):
    session = validated_session(session_id, "S")

    # 1) 사진 읽기 — EXIF(초점거리)는 원본 바이트에서 읽어야 하므로 바이트를 그대로 보관
    imgs, focal = {}, []
    for name, up in [("top", top_image), ("side", side_image)]:
        data = await read_photo(up)
        img = decode_photo(data)
        if img is None:
            label = "위" if name == "top" else "옆"
            return {"ok": False, "message": f"{label} 사진 파일을 읽을 수 없어요 (jpg/png만 가능)", "session_id": session}
        imgs[name] = (img, data)
        focal.append(exif_focal35(data))

    # 2) 좌표 입력 확인
    try:
        overrides = {"elbow_top": parse_xy(elbow_top), "elbow_side": parse_xy(elbow_side),
                     "wrist_top": parse_xy(wrist_top), "wrist_side": parse_xy(wrist_side)}
    except ValueError:
        return {"ok": False, "message": "좌표는 'x,y' 형식(예: 812,1530)으로 보내주세요", "session_id": session}

    # 3) 측정
    try:
        result, top_ov, side_ov = measure_arm(imgs["top"][0], imgs["side"][0], session, marker_mm, k,
                                              overrides, shell_thickness_mm, clearance_mm, tuple(focal))
    except Exception as e:  # 예상 못 한 사진에서 서버가 죽지 않게
        result = {"ok": False, "message": f"측정 중 오류가 났어요. 다시 찍어주세요 ({type(e).__name__})",
                  "session_id": session}
        top_ov = side_ov = None

    # 4) 기록 남기기 (원본 사진 + 결과 + 확인용 그림)
    d = LOG_DIR / session
    d.mkdir(parents=True, exist_ok=True)
    if os.environ.get("REFIT_VISION_KEEP_ORIGINALS") == "1":
        for name, (_, data) in imgs.items():
            (d / f"{name}.jpg").write_bytes(data)
    (d / "result.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    if top_ov is not None:
        imwrite(d / "top_overlay.jpg", top_ov)
        imwrite(d / "side_overlay.jpg", side_ov)

    # 5) 확인용 그림 (요청 시)
    if return_overlay and top_ov is not None:
        result["overlay_top_jpg_base64"] = base64.b64encode(cv2.imencode(".jpg", top_ov)[1]).decode()
        result["overlay_side_jpg_base64"] = base64.b64encode(cv2.imencode(".jpg", side_ov)[1]).decode()
    return result


@app.post("/measure_brace")
async def measure_brace_endpoint(
    top_image: UploadFile = File(..., description="위 사진 (손등이 위, 손 전체 + 손목 아래 10cm + 카드)"),
    side_image: UploadFile = File(..., description="옆 사진 (새끼손가락 쪽이 바닥, 손 전체 + 손목 아래 10cm + 카드)"),
    session_id: str = Form("", description="측정 구분용 이름. 비우면 서버 시간으로 자동 생성"),
    wrist_top: str = Form("", description="위 사진의 손목 0점 좌표 'x,y' (원본 픽셀). 비우면 MediaPipe 0번"),
    wrist_side: str = Form("", description="옆 사진의 손목 0점 좌표 'x,y'. 비우면 MediaPipe 0번"),
    card_top: str = Form("", description="위 사진 카드 네 모서리 'x1,y1,x2,y2,x3,y3,x4,y4' (원본 픽셀, 순서 상관없음). 비우면 자동"),
    card_side: str = Form("", description="옆 사진 카드 네 모서리. 비우면 자동"),
    fixed_mm: float = Form(FIXED_MM, description="0번 ~ ①② 위치 거리 mm (팀 결정값 40)"),
    marker_mm: float = Form(50.0, description="ArUco 마커를 쓸 때 한 변 길이(mm)"),
    return_overlay: bool = Form(False, description="true면 확인용 그림 2장을 base64 jpg로 함께 반환"),
    return_report: bool = Form(False, description="true면 보고서 그림(JPG)을 base64로 함께 반환. "
                                                   "아니면 report_url로 따로 받기"),
):
    """보조기 치수 ①~⑪ (mm). 정의는 measure_brace.py 맨 위 설명."""
    session = validated_session(session_id, "B")
    imgs, focal = {}, []
    for name, up in [("top", top_image), ("side", side_image)]:
        data = await read_photo(up)
        img = decode_photo(data)
        if img is None:
            label = "위" if name == "top" else "옆"
            return {"ok": False, "message": f"{label} 사진 파일을 읽을 수 없어요 (jpg/png만 가능)", "session_id": session}
        imgs[name] = (img, data)
        focal.append(exif_focal35(data))
    try:
        overrides = {"wrist_top": parse_xy(wrist_top), "wrist_side": parse_xy(wrist_side),
                     "card_top": parse_quad(card_top), "card_side": parse_quad(card_side)}
    except ValueError:
        return {"ok": False, "message": "좌표 형식이 틀렸어요. 손목은 'x,y', 카드는 'x1,y1,x2,y2,x3,y3,x4,y4'로 보내주세요",
                "session_id": session}
    if measurement_lock.locked():
        raise HTTPException(429, '다른 사진을 분석 중이에요. 잠시 뒤 다시 시도해 주세요.')
    try:
        async with measurement_lock:
            result, top_ov, side_ov, report = await run_in_threadpool(
                measure_brace, imgs["top"][0], imgs["side"][0], session, marker_mm,
                fixed_mm, overrides, tuple(focal))
    except Exception as e:
        result = {"ok": False, "message": f"측정 중 오류가 났어요. 다시 찍어주세요 ({type(e).__name__})",
                  "session_id": session}
        top_ov = side_ov = report = None

    d = LOG_DIR / session
    d.mkdir(parents=True, exist_ok=True)
    if os.environ.get("REFIT_VISION_KEEP_ORIGINALS") == "1":
        for name, (_, data) in imgs.items():
            (d / f"{name}.jpg").write_bytes(data)
    if top_ov is not None:
        imwrite(d / "brace_top.jpg", top_ov)
        imwrite(d / "brace_side.jpg", side_ov)
    if report is not None:  # 앱 결과 화면 + 설계팀 전달용
        cv2.imencode(".jpg", report, [cv2.IMWRITE_JPEG_QUALITY, 90])[1].tofile(str(d / "report.jpg"))
        (d / "dims.csv").write_text(dims_csv(result), encoding="utf-8-sig")
        result["report_url"] = f"/brace_report/{session}"
        result["dims_csv_url"] = f"/brace_dims/{session}"
    (d / "brace.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    if return_report and report is not None:
        result["report_jpg_base64"] = base64.b64encode(cv2.imencode(".jpg", report, [cv2.IMWRITE_JPEG_QUALITY, 90])[1]).decode()
    if return_overlay and top_ov is not None:
        result["overlay_top_jpg_base64"] = base64.b64encode(cv2.imencode(".jpg", top_ov)[1]).decode()
        result["overlay_side_jpg_base64"] = base64.b64encode(cv2.imencode(".jpg", side_ov)[1]).decode()
    return result


def _session_file(session_id, name):
    """저장된 측정 파일 경로. session_id에 경로 문자(.. / \\)가 들어오면 거절."""
    if not session_id or any(c in session_id for c in "/\\") or ".." in session_id:
        raise HTTPException(400, "잘못된 측정 ID예요")
    p = LOG_DIR / session_id / name
    if not p.exists():
        raise HTTPException(404, "해당 측정의 보고서가 없어요 (측정 실패했거나 ID가 틀림)")
    return p


@app.get("/brace_sessions")
def brace_sessions(limit: int = 50):
    """설계팀 화면용: 보고서가 있는 측정 목록 (최신순). 각 항목 = id, 측정 시각, ①~⑪ 값, 보고서 주소."""
    items = []
    if LOG_DIR.exists():
        dirs = [p for p in LOG_DIR.iterdir() if (p / "report.jpg").exists() and (p / "brace.json").exists()]
        for p in sorted(dirs, key=lambda q: (q / "report.jpg").stat().st_mtime, reverse=True)[:limit]:
            r = json.loads((p / "brace.json").read_text(encoding="utf-8"))
            items.append({
                "session_id": p.name,
                "measured_at": datetime.fromtimestamp((p / "report.jpg").stat().st_mtime).strftime("%Y-%m-%d %H:%M"),
                "dims_mm": r.get("dims_mm"),
                "has_warnings": bool(r.get("warnings")),
                "report_url": f"/brace_report/{p.name}",
                "dims_csv_url": f"/brace_dims/{p.name}",
            })
    return {"count": len(items), "items": items}


@app.get("/brace_result/{session_id}")
def brace_result(session_id: str):
    """측정 1건의 전체 결과 JSON (측정할 때 받은 응답과 같음, base64 그림 제외)."""
    return json.loads(_session_file(session_id, "brace.json").read_text(encoding="utf-8"))


@app.get("/brace_report/{session_id}")
def brace_report(session_id: str):
    """보고서 그림(JPG): 사진 위 측정 위치 + ①~⑪ 값 표. 앱 결과 화면·설계팀 전달용."""
    return FileResponse(_session_file(session_id, "report.jpg"), media_type="image/jpeg",
                        filename=f"{session_id}_report.jpg")


@app.get("/brace_dims/{session_id}")
def brace_dims(session_id: str):
    """①~⑪ 치수표(CSV, 엑셀에서 열림). 설계팀 전달용."""
    return FileResponse(_session_file(session_id, "dims.csv"), media_type="text/csv; charset=utf-8",
                        filename=f"{session_id}_dims.csv")




@app.post('/demo/check')
def demo_check():
    from measure import MODEL
    return {'ok': True, 'model_ready': MODEL.is_file(), 'photo_formats': ['JPEG', 'PNG', 'HEIF'], 'sensor_format': 'signed_INT16_LE'}


async def process_intake(directory, session):
    """Use the existing real measurement pipeline, preserving uploaded EXIF bytes."""
    def calculate():
        data = [(directory / (name + '.original')).read_bytes() for name in ('top', 'side')]
        images = [decode_photo(value) for value in data]
        if any(image is None for image in images):
            return {'ok': False, 'session_id': session, 'message': '사진을 읽지 못했어요. 원본 사진을 다시 제출해 주세요.'}
        for name, image in zip(('top', 'side'), images):
            imwrite(directory / (name + '.jpg'), image)
        correction_file = directory / 'corrections.json'
        corrections = json.loads(correction_file.read_text(encoding='utf-8')) if correction_file.exists() else {}
        result, top, side, report = measure_brace(images[0], images[1], session, 50.0, FIXED_MM,
                                                 corrections, tuple(exif_focal35(value) for value in data))
        for name, image in (('top_overlay', top), ('side_overlay', side)):
            if image is not None: imwrite(directory / (name + '.jpg'), image)
        dims = result.get('dims_mm') or {}
        complete = all(isinstance(dims.get(str(i)), (int, float)) and not isinstance(dims.get(str(i)), bool)
                       and math.isfinite(dims[str(i)]) and dims[str(i)] > 0 for i in range(1, 12))
        if result.get('ok') is True and (not complete or report is None):
            result['ok'] = False
            result['message'] = '11개 치수와 보고서를 모두 얻지 못했어요. 촬영 조건을 확인하고 다시 제출해 주세요.'
        if result.get('ok') is True:
            imwrite(directory / 'report.jpg', report)
            (directory / 'dims.csv').write_text(dims_csv(result), encoding='utf-8-sig')
        return result
    async with measurement_lock:
        return await run_in_threadpool(calculate)


app.include_router(intake_router)

@app.get('/operator')
def operator_page():
    return FileResponse(Path(__file__).parent / 'web' / 'operator.html')

@app.get('/demo')
def demo_page():
    return FileResponse(Path(__file__).parent / 'web' / 'demo.html')


app.mount('/', StaticFiles(directory=Path(__file__).parent / 'web', html=True), name='web_demo')
