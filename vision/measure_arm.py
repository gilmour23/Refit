"""팔 사진 2장(위·옆) → 전완(손목~팔꿈치) 길이 + 단면별 폭·두께·둘레(mm).   [v2, 2026-10-31]

사용법:
    python measure_arm.py photos/S01_top.jpg photos/S01_side.jpg --session S01_20261031
    python measure_arm.py top.jpg side.jpg --k 1.03                 # 줄자 비교로 구한 둘레 보정 계수 적용
    python measure_arm.py top.jpg side.jpg --elbow-top 1470,3540    # 팔꿈치 자동 인식이 틀렸을 때만 직접 지정 (원본 사진 픽셀)

출력 (out/ 폴더):
    <session>_arm.json          : 치수 결과 (CAD팀 전달용 measurements.json)
    <session>_top_overlay.jpg   : 위 사진 확인용 (윤곽·중심선·단면 폭)
    <session>_side_overlay.jpg  : 옆 사진 확인용 (윤곽·중심선·단면 두께)

촬영 규칙 (v2: 카드 위치·받침 규칙 없음):
    - top : 손등이 위 / side: 새끼손가락 쪽이 바닥. 카메라는 위에서 수직으로
    - 크기 기준: 카드(교통카드·학생증 등 85.6x54mm, 번호 있는 신용카드는 피하기) 또는 ArUco 마커를
      책상 위 아무 데나 평평하게. 팔과 겹치지만 않으면 됨
    - 손끝~팔꿈치가 화면 안에. 팔꿈치가 소매에 가려지면 자동 인식이 안 됨 (그때는 경고 후 소매까지로 계산)

v1과 달라진 점:
    1) 팔 분할: 피부의 '색감'(Lab의 a·b)만 비교하고 밝기는 뺌 → 흰 책상 그림자·소매가 팔에 안 붙음
    2) 팔꿈치 자동 추정: 손목에서 팔 방향으로 폭을 재면
         손목(좁음) → 근육 부분(가장 넓음) → 팔꿈치(다시 좁아지는 골짜기) → 위팔(다시 넓어짐)
       근육 다음의 가장 깊은 골짜기를 팔꿈치로 봄. 위/옆 사진 두 값의 평균
    3) 높이 보정: 카드는 책상 높이, 팔 윤곽은 그보다 (팔 두께의 절반)만큼 높아서 몇 % 크게 찍힘.
       사진 EXIF의 초점거리로 카메라 높이 H를 구하고, 실제 = 사진 측정값 × (H - 높이) / H 로 보정
       (EXIF 없으면 보정 생략 + 경고)

11-01 추가 (v2.1):
    4) 손목(0점) 보정: MediaPipe 손목 점은 손바닥 아래 볼록한 부분에 걸쳐서 0% 둘레가 실측보다 ~24% 컸음.
       위 사진에서 폭이 손 쪽 볼록함에서 전완 쪽으로 줄어드는 곳(볼록함 40%만 남은 지점)을 손목 주름으로 보고,
       같은 거리를 옆 사진에도 적용
    5) 단면마다 dist_mm(손목 주름에서 실제 거리) 추가. 위치는 계속 % 기준 (사람마다 팔 길이가 달라서)
    6) --at-mm 0,50,100 처럼 지정한 거리에서도 측정 (줄자 실측 비교용)

둘레 계산:
    한 단면을 폭(위 사진) × 두께(옆 사진)인 타원으로 보고 Ramanujan 근사식으로 둘레를 구한 뒤
    보정 계수 k를 곱한다. k = 줄자 실측 둘레 ÷ 타원 둘레의 평균 (기본 1.0 = 보정 안 함).
"""
import argparse
import io
import json
import math
from pathlib import Path

import cv2
import numpy as np

from measure import find_hand, find_marker_scale, imread, imwrite

WRIST = 0  # MediaPipe 손 관절점 0번 = 손목
MIDDLE_MCP = 9  # 중지 뿌리 → 손목에서 이 점 반대 방향이 팔 쪽
PALM = [0, 1, 5, 9, 13, 17]  # 손목 + 손가락 뿌리 관절점 → 이 다각형 안이 확실한 피부
MAX_SIDE = 2000  # 폰 원본(4000px)은 너무 커서 긴 변을 이 크기로 줄여서 처리
SECTIONS = [0, 25, 50, 75, 100]  # 손목(0%) ~ 팔꿈치(100%) 사이 측정 위치
WINDOW_MM = 3.0  # 단면 하나를 잴 때 앞뒤 ±3mm 범위 여러 줄의 중앙값을 씀 (잡음 줄이기)
CARD_MM = (85.60, 53.98)  # 신용카드 국제 규격 ISO/IEC 7810 ID-1 (교통카드·학생증도 같은 크기)

