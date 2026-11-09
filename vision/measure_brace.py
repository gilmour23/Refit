r"""손목 보조기 치수 11개 — 위·옆 사진 2장 → ①~⑪ (mm).   [2026-11-01 v2, 사용자와 확정한 정의]

숫자 = MediaPipe 손 관절점 번호 (0 손목, 2 엄지 MCP, 5 검지 뿌리, 17 새끼 뿌리, 18 새끼 가운데 마디)

    ① 0번에서 팔 쪽으로 4cm(FIXED_MM) 떨어진 곳의 가로 폭   (위 사진)
    ② ① 위치의 두께                                           (옆 사진)
    ③ 0번 위치의 가로 폭                                       (위 사진)
    ④ ③ 위치의 두께                                           (옆 사진)
    가로선 = 손 방향(0번→9번)에 수직인 선
    ⑤ 18번 ↔ 0번 세로 거리   ⑥ 2번 ↔ 0번 세로 거리   ⑦ 5번 ↔ 2번 세로 거리  (두 가로선 사이 간격, 위 사진)
    ⑧ 2번에서 가로로 새끼 쪽 손 테두리까지   ⑨ 5번에서 가로로 새끼 쪽 손 테두리까지     (위 사진)
    ⑩ 2번 높이의 두께 (⑧과 같은 높이)        ⑪ 5번 높이의 두께 (⑨와 같은 높이)        (옆 사진)
    [11-01 v3: ⑤⑥⑦ 직선→세로 거리, ⑧⑨ 관절점 직선→가로로 손 테두리까지 (사용자 정의)]

    ⑩⑪의 "높이" = 위 사진에서 0번부터 손 방향(0→9)으로 잰 거리(mm). 옆 사진에서도 0번부터 같은 거리에서 두께를 잼.

사용법:
    python measure_brace.py 위사진.jpg 옆사진.jpg --session S01
    python measure_brace.py 위.jpg 옆.jpg --tape 49,40,56,45,90,55,42,80,46,40,30   (자로 잰 ①~⑪과 비교)

출력 (out/ 폴더): <session>_brace.json, <session>_brace_top.jpg, <session>_brace_side.jpg

촬영: 손등이 위(위 사진) / 새끼손가락 쪽이 바닥(옆 사진). 손 전체 + 손목 아래 10cm + 카드.
      손가락은 붙이고, 옆 사진은 엄지를 손바닥 쪽에 붙여서 (⑩에 엄지가 섞이지 않게).
"""
import argparse
import json
from pathlib import Path

import cv2
import numpy as np

import measure_arm as M
from brace_report import dims_csv, make_report
from measure import imread, imwrite

FIXED_MM = 40.0  # 0번 ~ ①② 위치 거리. 팀 결정값 (11-01)
THUMB_MCP, INDEX_MCP, MIDDLE_MCP, PINKY_MCP, PINKY_PIP = 2, 5, 9, 17, 18
N_DIMS = 11

# 세로 거리: 번호 → (관절점, 관절점)  두 점을 지나는 가로선 사이 간격
VERTICAL = {5: (PINKY_PIP, M.WRIST), 6: (THUMB_MCP, M.WRIST), 7: (INDEX_MCP, THUMB_MCP)}
# 가로 길이: 번호 → 시작 관절점 (여기서 가로로 새끼 쪽 손 테두리까지)
HORIZONTAL = {8: THUMB_MCP, 9: INDEX_MCP}
# 두께 치수: 번호 → 어느 가로 길이와 같은 높이에서 재는지
THICK_AT = {10: 8, 11: 9}

NAMES = {
    1: ("0번에서 팔 쪽 4cm 폭", "width"), 2: ("① 위치 두께", "thickness"),
    3: ("0번(손목) 폭", "width"), 4: ("③ 위치 두께", "thickness"),
    5: ("18번 ↔ 0번 세로 거리", "length"), 6: ("2번 ↔ 0번 세로 거리", "length"),
    7: ("5번 ↔ 2번 세로 거리", "length"),
    8: ("2번 → 가로 → 새끼 쪽 손 테두리", "width"), 9: ("5번 → 가로 → 새끼 쪽 손 테두리", "width"),
    10: ("2번 높이 두께 (측면)", "thickness"), 11: ("5번 높이 두께 (측면)", "thickness"),
}


def unit(v):
    return v / np.linalg.norm(v)


def hand_axes(pts):
    """손 방향 h (0번 → 9번)와 그에 수직인 nh."""
    h = unit(pts[MIDDLE_MCP] - pts[M.WRIST])
    return h, np.array([-h[1], h[0]])


