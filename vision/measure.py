"""사진 1장 → 손 관절점 + ArUco 스케일 → mm 치수.

사용법:
    python measure.py photos/hand1.jpg
    python measure.py photos/hand1.jpg --marker-mm 49.5   # 인쇄된 마커 실측값이 다를 때

출력 (out/ 폴더):
    <사진이름>_overlay.jpg  : 관절점·마커·치수가 그려진 확인용 이미지
    <사진이름>.json         : 치수 결과 (CAD팀 전달용 measurements.json의 바탕)

촬영 규칙: 손바닥을 책상에 평평하게, 마커 카드는 손목 바로 옆(같은 평면)에,
카메라는 위에서 수직으로.
"""
import argparse
import json
from pathlib import Path

import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import BaseOptions, vision

MODEL = Path(__file__).parent / "models" / "hand_landmarker.task"
ARUCO_DICT = cv2.aruco.DICT_4X4_50

# MediaPipe 손 관절점 번호 (0=손목, 4=엄지끝, 8=검지끝, 12=중지끝, 16=약지끝, 20=새끼끝)
WRIST, INDEX_MCP, MIDDLE_MCP, PINKY_MCP, MIDDLE_TIP = 0, 5, 9, 17, 12
# 관절점 두 개 사이 직선거리로rmrj 재는 치수
SEGMENTS = {
    "hand_length_mm":   (WRIST, MIDDLE_TIP),   # 손목~중지 끝
    "palm_length_mm":   (WRIST, MIDDLE_MCP),   # 손목~중지 뿌리
    "palm_width_mm":    (INDEX_MCP, PINKY_MCP),  # 검지 뿌리~새끼 뿌리 (관절 중심 기준이라 실제 손폭보다 작음)
    "index_finger_mm":  (5, 8),
    "middle_finger_mm": (9, 12),
}


def imread(path):
    # cv2.imread는 한글 경로를 못 읽어서 바이트로 읽은 뒤 디코딩
    return cv2.imdecode(np.fromfile(str(path), np.uint8), cv2.IMREAD_COLOR)


def imwrite(path, img):
    cv2.imencode(Path(path).suffix, img)[1].tofile(str(path))


def find_marker_scale(img, marker_mm):
    """마커 네 변의 평균 픽셀 길이로 mm/px 배율을 구한다."""
    detector = cv2.aruco.ArucoDetector(cv2.aruco.getPredefinedDictionary(ARUCO_DICT))
    corners, ids, _ = detector.detectMarkers(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))
    if ids is None:
        return None, None
    c = corners[0][0]  # 첫 번째 마커의 네 꼭짓점 (4,2)
    side_px = np.mean([np.linalg.norm(c[i] - c[(i + 1) % 4]) for i in range(4)])
    return float(marker_mm / side_px), c


def find_hand(img):
    # 모델 파일도 한글 경로 문제를 피하려고 경로 대신 바이트로 넘김
    options = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_buffer=MODEL.read_bytes()),
        num_hands=1)
    with vision.HandLandmarker.create_from_options(options) as landmarker:
        rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        result = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    if not result.hand_landmarks:
        return None
    h, w = img.shape[:2]
    # 0~1로 정규화된 좌표 → 픽셀 좌표
    return np.array([[p.x * w, p.y * h] for p in result.hand_landmarks[0]])


def draw(img, pts, marker, measurements):
    out = img.copy()
    if marker is not None:
        cv2.polylines(out, [marker.astype(int)], True, (255, 0, 0), 3)
    if pts is not None:
        for conn in vision.HandLandmarksConnections.HAND_CONNECTIONS:
            cv2.line(out, tuple(pts[conn.start].astype(int)), tuple(pts[conn.end].astype(int)), (0, 255, 0), 2)
        for i, p in enumerate(pts):
            cv2.circle(out, tuple(p.astype(int)), 5, (0, 0, 255), -1)
            cv2.putText(out, str(i), tuple(p.astype(int) + 6), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
    for k, (name, val) in enumerate(measurements.items()):
        cv2.putText(out, f"{name}: {val}", (20, 40 + 35 * k), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 0), 4)
        cv2.putText(out, f"{name}: {val}", (20, 40 + 35 * k), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
    return out


def measure(img, marker_mm=50.0):
    """사진(BGR 배열) → (치수 dict, 관절점, 마커 꼭짓점). server.py도 이 함수를 쓴다."""
    scale, marker = find_marker_scale(img, marker_mm)
    pts = find_hand(img)

    measurements = {}
    if scale is not None:
        measurements["scale_mm_per_px"] = round(scale, 4)
    if pts is not None and scale is not None:
        for name, (a, b) in SEGMENTS.items():
            measurements[name] = round(float(np.linalg.norm(pts[a] - pts[b])) * scale, 1)
    return measurements, pts, marker


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--marker-mm", type=float, default=50.0)
    args = ap.parse_args()

    img = imread(args.image)
    if img is None:
        raise SystemExit(f"사진을 못 읽었어요: {args.image}")

    measurements, pts, marker = measure(img, args.marker_mm)
    print(f"마커: {'찾음' if marker is not None else '못 찾음'}")
    print(f"손:   {'찾음 (관절점 21개)' if pts is not None else '못 찾음'}")

    for k, v in measurements.items():
        print(f"  {k:18s} {v}")

    out_dir = Path(__file__).parent / "out"
    out_dir.mkdir(exist_ok=True)
    stem = Path(args.image).stem
    imwrite(out_dir / f"{stem}_overlay.jpg", draw(img, pts, marker, measurements))
    result = {"image": str(args.image), "measurements": measurements,
              "landmarks_px": pts.round(1).tolist() if pts is not None else None}
    (out_dir / f"{stem}.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"저장: out/{stem}_overlay.jpg, out/{stem}.json")


if __name__ == "__main__":
    main()