SKIN_THR = 4.0  # 피부 판정 기준의 최솟값 (손등 색에서의 마할라노비스 거리). 흰 책상 사진: 팔 1~2.6, 그림자·소매 7~12
SKIN_THR_MAX = 20.0  # 기준 자동 조정의 최댓값
PROFILE_STEP_MM = 2.0  # 팔꿈치 찾을 때 폭을 재는 간격
END_TRIM_MM = 25.0  # 피부 끝(소매·사진 끝)에서 윤곽이 가늘어지는 구간은 팔꿈치 후보에서 제외
MIN_DROP_MM = 1.5  # 근육 부분 최대폭보다 이만큼 이상 좁아져야 골짜기로 인정
RISE_MM = 1.0  # 골짜기 뒤에 이만큼 이상 다시 넓어져야 팔꿈치로 인정 (그냥 가늘어지다 끝나면 소매에 가려진 것)
SENSOR_DIAG_35MM = 43.27  # 35mm 필름 대각선 길이 (EXIF '35mm 환산 초점거리'의 기준)
WRIST_BULGE_FRAC = 0.4  # 손목 보정: 손바닥 아래 볼록함이 이 비율만 남은 곳을 손목 주름으로 봄
#   (11-01 C세트 줄자 비교: 0.3→12mm 이동 최대오차 4.8%, 0.5→5mm 4.8%, 0.4→7mm 3.2%(k=0.917). 1명 데이터라 재검증 필요)
WRIST_MIN_BULGE_MM = 4.0  # 볼록함이 이보다 작으면 손목 보정 안 함
# 손목 보정은 위 사진(폭)에서만 계산하고 옆 사진에도 같은 거리를 적용.
# 옆 사진(두께)은 손바닥 볼록함이 잘 안 보여서 위치가 들쭉날쭉함 (C세트: 위 12mm vs 옆 19mm)


def ellipse_circumference(a, b):
    """폭 a, 두께 b인 타원의 둘레 (Ramanujan 근사)."""
    A, B = a / 2, b / 2
    return math.pi * (3 * (A + B) - math.sqrt((3 * A + B) * (A + 3 * B)))


def color_distance(lab, ref):
    """Lab 색공간에서 ref 색과의 거리. 밝기 차이는 절반만 반영 (카드 찾기에 사용)."""
    diff = lab - ref
    diff[..., 0] *= 0.5
    return np.linalg.norm(diff, axis=2)


def exif_focal35(data):
    """사진 파일 바이트 → 35mm 환산 초점거리(mm). 없으면 None. (폰 사진은 보통 들어 있음)"""
    try:
        from PIL import Image
        v = Image.open(io.BytesIO(data)).getexif().get_ifd(0x8769).get(0xA405)  # FocalLengthIn35mmFilm
        return float(v) if v else None
    except Exception:
        return None