def forearm_width(view, dmm):
    """0번(손목)에서 팔 쪽으로 dmm 떨어진 곳의 전완 폭(mm, 높이 보정 전) + 그릴 선분. 앞뒤 ±3mm 7줄의 중앙값."""
    c, u, n, s_w, sc, mask = (view[k] for k in ["c", "u", "n", "s_w", "scale", "mask"])
    s = s_w + dmm / sc
    vals = []
    for ds in np.linspace(-M.WINDOW_MM / sc, M.WINDOW_MM / sc, 7):
        ch = M.chord(mask, c + (s + ds) * u, n)
        if ch:
            vals.append(ch[1] - ch[0] + 0.5)
    main = M.chord(mask, c + s * u, n)
    if not vals or main is None or main[2]:
        return None, None
    p = c + s * u
    return float(np.median(vals)) * sc, (p + main[0] * n, p + main[1] * n)


def hand_chord(view, mask, dmm):
    """손 방향으로 0번에서 dmm 떨어진 높이의 손 폭(위 사진) 또는 두께(옆 사진). mm(높이 보정 전) + 선분."""
    pts, sc = view["pts"], view["scale"]
    h, nh = hand_axes(pts)
    p = pts[M.WRIST] + dmm / sc * h
    ch = M.chord(mask, p, nh)
    if not ch or ch[2]:
        return None, None
    return (ch[1] - ch[0] + 0.5) * sc, (p + ch[0] * nh, p + ch[1] * nh)


def hand_segments(top, hand):
    """위 사진 ⑤~⑨ (mm, 높이 보정 전) + 그릴 선분, ⑩⑪을 잴 높이(0번에서 mm), 경고.

    ⑤⑥⑦ = 두 관절점을 지나는 가로선(손 방향에 수직) 사이의 세로 거리
    ⑧⑨ = 관절점(2번/5번)에서 가로로 새끼 쪽 손 테두리까지
    """
    pts, sc = top["pts"], top["scale"]
    h, nh = hand_axes(pts)
    if (pts[PINKY_MCP] - pts[INDEX_MCP]) @ nh < 0:  # nh = 새끼 쪽
        nh = -nh
    along = lambda p: float((p - pts[M.WRIST]) @ h)  # noqa: E731
    out, lines, pos, warn = {}, {}, {}, []

    ys, xs = np.nonzero(hand)
    rel = np.column_stack([xs, ys]) - pts[M.WRIST]
    t_all = [along(pts[i]) for i in (M.WRIST, PINKY_PIP, INDEX_MCP, THUMB_MCP)]
    band = (rel @ h >= min(t_all)) & (rel @ h <= max(t_all))   # 0번 ~ 18번 높이의 손 부분만
    side = rel[band] @ nh
    x_pinky, x_thumb = side.max() + 30, side.min() - 30   # 세로선은 손 바로 바깥에 그림
    at = lambda t, x: pts[M.WRIST] + t * h + x * nh  # noqa: E731
    for i, (a, b) in VERTICAL.items():
        ta, tb = along(pts[a]), along(pts[b])
        out[i] = abs(ta - tb) * sc
        x = x_pinky if i == 5 else x_thumb - (60 if i == 7 else 0)
        lines[i] = (at(ta, x), at(tb, x))
    for i, j in HORIZONTAL.items():
        p = pts[j]
        ch = M.chord(hand, p, nh)
        if ch and not ch[2] and ch[1] > 0:
            out[i] = ch[1] * sc
            lines[i] = (p, p + ch[1] * nh)
        else:
            warn.append(f"{j}번에서 가로로 손 테두리를 못 찾았어요 ({i}번). 손가락을 붙이고 흰 배경에서 찍어주세요")
    pos = {i: along(pts[HORIZONTAL[s]]) * sc for i, s in THICK_AT.items()}
    return out, lines, pos, warn


def geometry_view(view, lines, original_shape):
    """Geometry uses decoded original image pixels, independently of display zoom."""
    f = view.get('f', 1.0)
    def points(value):
        if value is None:
            return None
        a = np.asarray(value, dtype=float) / f
        return a.tolist() if np.isfinite(a).all() else None
    return {'width': int(original_shape[1]), 'height': int(original_shape[0]),
            'lines': {str(i): points(line) for i, line in lines.items()
                      if line is not None and points(line) is not None},
            'card': points(view.get('marker')),
            'wrist': points(view['pts'][0]) if 'pts' in view else None}


