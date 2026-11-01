r"""보조기 치수 보고서 그림 (앱 화면·설계팀 전달용).   [2026-11-01]

measure_brace()가 측정을 끝낸 뒤 make_report()를 불러 PNG 1장을 만든다.
  왼쪽 위: 위 사진(손 부분 확대) + ①③⑤~⑨ 선과 값
  왼쪽 아래: 옆 사진 + ②④⑩⑪ 선과 값
  오른쪽: 번호·정의·값(mm) 표, 측정 정보, 주의 문구
dims_csv()는 같은 값을 CSV(엑셀로 열림)로 만든다.

글꼴: 한글 글꼴이 필요함. vision/fonts/ 폴더의 .ttf/.otf를 먼저 찾고, 없으면 Windows 맑은 고딕,
      리눅스 나눔고딕·Noto CJK 순서로 찾음. 리눅스 서버에 올릴 때는 fonts/ 에 한글 글꼴 1개를 넣어두면 됨.
"""
import csv
import io
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

CIRC = "①②③④⑤⑥⑦⑧⑨⑩⑪"
COL = {1: (30, 30, 30), 2: (30, 30, 30), 3: (100, 100, 100), 4: (100, 100, 100), 5: (226, 60, 60),
       6: (150, 70, 210), 7: (240, 140, 0), 8: (30, 110, 240), 9: (20, 160, 90), 10: (30, 110, 240),
       11: (20, 160, 90)}  # RGB
VERTICAL_IDS = {5, 6, 7}
SIDE_IDS = {2, 4, 10, 11}

_FONT_DIRS = [Path(__file__).parent / "fonts"]
_FONT_FILES = {
    True: [r"C:\Windows\Fonts\malgunbd.ttf", "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
           "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", "/System/Library/Fonts/AppleSDGothicNeo.ttc"],
    False: [r"C:\Windows\Fonts\malgun.ttf", "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/System/Library/Fonts/AppleSDGothicNeo.ttc"],
}
_font_cache = {}


def font(size, bold=True):
    key = (size, bold)
    if key not in _font_cache:
        cands = []
        for d in _FONT_DIRS:
            if d.exists():
                fs = sorted(list(d.glob("*.ttf")) + list(d.glob("*.otf")) + list(d.glob("*.ttc")))
                bolds = [f for f in fs if "bold" in f.name.lower()]
                cands += (bolds + fs) if bold else ([f for f in fs if f not in bolds] + fs)
        cands += [Path(p) for p in _FONT_FILES[bold]]
        for p in cands:
            if p.exists():
                _font_cache[key] = ImageFont.truetype(str(p), size)
                break
        else:
            _font_cache[key] = ImageFont.load_default()  # 한글이 깨짐 → fonts/ 에 글꼴을 넣을 것
    return _font_cache[key]


def _panel(view, lines, dims, show_pts, title, pad):
    """사진에서 선이 있는 부분만 잘라 선·관절점·값을 그림."""
    img, pts = view["img"], view["pts"]
    allp = [np.asarray(q) for s in lines.values() if s is not None for q in s] + [pts[i] for i in show_pts]
    allp = np.array(allp)
    H, W = img.shape[:2]
    x0, y0 = np.maximum((allp.min(0) - pad).astype(int), 0)
    x1, y1 = (allp.max(0) + pad).astype(int)
    x1, y1 = min(x1, W), min(y1, H)
    crop = Image.fromarray(cv2.cvtColor(img[y0:y1, x0:x1], cv2.COLOR_BGR2RGB))
    Z = min(1.0, 1000 / max(crop.size))
    crop = crop.resize((int(crop.width * Z), int(crop.height * Z)), Image.LANCZOS)
    d = ImageDraw.Draw(crop, "RGBA")
    P = lambda q: np.array(((q[0] - x0) * Z, (q[1] - y0) * Z))  # noqa: E731
    for i, seg in lines.items():
        if seg is None:
            continue
        a, b = P(seg[0]), P(seg[1])
        d.line([tuple(a), tuple(b)], fill=COL[i] + (255,), width=7)
        if i in VERTICAL_IDS:  # 세로 거리 = 치수선 모양 (끝에 짧은 막대)
            v = (b - a) / max(np.linalg.norm(b - a), 1e-6)
            n = np.array([-v[1], v[0]]) * 15
            for q in (a, b):
                d.line([tuple(q - n), tuple(q + n)], fill=COL[i] + (255,), width=5)
        else:
            for q in (a, b):
                d.ellipse([q[0] - 9, q[1] - 9, q[0] + 9, q[1] + 9], fill=COL[i], outline="white", width=3)
    for i in show_pts:
        x, y = P(pts[i])
        d.ellipse([x - 17, y - 17, x + 17, y + 17], fill=(255, 205, 0), outline=(30, 30, 30), width=3)
        d.text((x, y), str(i), font=font(19), fill="black", anchor="mm")
    for i, seg in lines.items():
        if seg is None:
            continue
        a, b = P(seg[0]), P(seg[1])
        v = (b - a) / max(np.linalg.norm(b - a), 1e-6)
        m = (a + b) / 2 + np.array([-v[1], v[0]]) * 42 if i not in (1, 2, 3, 4) else b + v * 48
        val = f"{dims[i]:.1f}" if dims.get(i) else "?"
        d.text(tuple(m), f"{CIRC[i - 1]} {val}", font=font(30), fill=COL[i], anchor="mm",
               stroke_width=6, stroke_fill="white")
    out = Image.new("RGB", (crop.width, crop.height + 60), "white")
    ImageDraw.Draw(out).text((16, 30), title, font=font(32), fill=(25, 25, 25), anchor="lm")
    out.paste(crop, (0, 60))
    return out