def segment_arm(img, pts, wrist, cut_hand=True):
    """손등 피부 '색감'과 비슷한 픽셀 중 손목과 이어진 덩어리 = 팔 마스크. (마스크, 팔 옆 배경색) 반환.

    밝기(L)는 빼고 색감(a·b)만 봐서, 흰 책상의 그림자(회색)나 소매처럼 밝기만 비슷한 것은 안 붙음.
    피부색의 퍼진 정도(공분산)를 반영한 거리(마할라노비스)를 써서 사람·조명마다 기준이 자동으로 맞춰짐.
    """
    lab = cv2.cvtColor(cv2.GaussianBlur(img, (9, 9), 0), cv2.COLOR_BGR2LAB).astype(np.float32)
    h, w = lab.shape[:2]
    ref = np.zeros((h, w), np.uint8)
    cv2.fillPoly(ref, [cv2.convexHull(pts[PALM].astype(np.int32))], 255)
    if ref.sum() == 0:
        cv2.circle(ref, (int(wrist[0]), int(wrist[1])), max(5, int(0.01 * min(h, w))), 255, -1)
    ab = lab[..., 1:] - 128
    s = ab[ref > 0]
    mu = s.mean(axis=0)
    inv_cov = np.linalg.inv(np.cov(s.T) + np.eye(2))  # +1: 손등 색이 아주 균일해도 기준이 너무 좁아지지 않게
    d = ab - mu
    m = np.sqrt(np.einsum("hwi,ij,hwj->hw", d, inv_cov, d))
    skin_level = np.median(m[ref > 0])

    # 손목 너머(손가락 쪽)는 지움: 전완만 필요함
    to_arm = pts[WRIST] - pts[MIDDLE_MCP]
    palm_len = np.linalg.norm(to_arm)
    to_arm = to_arm / palm_len
    ys, xs = np.mgrid[0:h, 0:w]
    beyond_wrist = (xs - wrist[0]) * to_arm[0] + (ys - wrist[1]) * to_arm[1] < -0.15 * palm_len
    if not cut_hand:  # 손바닥까지 필요할 때 (measure_brace.py)
        beyond_wrist[:] = False
    seed = wrist + 0.3 * palm_len * to_arm  # 손목에서 팔 쪽으로 살짝 들어간 점
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))

    def cut(threshold):
        mask = (m < threshold).astype(np.uint8) * 255
        mask = cv2.morphologyEx(cv2.morphologyEx(mask, cv2.MORPH_OPEN, k), cv2.MORPH_CLOSE, k)
        mask[beyond_wrist] = 0
        n, labels, stats, _ = cv2.connectedComponentsWithStats(mask)
        arm_id = labels[int(np.clip(seed[1], 0, h - 1)), int(np.clip(seed[0], 0, w - 1))]
        if arm_id == 0:  # seed가 배경에 떨어졌으면 가장 큰 덩어리를 팔로 봄
            arm_id = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA])) if n > 1 else 0
        arm = (labels == arm_id).astype(np.uint8) * 255
        contours, _ = cv2.findContours(arm, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        cv2.drawContours(arm, contours, -1, 255, -1)  # 팔 안쪽 구멍(반사광 등) 메우기
        ring = cv2.dilate(arm, np.ones((31, 31), np.uint8)) & ~cv2.dilate(arm, np.ones((11, 11), np.uint8))
        return arm, ring

    # 기준값 자동 조정: 처음엔 SKIN_THR로 자르고, 팔 바로 바깥 배경의 거리를 재서
    # '피부와 배경의 중간'으로 다시 자름 (배경이 피부와 멀면 기준이 넉넉해져서 빛 받은 쪽 피부도 포함됨)
    thr = SKIN_THR
    for _ in range(3):
        arm, ring = cut(thr)
        if not ring.any():
            break
        new_thr = float(np.clip((skin_level + np.median(m[ring > 0])) / 2, SKIN_THR, SKIN_THR_MAX))
        if abs(new_thr - thr) < 0.2:
            break
        thr = new_thr
    # (GrabCut으로 경계 다듬기도 시험했으나, 흰 책상에서 팔 가장자리 그림자까지 잡아 폭이 ~5mm 커져서 뺐음. 10-31)
    bg = np.median(lab[ring > 0], axis=0) if ring.any() else None  # 팔 바로 옆 배경색 (카드 찾기 1순위 기준)
    return arm, bg, to_arm


def find_card(img, arm, bg):
    """배경(bg 색)과 다른 덩어리 중 신용카드 비율(85.6:53.98 ≈ 1.59) 직사각형 → (mm/px 배율, 꼭짓점 4개).

    bg 후보 2개를 차례로 시도: ① 팔 바로 옆 띠의 색 ② 팔을 뺀 사진 전체의 대표색.
    (①은 팔 옆에 소매·그림자가 걸치면 실제 배경보다 어둡게 잡혀서, 하얀 책상 등에서 실패함 → ②로 보완)
    없으면 (None, None).
    """
    lab = cv2.cvtColor(cv2.GaussianBlur(img, (7, 7), 0), cv2.COLOR_BGR2LAB).astype(np.float32)
    not_arm = cv2.dilate(arm, np.ones((15, 15), np.uint8)) == 0
    candidates = ([bg] if bg is not None else []) + [np.median(lab[not_arm], axis=0)]
    for ref in candidates:
        found = _find_card_with_bg(img, lab, arm, not_arm, ref)
        if found[0] is not None:
            return found
    return None, None


def _find_card_with_bg(img, lab, arm, not_arm, bg):
    dist = color_distance(lab, bg)
    # 기준선: 팔 바로 옆 배경의 색 흔들림(보풀·그림자)보다 확실히 큰 차이
    ring = cv2.dilate(arm, np.ones((31, 31), np.uint8)) & ~cv2.dilate(arm, np.ones((11, 11), np.uint8))
    # (중앙값 사용: 팔 옆에 지퍼·책상 등이 조금 걸쳐 있어도 영향 안 받게)
    mask = ((dist > max(15, 2 * np.median(dist[ring > 0]))) & not_arm).astype(np.uint8) * 255
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    mask = cv2.morphologyEx(cv2.morphologyEx(mask, cv2.MORPH_OPEN, k), cv2.MORPH_CLOSE, k)

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    min_side = 0.05 * min(img.shape[:2])  # 너무 작은 잡티는 제외
    best, best_fill = None, 0.85  # 직사각형을 85% 이상 채워야 카드로 인정
    for cnt in contours:
        rect = cv2.minAreaRect(cnt)
        long_px, short_px = max(rect[1]), min(rect[1])
        if short_px < min_side or abs(long_px / short_px - CARD_MM[0] / CARD_MM[1]) > 0.12:
            continue
        fill = cv2.contourArea(cnt) / (long_px * short_px)
        if fill > best_fill:
            best, best_fill = rect, fill
    if best is None:
        return None, None

    # 테두리 정밀하게 다시 잡기: 카드 바로 바깥(배경)과 카드 안쪽 색의 중간값으로 자름
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    inner, near, ring = (np.zeros(img.shape[:2], np.uint8) for _ in range(3))
    box = cv2.boxPoints(best)
    c = box.mean(axis=0)
    cv2.fillPoly(inner, [((box - c) * 0.7 + c).astype(np.int32)], 255)
    cv2.fillPoly(near, [((box - c) * 1.15 + c).astype(np.int32)], 255)
    cv2.fillPoly(ring, [((box - c) * 1.3 + c).astype(np.int32)], 255)
    ring[near > 0] = 0
    dist = color_distance(lab, np.median(lab[ring > 0], axis=0))  # 배경 = 카드 둘레 고리 영역
    card_level = np.median(dist[inner > 0])
    refined = ((dist > 0.5 * card_level) & (near > 0)).astype(np.uint8) * 255
    contours, _ = cv2.findContours(refined, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contours:
        best = cv2.minAreaRect(max(contours, key=cv2.contourArea))
    long_px, short_px = max(best[1]), min(best[1])
    scale = (CARD_MM[0] / long_px + CARD_MM[1] / short_px) / 2
    return float(scale), cv2.boxPoints(best)


def arm_axis(mask, wrist, u0):
    """팔 중심선 (점 c, 방향 u: 손목→팔꿈치). 손목~피부 끝 사이 10~90% 구간 픽셀의 주성분 방향."""
    ys, xs = np.nonzero(mask)
    pts = np.column_stack([xs, ys]).astype(np.float64)
    u, c = u0, wrist
    for _ in range(3):  # 방향이 바뀌면 구간도 바뀌니 몇 번 반복
        t = (pts - wrist) @ u
        L = t.max()
        band = pts[(t > 0.1 * L) & (t < 0.9 * L)]
        if len(band) < 100:
            break
        c = band.mean(axis=0)
        v = np.linalg.svd(band - c, full_matrices=False)[2][0]
        u = v if v @ u > 0 else -v
    return c, u


def chord(mask, p, n):
    """점 p에서 n 방향 직선이 팔 마스크를 지나는 구간 (r_lo, r_hi, 사진끝에 닿음) 픽셀. 못 찾으면 None."""
    h, w = mask.shape
    R = max(h, w)
    r = np.arange(-R, R, 0.5)
    xy = p + r[:, None] * n
    x, y = np.round(xy[:, 0]).astype(int), np.round(xy[:, 1]).astype(int)
    ok = (x >= 0) & (x < w) & (y >= 0) & (y < h)
    inside = np.zeros(len(r), bool)
    inside[ok] = mask[y[ok], x[ok]] > 0
    if not inside.any():
        return None
    i0 = int(np.argmin(np.abs(r)))
    if not inside[i0]:
        idx = np.nonzero(inside)[0]
        i0 = int(idx[np.argmin(np.abs(idx - i0))])
    lo = hi = i0
    while lo > 0 and inside[lo - 1]:
        lo -= 1
    while hi < len(r) - 1 and inside[hi + 1]:
        hi += 1
    touches_edge = not ok[max(lo - 1, 0)] or not ok[min(hi + 1, len(r) - 1)]
    return r[lo], r[hi], touches_edge


def moving_median(x, k=5):
    pad = np.pad(x, k // 2, mode="edge")
    return np.array([np.median(pad[i:i + k]) for i in range(len(x))])


def find_elbow(d, w):
    """폭 프로파일(손목에서 거리 d, 폭 w; mm) → 팔꿈치까지 거리(mm). 못 찾으면 None.

    손목 쪽 가장 좁은 곳 이후로, '지금까지 최대폭 - 현재 폭'이 가장 큰 곳(= 근육 다음의 가장 깊은 골짜기)이면서
    그 뒤로 다시 넓어지는 곳을 팔꿈치로 본다.
    """
    ok = ~np.isnan(w)
    d, w = d[ok], w[ok]
    if len(d) == 0:
        return None
    keep = d < d.max() - END_TRIM_MM
    d, w = d[keep], w[keep]
    if len(w) < 20:
        return None
    w = moving_median(w)
    i0 = int(np.argmin(np.where(d < 0.4 * d.max(), w, np.inf)))  # 손목 쪽 가장 좁은 곳
    d, w = d[i0:], w[i0:]
    run = np.maximum.accumulate(w)  # 지금까지의 최대폭 (근육 부분)
    drop = run - w
    rise_after = np.maximum.accumulate(w[::-1])[::-1] - w  # 이 지점 뒤에서 다시 넓어지는 정도
    score = np.where(rise_after >= RISE_MM, drop, 0)
    j = int(np.argmax(score))
    if score[j] < max(MIN_DROP_MM, 0.02 * run[j]):
        return None
    # 골짜기가 평평하게 넓으면(폭 차이 0.5mm 이내 구간) 그 가운데를 팔꿈치로 봄
    flat = np.nonzero(score >= score[j] - 0.5)[0]
    return float(np.mean(d[_run_around(flat, j)]))


def _run_around(idx, j):
    """정렬된 인덱스 배열 idx에서 j를 포함하는 연속 구간만 골라냄."""
    lo = hi = int(np.searchsorted(idx, j))
    while lo > 0 and idx[lo - 1] == idx[lo] - 1:
        lo -= 1
    while hi < len(idx) - 1 and idx[hi + 1] == idx[hi] + 1:
        hi += 1
    return idx[lo:hi + 1]


def _width_at(mask, c, u, n, s_w, scale, dmm):
    """MediaPipe 손목에서 중심선을 따라 dmm 떨어진 곳의 폭(mm). 윤곽이 없으면 nan."""
    ch = chord(mask, c + (s_w + dmm / scale) * u, n)
    return (ch[1] - ch[0] + 0.5) * scale if ch else np.nan


def find_wrist_shift(width_at):
    """손바닥 아래 볼록함이 끝나는 곳 = 손목 주름으로 보고, MediaPipe 손목 점에서 얼마나(mm) 옮길지.

    손목 점 주변(-15~+60mm)의 폭을 재면  손 쪽(넓음) → 손목(급히 좁아짐) → 전완(천천히 변함).
    '볼록한 최대폭'과 '그 뒤 최소폭' 사이를 WRIST_BULGE_FRAC 만큼만 남긴 지점을 손목 주름으로 본다.
    볼록함이 WRIST_MIN_BULGE_MM보다 작으면(옆 사진 등) 판단 불가 → None.
    """
    d = np.arange(-15, 61, 1.0)
    w = moving_median(np.array([width_at(x) for x in d]), 3)
    ok = ~np.isnan(w)
    d, w = d[ok], w[ok]
    near = d <= 10
    if near.sum() < 3 or (~near).sum() < 10:
        return None
    i_peak = int(np.argmax(np.where(near, w, -np.inf)))
    after = slice(i_peak, None)
    w_min = float(w[after].min())
    bulge = float(w[i_peak]) - w_min
    if bulge < WRIST_MIN_BULGE_MM:
        return None
    thr = w_min + WRIST_BULGE_FRAC * bulge
    below = np.nonzero(w[after] <= thr)[0]
    return float(d[i_peak + below[0]]) if len(below) else None


def measure_view(img, marker_mm=50.0, wrist=None, elbow=None, auto_wrist=True, card=None):
    """사진 1장 → 배율, 팔 마스크, 중심선, 손목에서부터의 폭 프로파일, 팔꿈치 후보.

    wrist/elbow: 원본 사진 픽셀 좌표로 직접 지정할 때만 넘김 (None이면 자동).
    card: 앱에서 탭한 카드 네 모서리 (원본 픽셀, 4x2). 있으면 카드 자동 찾기 대신 이걸 씀.
    단면 폭은 팔꿈치가 두 사진을 합쳐 정해진 뒤 section_widths()에서 잰다.
    """
    f = min(1.0, MAX_SIDE / max(img.shape[:2]))
    if f < 1:
        img = cv2.resize(img, None, fx=f, fy=f, interpolation=cv2.INTER_AREA)
    view = {"img": img, "f": f, "warnings": [], "marker": None, "mask": None}

    pts = find_hand(img)
    if pts is None:
        view["error"] = "손을 못 찾았어요. 손 전체가 보이게 찍어주세요"
        return view
    view["pts"] = pts  # 손 관절점 21개 (measure_brace.py에서 사용)
    manual_wrist = wrist
    wrist = np.asarray(wrist, float) * f if wrist is not None else pts[WRIST]
    if manual_wrist is not None:
        pts = pts.copy()
        pts[WRIST] = wrist
        view['pts'] = pts

    mask, bg, to_arm = segment_arm(img, pts, wrist)
    view["mask"] = mask
    if mask.mean() / 255 > 0.6:  # 사진 대부분이 팔로 잡힘 = 배경이 피부색과 비슷한 것
        view["error"] = "배경과 팔이 구분이 안 돼요. 피부색과 다른 단색 배경(흰색·파랑·초록·검정) 위에서 찍어주세요"
        return view

    # 크기 기준: 앱에서 탭한 카드 모서리 > ArUco 마커 > 카드 자동 찾기
    scale = None
    if card is not None:
        scale, ref, ratio_err = card_scale_from_corners(np.asarray(card, float) * f)
        view["ref"] = "card_tap"
        if scale is not None and ratio_err > 0.08:
            view["warnings"].append(f"탭한 카드 모서리의 가로세로 비율이 카드와 {ratio_err * 100:.0f}% 달라요. "
                                    "네 모서리를 정확히 눌렀는지, 사진을 위에서 수직으로 찍었는지 확인해주세요")
    if scale is None:
        scale, ref = find_marker_scale(img, marker_mm)
        view["ref"] = "marker"
    if scale is None:
        scale, ref = find_card(img, mask, bg)
        view["ref"] = "card"
    view["marker"] = ref
    if scale is None:
        view["error"] = "마커나 카드를 못 찾았어요. 배경과 색이 다른 카드를 팔과 겹치지 않게 놓고 찍어주세요"
        return view
    view["scale"] = scale

    c, u = arm_axis(mask, wrist, to_arm)
    n = np.array([-u[1], u[0]])
    s_w = (wrist - c) @ u
    ys, xs = np.nonzero(mask)
    s_end = float(((np.column_stack([xs, ys]) - c) @ u).max())  # 피부가 끝나는 곳 (소매·사진 끝)

    # 손목 → 피부 끝까지 2mm 간격으로 폭을 잼 (팔꿈치 찾기용)
    def profile(s0):
        ss = np.arange(s0, s_end, PROFILE_STEP_MM / scale)
        prof = []
        for s in ss:
            ch = chord(mask, c + s * u, n)
            prof.append((ch[1] - ch[0] + 0.5) * scale if ch else np.nan)
        return (ss - s0) * scale, np.array(prof)

    prof_d, prof_w = profile(s_w)
    # 손목(0점) 보정: MediaPipe 손목 점은 손바닥 아래 볼록한 부분에 걸쳐 있음 → 그 볼록함이 끝나는 곳으로 옮김
    view["wrist_shift_mm"] = 0.0
    view["wrist_method"] = "manual"
    if manual_wrist is None and not auto_wrist:
        view["wrist_method"] = "mediapipe"  # 위 사진의 보정값을 measure_arm()에서 적용
    elif manual_wrist is None:
        shift = find_wrist_shift(lambda dmm: _width_at(mask, c, u, n, s_w, scale, dmm))
        view["wrist_method"] = "auto" if shift is not None else "mediapipe"
        if shift:
            s_w += shift / scale
            view["wrist_shift_mm"] = round(shift, 1)
            prof_d, prof_w = profile(s_w)
    view.update(c=c, u=u, n=n, s_w=s_w, s_end=s_end, prof_d=prof_d, prof_w=prof_w)
    view["elbow_auto_mm"] = find_elbow(view["prof_d"], view["prof_w"])
    if elbow is not None:
        view["elbow_manual_mm"] = float((np.asarray(elbow, float) * f - c) @ u - s_w) * scale
    return view


def section_widths(view, length_px, sections=SECTIONS):
    """손목에서 중심선을 따라 length_px 떨어진 곳을 팔꿈치로 두고, 단면별 폭(mm, 높이 보정 전)과 그릴 선분."""
    c, u, n, s_w, scale, mask = (view[k] for k in ["c", "u", "n", "s_w", "scale", "mask"])
    s_e = s_w + length_px
    half = WINDOW_MM / scale
    widths, chords = [], []
    for pos in sections:
        s = s_w + pos / 100 * (s_e - s_w)
        vals, edge = [], False
        for ds in np.linspace(-half, half, 7):
            ch = chord(mask, c + (s + ds) * u, n)
            if ch:
                vals.append(ch[1] - ch[0] + 0.5)
                edge |= ch[2]
        main = chord(mask, c + s * u, n)
        if not vals or main is None or s > view["s_end"]:
            widths.append(None)
            chords.append(None)
            view["warnings"].append(f"{pos}% 지점에서 팔 윤곽을 못 찾았어요 (소매에 가려졌거나 사진 밖)")
            continue
        if edge:
            view["warnings"].append(f"{pos}% 지점 팔이 사진 가장자리에 닿아 잘렸어요")
        widths.append(float(np.median(vals)) * scale)
        p = c + s * u
        chords.append((p + main[0] * n, p + main[1] * n))
    view.update(wrist=c + s_w * u, elbow=c + s_e * u, chords=chords)
    return widths


def camera_height(view, focal35):
    """카메라~책상 거리(mm). 책상 위 물체 X mm가 X·f/H 픽셀로 찍히므로 H = (mm/px 배율) × (초점거리 px)."""
    if not focal35:
        return None
    h, w = view["img"].shape[:2]
    f_px = focal35 * math.hypot(h, w) / SENSOR_DIAG_35MM
    H = view["scale"] * f_px
    return H if 150 < H < 2000 else None  # 말이 안 되는 값(EXIF 오류 등)은 버림


def height_factor(H, z):
    """책상에서 z mm 높이에 있는 것은 (H - z)/H 배로 줄여야 실제 크기."""
    return (H - z) / H if H and z else 1.0


def draw_view(view, label, sections=SECTIONS):
    out = view["img"].copy()
    t = max(2, out.shape[1] // 600)  # 선 굵기를 사진 크기에 맞춤
    fs = out.shape[1] / 1500
    if view.get("mask") is not None:
        contours, _ = cv2.findContours(view["mask"], cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        cv2.drawContours(out, contours, -1, (0, 255, 0), t)
    if view.get("marker") is not None:
        cv2.polylines(out, [view["marker"].astype(int)], True, (255, 0, 0), t)
    if "elbow" in view:
        w, e = view["wrist"].astype(int), view["elbow"].astype(int)
        cv2.line(out, tuple(w), tuple(e), (0, 255, 255), t)
        for pt, name in [(w, "wrist"), (e, f"elbow({view.get('elbow_method', '')})")]:
            cv2.circle(out, tuple(pt), 4 * t, (0, 0, 255), -1)
            cv2.putText(out, name, tuple(pt + 8 * t), cv2.FONT_HERSHEY_SIMPLEX, fs, (0, 0, 255), t)
        for pos, ch, val in zip(sections, view["chords"], view["final_widths"]):
            if ch is None:
                continue
            a, b = ch[0].astype(int), ch[1].astype(int)
            cv2.line(out, tuple(a), tuple(b), (0, 0, 255), t)
            txt = f"{pos}% ({pos / 100 * view['length_mm']:.0f}mm): {val}mm"
            cv2.putText(out, txt, tuple(b + 6 * t), cv2.FONT_HERSHEY_SIMPLEX, fs, (0, 0, 0), 3 * t)
            cv2.putText(out, txt, tuple(b + 6 * t), cv2.FONT_HERSHEY_SIMPLEX, fs, (255, 255, 255), t)
    if "length_mm" in view:
        H = view.get("H")
        head = f"{label} ({view['ref']})  forearm={view['length_mm']:.1f}mm" + (f"  H={H:.0f}mm" if H else "")
    else:
        head = f"{label}  {view.get('error', '')}"
    cv2.putText(out, head, (20, int(50 * fs)), cv2.FONT_HERSHEY_SIMPLEX, fs * 1.2, (0, 0, 0), 3 * t)
    cv2.putText(out, head, (20, int(50 * fs)), cv2.FONT_HERSHEY_SIMPLEX, fs * 1.2, (255, 255, 255), t)
    return out


def measure_arm(top_img, side_img, session_id, marker_mm=50.0, k=1.0,
                overrides=None, shell_thickness_mm=3.0, clearance_mm=2.0, focal35=(None, None),
                extra_dists_mm=None):
    """위·옆 사진 → (결과 dict, 위 overlay, 옆 overlay). server.py에서도 이 함수를 쓴다.

    focal35: (위 사진, 옆 사진)의 35mm 환산 초점거리. exif_focal35(파일 바이트)로 구함. None이면 높이 보정 생략.
    extra_dists_mm: 손목 주름에서 이 거리(mm)들에서도 단면을 재서 result["sections_at_mm"]에 넣음 (줄자 비교용).
    """
    ov = overrides or {}
    top = measure_view(top_img, marker_mm, ov.get("wrist_top"), ov.get("elbow_top"))
    side = measure_view(side_img, marker_mm, ov.get("wrist_side"), ov.get("elbow_side"), auto_wrist=False)
    views = {"top": top, "side": side}
    names = {"top": "위", "side": "옆"}

    problems = [f"[{names[v]} 사진] {views[v]['error']}" for v in views if "error" in views[v]]
    result = {"ok": not problems, "message": " / ".join(problems) or None, "session_id": session_id}
    if problems:
        return result, draw_view(top, "top"), draw_view(side, "side")
    warnings = []

    # 0) 위 사진에서 구한 손목 보정 거리를 옆 사진에도 적용 (같은 팔이니까)
    for v, o in [("side", "top")]:
        if views[v]["wrist_method"] == "mediapipe" and views[o]["wrist_method"] == "auto":
            sh = views[o]["wrist_shift_mm"]
            V = views[v]
            V["s_w"] += sh / V["scale"]
            keep = V["prof_d"] >= sh
            V["prof_d"], V["prof_w"] = V["prof_d"][keep] - sh, V["prof_w"][keep]
            for key in ["elbow_auto_mm", "elbow_manual_mm"]:
                if V.get(key) is not None:
                    V[key] -= sh
            V.update(wrist_shift_mm=sh, wrist_method="auto(other)")

    # 1) 카메라 높이 → 높이 보정 계수. 위 사진의 팔 윤곽은 (두께/2), 옆 사진은 (폭/2) 높이에 있음
    for v, fl in zip(["top", "side"], focal35):
        views[v]["H"] = camera_height(views[v], fl)
    if not (top["H"] and side["H"]):
        warnings.append("사진에 초점거리 정보(EXIF)가 없어 높이 보정을 못 했어요. 폭·두께가 몇 % 크게 나올 수 있어요")
    z_top = np.nanmedian(side["prof_w"]) / 2  # 위 사진 윤곽 높이 ≈ 팔 두께의 절반 (전체 평균값)
    z_side = np.nanmedian(top["prof_w"]) / 2
    fL = {"top": height_factor(top["H"], z_top), "side": height_factor(side["H"], z_side)}

    # 2) 팔꿈치: 직접 지정 > 자동(골짜기) > 피부 끝(소매)
    est = {v: views[v]["elbow_manual_mm"] * fL[v] for v in views if "elbow_manual_mm" in views[v]}
    method = "manual"
    if not est:
        est = {v: views[v]["elbow_auto_mm"] * fL[v] for v in views if views[v]["elbow_auto_mm"]}
        method = "auto"
    if est:
        L = float(np.mean(list(est.values())))
        if len(est) == 1:
            warnings.append(f"팔꿈치를 {names[next(iter(est))]} 사진에서만 찾았어요")
    else:
        est = {v: (views[v]["s_end"] - views[v]["s_w"]) * views[v]["scale"] * fL[v] for v in views}
        L = float(min(est.values()))
        method = "skin_end"
        warnings.append("팔꿈치를 못 찾아서 피부가 끝나는 곳(소매·사진 끝)까지를 전완으로 봤어요. "
                        "소매를 팔꿈치 위로 걷고, 팔꿈치 너머까지 찍으면 정확해져요")
    if len(est) == 2:
        a, b = est["top"], est["side"]
        if abs(a - b) / max(a, b) > 0.08:
            warnings.append(f"위/옆 사진에서 찾은 팔꿈치 위치가 8% 넘게 달라요 ({a:.0f} vs {b:.0f}mm). 확인용 그림을 봐주세요")
    if not 150 <= L <= 400:
        warnings.append(f"전완 길이 {L:.0f}mm는 보통 범위(150~400mm)를 벗어나요. 팔꿈치 위치를 확인하세요")

    # 3) 단면별 폭·두께 (사진 측정값 → 높이 보정)
    def cross_sections(positions, vs):
        """positions(%)마다 폭·두께·둘레. vs = 단면 선을 기록할 view들 (그림용)."""
        raw = {v: section_widths(vs[v], L / (vs[v]["scale"] * fL[v]), positions) for v in vs}
        out, fin = [], {"top": [], "side": []}
        for i, pos in enumerate(positions):
            a0, b0 = raw["top"][i], raw["side"][i]
            a = round(a0 * height_factor(top["H"], b0 / 2 if b0 else z_top), 1) if a0 else None
            b = round(b0 * height_factor(side["H"], a0 / 2 if a0 else z_side), 1) if b0 else None
            fin["top"].append(a)
            fin["side"].append(b)
            circ = round(k * ellipse_circumference(a, b), 1) if a and b else None
            out.append({"pos": round(pos, 1), "dist_mm": round(pos / 100 * L, 1),  # 손목 주름에서 실제 거리
                        "width_mm": a, "thickness_mm": b, "circumference_mm": circ})
        return out, fin

    sections, final = cross_sections(SECTIONS, views)
    if extra_dists_mm:  # 지정한 거리(mm)에서도 재기 (줄자 비교·전극 위치용). 그림·경고에는 안 넣음
        tmp = {v: dict(views[v], warnings=[]) for v in views}
        result["sections_at_mm"] = cross_sections([d / L * 100 for d in extra_dists_mm], tmp)[0]
    for v in views:
        views[v].update(final_widths=final[v], length_mm=L, elbow_method=method)
        warnings += [f"[{names[v]}] {w}" for w in views[v]["warnings"]]

    result.update({
        "forearm_length_mm": round(L, 1),
        "forearm_length_top_mm": round(est["top"], 1) if "top" in est else None,  # 각 사진에서 찾은 팔꿈치까지 거리
        "forearm_length_side_mm": round(est["side"], 1) if "side" in est else None,
        "elbow_method": method,  # manual(직접 지정) / auto(폭 골짜기) / skin_end(못 찾음, 소매까지)
        # 손목(0점): manual(직접 지정) / auto(손바닥 아래 볼록함 끝) / mediapipe(보정 못 함)
        "wrist_method": {v: views[v]["wrist_method"] for v in views},
        "wrist_shift_mm": {v: views[v]["wrist_shift_mm"] for v in views},  # MediaPipe 손목 점에서 팔 쪽으로 옮긴 거리
        "sections": sections,
        "reference": {"top": top["ref"], "side": side["ref"]},  # 크기 기준으로 쓴 것: marker / card
        "camera_height_mm": {v: round(views[v]["H"]) if views[v]["H"] else None for v in views},
        "k": k,
        "shell_thickness_mm": shell_thickness_mm,
        "clearance_mm": clearance_mm,
        "warnings": warnings,
    })
    return result, draw_view(top, "top"), draw_view(side, "side")


def parse_xy(s):
    return [float(v) for v in s.split(",")] if s else None


def parse_quad(s):
    """'x1,y1,x2,y2,x3,y3,x4,y4' → 4x2 배열 (카드 네 모서리 탭 좌표). 비어 있으면 None."""
    if not s:
        return None
    v = [float(t) for t in s.split(",")]
    if len(v) != 8:
        raise ValueError("카드 모서리는 4점, 숫자 8개(x1,y1,...,x4,y4)로 보내주세요")
    return np.array(v, float).reshape(4, 2)


def card_scale_from_corners(q):
    """카드 네 모서리(순서 상관없음) → (mm/px 배율, 정렬된 꼭짓점). 마주 보는 변의 평균 길이로 계산."""
    q = np.asarray(q, float)
    c = q.mean(axis=0)
    q = q[np.argsort(np.arctan2(q[:, 1] - c[1], q[:, 0] - c[0]))]  # 한 바퀴 순서로 정렬
    side = lambda i: np.linalg.norm(q[(i + 1) % 4] - q[i])  # noqa: E731
    a, b = (side(0) + side(2)) / 2, (side(1) + side(3)) / 2
    L, S = max(a, b), min(a, b)
    if S < 10:
        return None, None, None
    ratio_err = abs(L / S - CARD_MM[0] / CARD_MM[1]) / (CARD_MM[0] / CARD_MM[1])
    return float((CARD_MM[0] / L + CARD_MM[1] / S) / 2), q.astype(np.float32), ratio_err


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("top", help="위에서 찍은 사진 (손등이 위)")
    ap.add_argument("side", help="옆에서 찍은 사진 (새끼손가락 쪽이 바닥)")
    ap.add_argument("--session", help="예: S01_20261031 (없으면 위 사진 파일 이름)")
    ap.add_argument("--marker-mm", type=float, default=50.0)
    ap.add_argument("--k", type=float, default=1.0, help="둘레 보정 계수 (줄자 실측 ÷ 타원 둘레)")
    ap.add_argument("--focal35", type=float, help="35mm 환산 초점거리 직접 지정 (기본: 사진 EXIF에서 읽음)")
    ap.add_argument("--at-mm", help="손목 주름에서 이 거리들(mm)에서도 재기. 예: 0,50,100,150,200")
    for v in ["top", "side"]:
        ap.add_argument(f"--wrist-{v}", help="x,y  손목 좌표 직접 지정 (원본 사진 픽셀)")
        ap.add_argument(f"--elbow-{v}", help="x,y  팔꿈치 좌표 직접 지정 (기본: 폭 골짜기로 자동 추정)")
    args = ap.parse_args()

    imgs, focal = {}, []
    for v in ["top", "side"]:
        path = getattr(args, v)
        imgs[v] = imread(path)
        if imgs[v] is None:
            raise SystemExit(f"사진을 못 읽었어요: {path}")
        focal.append(args.focal35 or exif_focal35(Path(path).read_bytes()))
    session = args.session or Path(args.top).stem
    overrides = {f"{p}_{v}": parse_xy(getattr(args, f"{p}_{v}")) for p in ["wrist", "elbow"] for v in ["top", "side"]}

    result, top_ov, side_ov = measure_arm(imgs["top"], imgs["side"], session, args.marker_mm, args.k,
                                          overrides, focal35=tuple(focal),
                                          extra_dists_mm=parse_xy(args.at_mm))

    if not result["ok"]:
        print("실패:", result["message"])
    else:
        print(f"크기 기준: 위={result['reference']['top']}, 옆={result['reference']['side']}"
              f"   카메라 높이: {result['camera_height_mm']}")
        print(f"전완 길이: {result['forearm_length_mm']} mm  [{result['elbow_method']}]"
              f"  (위 {result['forearm_length_top_mm']} / 옆 {result['forearm_length_side_mm']})")
        print(f"손목 보정: {result['wrist_method']}  옮긴 거리(mm): {result['wrist_shift_mm']}")
        print(f"{'위치':>6} {'손목에서':>8} {'폭':>8} {'두께':>8} {'둘레':>8}   (mm, k={args.k})")
        for s in result["sections"]:
            print(f"{s['pos']:>5}% {s['dist_mm']!s:>8} {s['width_mm']!s:>8} {s['thickness_mm']!s:>8} {s['circumference_mm']!s:>8}")
        for s in result.get("sections_at_mm", []):
            print(f"  손목에서 {s['dist_mm']:>6}mm: 폭 {s['width_mm']} 두께 {s['thickness_mm']} 둘레 {s['circumference_mm']}")
        for w in result["warnings"]:
            print("주의:", w)

    out_dir = Path(__file__).parent / "out"
    out_dir.mkdir(exist_ok=True)
    imwrite(out_dir / f"{session}_top_overlay.jpg", top_ov)
    imwrite(out_dir / f"{session}_side_overlay.jpg", side_ov)
    (out_dir / f"{session}_arm.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"저장: out/{session}_arm.json, out/{session}_top_overlay.jpg, out/{session}_side_overlay.jpg")


if __name__ == "__main__":
    main()