def measure_brace(top_img, side_img, session_id, marker_mm=50.0, fixed_mm=FIXED_MM,
                  overrides=None, focal35=(None, None)):
    """위·옆 사진 → (결과 dict, 위 overlay, 옆 overlay, 보고서 그림). 실패하면 보고서 = None."""
    ov = overrides or {}
    # 0점 = MediaPipe 0번 그대로 (손목 보정 안 함). 앱에서 손목을 탭하면 그 좌표가 0점.
    # 카드: 앱에서 네 모서리를 탭했으면 그 좌표, 아니면 자동 찾기
    top = M.measure_view(top_img, marker_mm, ov.get("wrist_top"), auto_wrist=False, card=ov.get("card_top"))
    side = M.measure_view(side_img, marker_mm, ov.get("wrist_side"), auto_wrist=False, card=ov.get("card_side"))
    names = {"top": "위", "side": "옆"}
    problems = [f"[{names[k]} 사진] {v['error']}" for k, v in [("top", top), ("side", side)] if "error" in v]
    result = {"ok": not problems, "message": " / ".join(problems) or None, "session_id": session_id,
              "analysis_version": "2026-11-08.1"}
    if problems:
        result['geometry'] = {'top': geometry_view(top, {}, top_img.shape),
                              'side': geometry_view(side, {}, side_img.shape)}
        return result, M.draw_view(top, "top"), M.draw_view(side, "side"), None
    warnings = []

    # 카메라 높이 → 높이 보정 (카드는 바닥, 재는 윤곽은 바닥에서 z만큼 위)
    for v, fl in zip([top, side], focal35):
        v["H"] = M.camera_height(v, fl)
    if not (top["H"] and side["H"]):
        warnings.append("사진에 초점거리 정보(EXIF)가 없어 높이 보정을 못 했어요. 값이 몇 % 크게 나올 수 있어요")
    hf = M.height_factor
    dims = {}

    # ①②③④ 전완: 같은 위치의 폭(위)·두께(옆)를 서로의 높이 보정에 사용
    raw = {}
    for dmm, (iw, it) in [(fixed_mm, (1, 2)), (0.0, (3, 4))]:
        w, lw = forearm_width(top, dmm)
        t, lt = forearm_width(side, dmm)
        if w is None or t is None:
            warnings.append(f"0번에서 {dmm:.0f}mm 위치의 팔 윤곽을 못 찾았어요 (소매·사진 밖)")
        raw[iw], raw[it] = (w, lw), (t, lt)
    for iw, it in [(1, 2), (3, 4)]:
        w, t = raw[iw][0], raw[it][0]
        dims[iw] = w * hf(top["H"], t / 2) if w and t else w
        dims[it] = t * hf(side["H"], w / 2) if w and t else t

    # ⑩⑪ 손 두께(옆 사진): ⑧·⑨ 선 가운데 높이에서. 높이 보정에는 같은 높이의 위 사진 손 폭을 씀
    top_hand = M.segment_arm(top["img"], top["pts"], top["pts"][M.WRIST], cut_hand=False)[0]
    seg, seg_lines, pos, seg_warn = hand_segments(top, top_hand)
    warnings += seg_warn
    side_hand = M.segment_arm(side["img"], side["pts"], side["pts"][M.WRIST], cut_hand=False)[0]
    side_lines = {i: raw[i][1] for i in (2, 4)}
    for i, dmm in pos.items():
        t, lt = hand_chord(side, side_hand, dmm)
        w, _ = hand_chord(top, top_hand, dmm)
        side_lines[i] = lt
        if t is None:
            dims[i] = None
            warnings.append(f"옆 사진에서 ⑩⑪ 위치(0번에서 {dmm:.0f}mm)의 손 윤곽을 못 찾았어요")
            continue
        dims[i] = t * hf(side["H"], (w or 80.0) / 2)

    # ⑤~⑨ (위 사진): 손등 윤곽·관절점은 높이 ≈ 손 두께(⑪)의 절반에 있다고 보고 보정
    th = dims.get(11)
    if th is None or not 15 <= th <= 45:  # 손이 기울어져 손바닥이 보이면 두께가 크게 잡힘
        warnings.append(f"⑪ 손 두께({th and round(th)}mm)가 이상해요. 새끼손가락 쪽을 바닥에 똑바로 대고 "
                        "다시 찍어주세요 (⑤~⑨ 높이 보정은 손 두께 30mm로 계산)")
    z_hand = th / 2 if th and 15 <= th <= 45 else 15.0
    for i, v in seg.items():
        dims[i] = v * hf(top["H"], z_hand)
    if dims.get(10) and dims.get(11) and dims[10] > 1.4 * dims[11]:
        warnings.append("⑩이 ⑪보다 많이 두꺼워요. 옆 사진에 엄지가 섞였을 수 있어요 (엄지를 손바닥 쪽에 붙이고 찍기)")

    for v in (top, side):
        warnings += [f"[{names['top' if v is top else 'side']}] {w}" for w in v["warnings"]]
    rd = lambda x: round(float(x), 1) if x else None  # noqa: E731
    result.update({
        "dims_mm": {str(i): rd(dims.get(i)) for i in range(1, N_DIMS + 1)},
        "dims": [{"id": i, "name": NAMES[i][0], "kind": NAMES[i][1], "value_mm": rd(dims.get(i))}
                 for i in range(1, N_DIMS + 1)],
        "fixed_length_mm": fixed_mm,
        "extras": {"thickness_pos_from_wrist_mm": {str(i): round(p, 1) for i, p in pos.items()}},
        "reference": {"top": top["ref"], "side": side["ref"]},
        "camera_height_mm": {"top": round(top["H"]) if top["H"] else None,
                             "side": round(side["H"]) if side["H"] else None},
        "wrist_method": {"top": top["wrist_method"], "side": side["wrist_method"]},
        "warnings": warnings,
    })
    top_lines = {i: raw[i][1] for i in (1, 3)} | seg_lines
    result['geometry'] = {'top': geometry_view(top, top_lines, top_img.shape),
                          'side': geometry_view(side, side_lines, side_img.shape)}
    shown = {i: rd(dims.get(i)) for i in range(1, N_DIMS + 1)}  # 보고서에는 JSON과 같은 반올림 값
    missing = [str(i) for i in range(1, N_DIMS + 1)
               if dims.get(i) is None or not np.isfinite(dims[i]) or dims[i] <= 0]
    if missing:
        result["ok"] = False
        hints = []
        if any(i in missing for i in ('1', '2')):
            hints.append("손등·측면 사진 모두 손목 아래 10cm까지 담아 주세요")
        if any(i in missing for i in ('8', '9')):
            hints.append("손과 배경의 경계를 구분하지 못했어요. 나뭇결·무늬 없는 단색 배경에서 다시 찍어 주세요")
        if not hints:
            hints.append("손 전체와 손목 아래가 보이게 다시 촬영해 주세요")
        result["message"] = "치수를 측정하지 못했어요 (" + ", ".join(missing) + "). " + ". ".join(hints) + "."
    report = make_report(result, top, side, top_lines, side_lines, shown, NAMES) if not missing else None
    return (result,
            draw(top, top_lines, dims, "top", top_hand, top["pts"], (M.WRIST, THUMB_MCP, INDEX_MCP, PINKY_MCP, PINKY_PIP)),
            draw(side, side_lines, dims, "side", side_hand, side["pts"], (M.WRIST,)),
            report)


