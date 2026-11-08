"""Find an ID-1 card when the photograph contains more than one background.

Proposals come from colour regions and closed edges; each proposal is checked
against its own surrounding background. No hand landmarks or dimensions are
inferred here. Ambiguous, clipped or nonrectangular references are rejected.
"""
import cv2
import numpy as np

ASPECT = 85.6 / 53.98


def _region(shape, box, factor=1):
    mask = np.zeros(shape, np.uint8)
    center = box.mean(axis=0)
    cv2.fillConvexPoly(mask, ((box - center) * factor + center).astype(np.int32), 255)
    return mask


def _rectangle(contour, shape, aspect_tolerance=.14, min_fill=.85):
    rect = cv2.minAreaRect(contour)
    long, short = sorted(rect[1], reverse=True)
    h, w = shape
    if short < .05 * min(h, w) or abs(long / short - ASPECT) > aspect_tolerance:
        return None
    area = long * short
    if not .008 * h * w < area < .15 * h * w:
        return None
    fill = cv2.contourArea(contour) / area
    if fill < min_fill:
        return None
    box = cv2.boxPoints(rect)
    if np.any(box < 3) or np.any(box[:, 0] > w - 4) or np.any(box[:, 1] > h - 4):
        return None
    return box, min(fill, 1.)


def find_local_card(image, arm):
    """Return card corners in image pixels, or None when evidence is insufficient."""
    f = min(1., 1600 / max(image.shape[:2]))
    img = cv2.resize(image, None, fx=f, fy=f, interpolation=cv2.INTER_AREA) if f < 1 else image
    h, w = img.shape[:2]
    excluded = cv2.resize(arm, (w, h), interpolation=cv2.INTER_NEAREST) > 0
    smooth = cv2.GaussianBlur(img, (5, 5), 0)
    lab = cv2.cvtColor(smooth, cv2.COLOR_BGR2LAB).astype(np.float32)
    hsv = cv2.cvtColor(smooth, cv2.COLOR_BGR2HSV)
    seeds = []

    def add_seed(contour, colour=False):
        hull = cv2.convexHull(contour)
        result = _rectangle(hull, (h, w), .45 if colour else .16)
        if result is None:
            return
        box, _ = result
        rect = cv2.minAreaRect(hull)
        if cv2.contourArea(contour) / (rect[1][0] * rect[1][1]) < (.8 if colour else .55):
            return
        quad = cv2.approxPolyDP(hull, .025 * cv2.arcLength(hull, True), True)
        if len(quad) != 4:
            return
        center = box.mean(axis=0)
        if any(np.linalg.norm(center - b.mean(axis=0)) < 12 and
               abs(cv2.contourArea(box) / cv2.contourArea(b) - 1) < .2 for b in seeds):
            return
        region = _region((h, w), box) > 0
        if excluded[region].mean() > .02:
            return
        seeds.append(box)

    # Printed blue/coloured cards can have a pale strip outside the coloured body.
    # A colour proposal therefore has a looser aspect gate; the final card does not.
    for hue in range(0, 180, 10):
        delta = np.abs(hsv[:, :, 0].astype(np.int16) - hue)
        delta = np.minimum(delta, 180 - delta)
        mask = ((delta < 12) & (hsv[:, :, 1] > 55) & (hsv[:, :, 2] > 45)).astype(np.uint8) * 255
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            add_seed(contour, colour=True)

    gray = cv2.GaussianBlur(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), (5, 5), 0)
    for low, high in ((10, 30), (20, 60), (40, 100), (80, 180)):
        edge = cv2.Canny(gray, low, high)
        edge = cv2.morphologyEx(edge, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
        contours, _ = cv2.findContours(edge, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            add_seed(contour)

    # Bound work on textured scenes. Excessive competing objects are unsafe as a
    # scale reference; the operator can still select the four corners explicitly.
    if len(seeds) > 32:
        return None
    found = []
    for box in seeds:
        center = box.mean(axis=0)
        near = _region((h, w), box, 1.55)
        ring = (_region((h, w), box, 1.35) > 0) & (_region((h, w), box, 1.1) == 0)
        if ring.sum() < 100:
            continue
        diff = lab - np.median(lab[ring], axis=0)
        diff[:, :, 0] *= .5
        distance = np.linalg.norm(diff, axis=2)
        interior = _region((h, w), box, .7) > 0
        level = float(np.median(distance[interior]))
        noise = max(8., 2 * float(np.median(distance[ring])))
        boundary = cv2.dilate(near, np.ones((5, 5), np.uint8)) != cv2.erode(near, np.ones((5, 5), np.uint8))
        for fraction in (0., .25, .4, .5, .65):
            mask = ((distance > max(noise, fraction * level)) & (near > 0)).astype(np.uint8) * 255
            mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
            contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            for contour in contours:
                result = _rectangle(contour, (h, w))
                if result is None or cv2.pointPolygonTest(contour, tuple(map(float, center)), False) < 0:
                    continue
                candidate, fill = result
                region = np.zeros((h, w), np.uint8)
                cv2.drawContours(region, [contour], -1, 255, -1)
                if np.any((region > 0) & boundary) or excluded[region > 0].mean() > .02:
                    continue
                if np.linalg.norm(candidate.mean(axis=0) - center) > .2 * max(cv2.minAreaRect(box)[1]):
                    continue
                found.append((candidate, fill))
    if not found:
        return None
    # Merge repeated thresholds for one card, preferring its complete boundary.
    groups = []
    for box, fill in sorted(found, key=lambda item: cv2.contourArea(item[0]), reverse=True):
        if any(np.linalg.norm(box.mean(axis=0) - b.mean(axis=0)) < .2 * max(cv2.minAreaRect(b)[1]) for b in groups):
            continue
        groups.append(box)
    if len(groups) != 1:
        return None
    return groups[0] / f