def _wrap(text, fnt, width, draw):
    """글자 폭에 맞춰 줄바꿈."""
    lines, cur = [], ""
    for ch in text:
        if draw.textlength(cur + ch, font=fnt) > width and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    return lines + ([cur] if cur else [])


def make_report(result, top, side, top_lines, side_lines, dims, names):
    """측정 결과 → 보고서 그림 (OpenCV BGR 배열)."""
    ptop = _panel(top, top_lines, dims, (0, 2, 5, 18), "위 사진 (손등)", np.array([100, 60]))
    pside = _panel(side, side_lines, dims, (0,), "측면 사진 (새끼손가락 쪽이 바닥)", np.array([180, 90]))
    left_w = max(ptop.width, pside.width)
    left_h = ptop.height + 20 + pside.height

    PW = 860
    tb = Image.new("RGB", (PW, max(left_h, 1500)), (249, 249, 247))
    d = ImageDraw.Draw(tb)
    d.text((36, 28), "손목 보조기 치수 보고서", font=font(44), fill=(25, 25, 25))
    when = datetime.now().strftime("%Y-%m-%d %H:%M")
    d.text((36, 90), f"측정 ID {result.get('session_id', '')}  ·  {when}  ·  단위 mm", font=font(22, False),
           fill=(120, 120, 120))
    y = 140
    for i in range(1, 12):
        if i % 2:
            d.rounded_rectangle([24, y - 6, PW - 24, y + 58], 12, fill=(238, 239, 235))
        d.ellipse([36, y, 88, y + 52], fill=COL[i])
        d.text((62, y + 26), CIRC[i - 1], font=font(32), fill="white", anchor="mm")
        d.text((106, y + 26), names[i][0], font=font(26, False), fill=(30, 30, 30), anchor="lm")
        d.text((560, y + 26), "측면" if i in SIDE_IDS else "위", font=font(22), fill=(130, 130, 130), anchor="lm")
        val = f"{dims[i]:.1f}" if dims.get(i) else "-"
        d.text((PW - 44, y + 26), val, font=font(34), fill=COL[i], anchor="rm")
        y += 70
    y += 16
    d.text((36, y), "정의", font=font(26), fill=(60, 60, 60))
    y += 40
    for t in ["숫자 = MediaPipe 손 관절점 (0 손목, 2 엄지 첫 관절, 5 검지 뿌리, 18 새끼 가운데 마디)",
              "가로선 = 손 방향(0번→9번)에 수직인 선",
              "⑤⑥⑦ = 두 관절점을 지나는 가로선 사이 세로 거리",
              "⑧⑨ = 2번/5번에서 가로로 새끼 쪽 손 테두리까지",
              f"①② = 0번에서 팔 쪽 {result.get('fixed_length_mm', 40):.0f}mm 위치,  ⑩⑪ = 2번/5번 높이"]:
        for ln in _wrap(t, font(21, False), PW - 80, d):
            d.text((44, y), ln, font=font(21, False), fill=(90, 90, 90))
            y += 31
    y += 14
    ref = result.get("reference", {})
    camh = result.get("camera_height_mm", {})
    refname = {"card_tap": "카드(탭)", "card": "카드(자동)", "marker": "마커"}
    d.text((36, y), "측정 정보", font=font(26), fill=(60, 60, 60))
    y += 40
    d.text((44, y), f"크기 기준: 위 {refname.get(ref.get('top'), ref.get('top'))} / 측면 "
                    f"{refname.get(ref.get('side'), ref.get('side'))}", font=font(21, False), fill=(90, 90, 90))
    y += 31
    d.text((44, y), f"카메라 높이: 위 {camh.get('top')}mm / 측면 {camh.get('side')}mm", font=font(21, False),
           fill=(90, 90, 90))
    y += 31
    warns = result.get("warnings") or []
    if warns:
        y += 14
        d.text((36, y), "주의", font=font(26), fill=(190, 60, 60))
        y += 40
        for w in warns:
            for ln in _wrap("• " + w, font(21, False), PW - 80, d):
                d.text((44, y), ln, font=font(21, False), fill=(190, 60, 60))
                y += 31
    tb = tb.crop((0, 0, PW, max(y + 30, left_h)))

    H = max(left_h, tb.height)
    canvas = Image.new("RGB", (left_w + 20 + PW, H), "white")
    canvas.paste(ptop, (0, 0))
    canvas.paste(pside, (0, ptop.height + 20))
    canvas.paste(tb, (left_w + 20, 0))
    return cv2.cvtColor(np.array(canvas), cv2.COLOR_RGB2BGR)


def dims_csv(result):
    """설계팀 전달용 CSV 문자열 (엑셀에서 한글 안 깨지게 utf-8-sig로 저장할 것)."""
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(["측정ID", result.get("session_id", "")])
    w.writerow(["번호", "항목", "종류", "사진", "값(mm)"])
    kind = {"width": "폭", "thickness": "두께", "length": "길이"}
    for d in result.get("dims", []):
        w.writerow([d["id"], d["name"], kind.get(d["kind"], d["kind"]),
                    "측면" if d["id"] in SIDE_IDS else "위", d["value_mm"]])
    return buf.getvalue()