COLORS = {1: (20, 20, 20), 2: (20, 20, 20), 3: (20, 20, 20), 4: (20, 20, 20), 5: (50, 50, 230),
          6: (200, 60, 140), 7: (0, 140, 240), 8: (255, 110, 30), 9: (30, 160, 60),
          10: (255, 110, 30), 11: (30, 160, 60)}  # BGR, ⑩=⑧ 색, ⑪=⑨ 색


def draw(view, lines, dims, label, mask, pts, show_pts):
    out = view["img"].copy()
    t = max(2, out.shape[1] // 500)
    fs = out.shape[1] / 1400
    cs, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    cv2.drawContours(out, cs, -1, (0, 200, 0), t)
    if view.get("marker") is not None:
        cv2.polylines(out, [view["marker"].astype(int)], True, (255, 0, 0), t)
    for i, seg in lines.items():
        if seg is None:
            continue
        a, b = (tuple(np.asarray(q).astype(int)) for q in seg)
        cv2.line(out, a, b, COLORS[i], t + 1)
        for q in (a, b):
            cv2.circle(out, q, 3 * t, COLORS[i], -1)
        txt = f"({i}) {dims[i]:.1f}mm" if dims.get(i) else f"({i}) ?"
        pos = (int((a[0] + b[0]) / 2) + 6 * t, int((a[1] + b[1]) / 2) - 4 * t)
        cv2.putText(out, txt, pos, cv2.FONT_HERSHEY_SIMPLEX, fs, (255, 255, 255), 4 * t)
        cv2.putText(out, txt, pos, cv2.FONT_HERSHEY_SIMPLEX, fs, COLORS[i], t)
    for i in show_pts:  # 쓰는 관절점 + 번호
        p = tuple(pts[i].astype(int))
        cv2.circle(out, p, 4 * t, (0, 220, 255), -1)
        cv2.putText(out, str(i), (p[0] - 12 * t, p[1] - 5 * t), cv2.FONT_HERSHEY_SIMPLEX, fs * 0.8, (0, 0, 0), 2 * t)
    head = f"{label} ({view['ref']})" + (f"  H={view['H']:.0f}mm" if view.get("H") else "")
    cv2.putText(out, head, (20, int(50 * fs)), cv2.FONT_HERSHEY_SIMPLEX, fs * 1.2, (0, 0, 0), 3 * t)
    cv2.putText(out, head, (20, int(50 * fs)), cv2.FONT_HERSHEY_SIMPLEX, fs * 1.2, (255, 255, 255), t)
    return out


def main():
    ap = argparse.ArgumentParser(description="손목 보조기 치수 ①~⑪")
    ap.add_argument("top", help="위 사진 (손등이 위)")
    ap.add_argument("side", help="옆 사진 (새끼손가락 쪽이 바닥)")
    ap.add_argument("--session")
    ap.add_argument("--marker-mm", type=float, default=50.0)
    ap.add_argument("--fixed-mm", type=float, default=FIXED_MM, help="0번 ~ ①② 위치 거리 (기본 40)")
    ap.add_argument("--focal35", type=float)
    ap.add_argument("--wrist-top", help="x,y 손목 0점 좌표 직접 지정 (원본 픽셀). 비우면 MediaPipe 0번")
    ap.add_argument("--wrist-side")
    ap.add_argument("--card-top", help="위 사진 카드 네 모서리 x1,y1,x2,y2,x3,y3,x4,y4 (원본 픽셀). 비우면 자동")
    ap.add_argument("--card-side", help="옆 사진 카드 네 모서리 (원본 픽셀). 비우면 자동")
    ap.add_argument("--tape", help=f"자로 잰 ①~⑪ (mm, 쉼표 {N_DIMS}개, 모르면 비워서 ,,) → 비교표 출력")
    args = ap.parse_args()

    here = Path(__file__).parent
    paths, imgs, focal = [], [], []
    for p in (args.top, args.side):
        path = next((c for c in [Path(p), here / p, here / "photos" / p] if c.exists()), None)
        if path is None:
            raise SystemExit(f"사진을 못 찾았어요: {p}")
        paths.append(path)
        imgs.append(imread(str(path)))
        focal.append(args.focal35 or M.exif_focal35(path.read_bytes()))
    session = args.session or paths[0].stem
    ov = {"wrist_top": M.parse_xy(args.wrist_top), "wrist_side": M.parse_xy(args.wrist_side),
          "card_top": M.parse_quad(args.card_top), "card_side": M.parse_quad(args.card_side)}

    result, top_ov, side_ov, report = measure_brace(imgs[0], imgs[1], session, args.marker_mm, args.fixed_mm,
                                                    ov, tuple(focal))
    if not result["ok"]:
        print("실패:", result["message"])
    else:
        tape = [None] * N_DIMS
        if args.tape:
            tape = ([float(v) if v.strip() else None for v in args.tape.split(",")] + [None] * N_DIMS)[:N_DIMS]
        print(f"크기 기준: {result['reference']}  카메라 높이: {result['camera_height_mm']}  "
              f"고정 길이: {args.fixed_mm:.0f}mm")
        print(f"{'번호':>4}  {'사진(mm)':>8}  {'자(mm)':>7}  {'차이':>6}   항목")
        for d, tp in zip(result["dims"], tape):
            v = d["value_mm"]
            diff = f"{v - tp:+.1f}" if v and tp else ""
            print(f"  ({d['id']:>2})  {v!s:>8}  {tp if tp else '':>7}  {diff:>6}   {d['name']}")
        print(f"참고: ⑩⑪ 잰 높이(0번에서) {result['extras']['thickness_pos_from_wrist_mm']} mm")
        for w in result["warnings"]:
            print("주의:", w)

    out = here / "out"
    out.mkdir(exist_ok=True)
    imwrite(out / f"{session}_brace_top.jpg", top_ov)
    imwrite(out / f"{session}_brace_side.jpg", side_ov)
    (out / f"{session}_brace.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"저장: out/{session}_brace.json, out/{session}_brace_top.jpg, out/{session}_brace_side.jpg")
    if report is not None:  # 설계팀 전달용: 보고서 그림 + 치수표(CSV, 엑셀에서 열림)
        cv2.imencode(".jpg", report, [cv2.IMWRITE_JPEG_QUALITY, 90])[1].tofile(str(out / f"{session}_report.jpg"))
        (out / f"{session}_dims.csv").write_text(dims_csv(result), encoding="utf-8-sig")
        print(f"보고서: out/{session}_report.jpg, out/{session}_dims.csv")


if __name__ == "__main__":
    main()

