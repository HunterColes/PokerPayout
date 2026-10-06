#!/usr/bin/env python3
"""Layout heuristics over a device tour's UI dumps (stdlib; Pillow optional, for pictures).

    layout_check.py REPORT_DIR [REPORT_DIR ..] [--allow FILE]

Each REPORT_DIR is one tour run (tour.sh's output: NN-step.xml + NN-step.png). The screen's size,
density and font scale come from REPORT_DIR/display.env (written by the matrix's `profile` step),
else from the "Device:" line of REPORT_DIR/index.md. Writes REPORT_DIR/checks.json and, with
Pillow, NN-step.checks.png: the screenshot with each finding boxed (red = fail, orange = warn).

Checks on every dump (the window uiautomator saw after the step):
    offscreen    a text node beyond the screen (FAIL), or touching its left/right edge (warn)
    under-bars   a text node under the status bar or the navigation bar (warn; portrait only)
    small-target a clickable node under 48 x 48 dp, at the profile's density (warn)
    overlap      two clickable nodes, neither inside the other, overlapping (warn; FAIL when the
                 overlap covers half of the smaller one, so a tap there can't be told apart)
    ellipsis     a text ending in "…" or "..." (warn). Compose reports a Text's full string to
                 uiautomator even when it draws it ellipsized, so this only sees an ellipsis that
                 is in the string itself; text-fit and cut-text are what find ellipsized text.
    text-fit     a one-line text box (or a text field's text area) narrower than its text could
                 be drawn even at the app's smallest type, or a text box too short for one line
                 of it: it can't be showing all of it (warn)
With two or more REPORT_DIRs (the matrix's profiles), and Pillow:
    cut-text     the same text in the same step on several profiles, whose ink (the glyphs drawn
                 in the screenshot, line by line, over the line pitch) is much shorter than on
                 another profile: it is drawn cut off, ellipsized, or not at all (warn). Wrapping
                 doesn't count. A text field's value is measured inside its padding.

A node that touches the edge of its scrolling container (or the window) is clipped by it, so its
bounds are not its size: it is left out of the size, overlap and cut-text checks.

Allow-list (default scripts/device/layout-allow.txt), one rule per line:
    check | step glob | regex on the node's label | why
The label is the node's text, or "desc=<content-desc>" when it has none.
"""

import argparse
import fnmatch
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
STEP_RE = re.compile(r"^(\d+)-(.+)\.xml$")
HERE = os.path.dirname(os.path.abspath(__file__))
MIN_TARGET_DP = 48
CUT_INK_RATIO = 0.7          # drawn under 70 % as long as on the best profile -> cut-text

try:
    from PIL import Image, ImageDraw
except ImportError:          # the checks that need pixels are skipped
    Image = None


class Node:
    __slots__ = ("a", "parent", "kids", "b", "clipped")

    def __init__(self, attrs, parent):
        self.a, self.parent, self.kids, self.clipped = attrs, parent, [], False
        m = BOUNDS_RE.match(attrs.get("bounds", ""))
        self.b = tuple(int(v) for v in m.groups()) if m else (0, 0, 0, 0)

    text = property(lambda s: s.a.get("text", ""))
    desc = property(lambda s: s.a.get("content-desc", ""))
    cls = property(lambda s: s.a.get("class", ""))
    w = property(lambda s: s.b[2] - s.b[0])
    h = property(lambda s: s.b[3] - s.b[1])

    def flag(self, name):
        return self.a.get(name) == "true"

    def label(self):
        return self.text or ("desc=" + self.desc if self.desc else "")

    def ancestors(self):
        p = self.parent
        while p is not None:
            yield p
            p = p.parent


def load_dump(path):
    root = ET.parse(path).getroot()
    nodes = []

    def walk(el, parent):
        n = Node(dict(el.attrib), parent)
        nodes.append(n)
        if parent is not None:
            parent.kids.append(n)
        for c in el:
            if c.tag == "node":
                walk(c, n)

    for top in root:
        if top.tag == "node":
            walk(top, None)
    rotation = int(root.get("rotation", "0") or 0)
    # Clipped: touching the edge of a scrolling ancestor or of the window means the node's
    # bounds were cut to that edge (uiautomator reports the visible part only).
    window = nodes[0].b if nodes else (0, 0, 0, 0)
    for n in nodes:
        boxes = [p.b for p in n.ancestors() if p.flag("scrollable")] + [window]
        n.clipped = any(touches_edge(n.b, box) for box in boxes)
    return nodes, rotation


def touches_edge(b, box, slack=1):
    if box == (0, 0, 0, 0):
        return False
    x1, y1, x2, y2 = b
    bx1, by1, bx2, by2 = box
    return y1 <= by1 + slack or y2 >= by2 - slack or x1 <= bx1 + slack or x2 >= bx2 - slack


def read_display(report_dir):
    """width, height (portrait, px), density, font_scale, status_bar, nav_bar, profile."""
    env = {}
    path = os.path.join(report_dir, "display.env")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if "=" in line:
                k, v = line.strip().split("=", 1)
                env[k] = v
    else:
        index = os.path.join(report_dir, "index.md")
        text = open(index, encoding="utf-8").read() if os.path.exists(index) else ""
        m = re.search(r"(\d+)x(\d+) @ (\d+)dpi", text)
        if m:
            env.update(width=m.group(1), height=m.group(2), density=m.group(3))
    return {
        "profile": env.get("profile", os.path.basename(os.path.normpath(report_dir))),
        "width": int(env.get("width", 1080)), "height": int(env.get("height", 2400)),
        "density": int(env.get("density", 420)), "font_scale": float(env.get("font_scale", 1.0)),
        "status_bar": int(env.get("status_bar", 0)), "nav_bar": int(env.get("nav_bar", 0)),
    }


def load_allow(path):
    rules = []
    if path and os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split("|")]
            if len(parts) >= 3:
                rules.append((parts[0], parts[1], re.compile(parts[2])))
    return rules


def allowed(rules, check, step, label):
    return any(c in (check, "*") and fnmatch.fnmatch(step, g) and rx.search(label)
               for c, g, rx in rules)


def overlap(a, b):
    w = min(a[2], b[2]) - max(a[0], b[0])
    h = min(a[3], b[3]) - max(a[1], b[1])
    return (w, h) if w > 0 and h > 0 else (0, 0)


def dump_findings(nodes, rotation, disp, step):
    """The single-dump checks; yields (check, severity, node, detail)."""
    dpx = disp["density"] / 160.0
    W, H = disp["width"], disp["height"]
    if rotation in (1, 3):
        W, H = H, W
    texts = [n for n in nodes if n.text.strip() and n.w > 0 and n.h > 0]
    for n in texts:
        x1, y1, x2, y2 = n.b
        if x1 < 0 or y1 < 0 or x2 > W or y2 > H:
            yield "offscreen", "fail", n, "beyond the %dx%d screen" % (W, H)
        elif x1 <= 0 or x2 >= W:
            yield "offscreen", "warn", n, "touches the screen's %s edge" % ("left" if x1 <= 0 else "right")
        if rotation == 0 and disp["status_bar"] and y1 < disp["status_bar"] and y2 > 0:
            yield "under-bars", "warn", n, "under the status bar (top %d px)" % disp["status_bar"]
        if rotation == 0 and disp["nav_bar"] and y2 > H - disp["nav_bar"] and y1 < H:
            yield "under-bars", "warn", n, "under the navigation bar (bottom %d px)" % disp["nav_bar"]
        t = n.text.rstrip()
        if t.endswith("…") or t.endswith("..."):
            yield "ellipsis", "warn", n, "ends with an ellipsis"
        if not n.clipped:
            short = too_narrow(n, dpx, disp["font_scale"])
            if short:
                yield "text-fit", "warn", n, short
    clickables = [n for n in nodes if (n.flag("clickable") or n.flag("long-clickable")) and n.w > 0 and n.h > 0]
    for n in clickables:
        if n.clipped:
            continue
        wdp, hdp = n.w / dpx, n.h / dpx
        if min(wdp, hdp) < MIN_TARGET_DP - 0.5:
            yield "small-target", "warn", n, "%.0f x %.0f dp" % (wdp, hdp)
    for i, a in enumerate(clickables):
        anc_a = set(map(id, a.ancestors()))
        for b in clickables[i + 1:]:
            if id(b) in anc_a or id(a) in set(map(id, b.ancestors())):
                continue
            ow, oh = overlap(a.b, b.b)
            if ow / dpx < 2 or oh / dpx < 2:
                continue
            frac = ow * oh / float(min(a.w * a.h, b.w * b.h))
            sev = "fail" if frac >= 0.5 and not (a.clipped or b.clipped) else "warn"
            yield "overlap", sev, a, "overlaps %r by %.0f x %.0f dp (%.0f%% of the smaller)" % (
                b.label() or b.cls.rsplit(".", 1)[-1], ow / dpx, oh / dpx, frac * 100)


# text-fit: the narrowest a text can be drawn is at the app's smallest type (9 sp) in a condensed
# face: about 0.38 em, 3.4 dp, a letter (half that for a space); Android 14 scales text that small
# by the full font scale. A box that is one line high (under
# two 13 dp lines) and narrower than that can't be showing all of its text. A text field's box has
# 16 dp of padding each side.
MIN_CHAR_DP, MIN_LINE_DP, FIELD_PAD_DP = 3.4, 13.0, 16.0
MIN_GLYPH_DP = 9.0   # a line of the smallest type is at least this tall: a shorter box is squeezed


def too_narrow(n, dpx, font_scale):
    text = n.text.strip()
    w_dp, h_dp = n.w / dpx, n.h / dpx
    if text and h_dp < 0.8 * MIN_GLYPH_DP * font_scale:
        return "box %.0f dp tall: too short for a line of text at font %.1f (squeezed out)" % (h_dp, font_scale)
    if len(text) < 3:
        return None
    field = "EditText" in n.cls
    if field:
        w_dp -= 2 * FIELD_PAD_DP
    elif h_dp >= 2 * MIN_LINE_DP * font_scale:
        return None          # may be two lines or more: no simple bound
    need = (sum(1 for c in text if not c.isspace()) + 0.5 * sum(1 for c in text if c.isspace())) \
        * MIN_CHAR_DP * font_scale
    if w_dp < 0.95 * need:
        return "%s %.0f dp wide; %d characters need at least %.0f dp" % (
            "field's text area" if field else "box", max(w_dp, 0), len(text), need)
    return None


def ink_length(img, b, glyph_pitch=False):
    """How long the drawn text in box b is, in units of its line pitch: the glyphs' extent on each
    line (rows of ink), added up, over (box height / lines). Wrapping doesn't change it; cutting a
    text off or ellipsizing it shortens it; a text not drawn at all gives 0. None if b is too small."""
    x1, y1, x2, y2 = b
    if x2 - x1 < 4 or y2 - y1 < 4:
        return None
    crop = img.crop((x1, y1, x2, y2)).convert("RGB")
    w, h = crop.size
    px = crop.load()
    counts = {}
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            c = px[x, y]
            counts[c] = counts.get(c, 0) + 1
    bg = max(counts, key=counts.get)

    def dist(c):
        return abs(c[0] - bg[0]) + abs(c[1] - bg[1]) + abs(c[2] - bg[2])
    # Ink is what stands out from the box's main colour by half the text's own contrast, so
    # low-contrast text (an unselected tab's grey) counts as fully as white on green.
    strongest = max(dist(px[x, y]) for y in range(0, h, 2) for x in range(0, w, 2))
    if strongest < 40:
        return 0.0
    threshold = max(30, 0.45 * strongest)
    rows = [[x for x in range(w) if dist(px[x, y]) > threshold] for y in range(h)]
    bands, cur = [], None          # runs of rows with ink, gaps of up to 2 px bridged
    for y, r in enumerate(rows):
        if r:
            if cur and y - cur[1] <= 3:
                cur[1] = y
                cur[2] = min(cur[2], r[0]); cur[3] = max(cur[3], r[-1])
            else:
                cur = [y, y, r[0], r[-1]]
                bands.append(cur)
    bands = [bd for bd in bands if bd[1] - bd[0] >= 2]    # specks, underlines
    if not bands:
        return 0.0
    total = sum(bd[3] - bd[2] + 1 for bd in bands)
    if glyph_pitch:   # a text field: its value is the lowest line (a big floating label can reach
        bd = bands[-1]  # into the box above it), measured against the glyphs' own height, up to
        pitch = bd[1] - bd[0] + 1   # the first gap wider than a letter (a dropdown's arrow)
        cols = sorted({x for y in range(bd[0], bd[1] + 1) for x in rows[y]})
        end = cols[0]
        for x in cols[1:]:
            if x - end > pitch:
                break
            end = x
        return (end - cols[0] + 1) / float(pitch)
    return total / (float(h) / len(bands))


def step_files(report_dir):
    out = []
    for f in sorted(os.listdir(report_dir)):
        m = STEP_RE.match(f)
        if m:
            out.append((m.group(1) + "-" + m.group(2), m.group(2), os.path.join(report_dir, f)))
    return out


def finding(step_id, step, check, sev, node, detail):
    return {"step_id": step_id, "step": step, "check": check, "severity": sev,
            "label": node.label(), "bounds": list(node.b), "detail": detail}


def check_dir(report_dir, rules):
    disp = read_display(report_dir)
    found, dumps = [], {}
    for step_id, step, path in step_files(report_dir):
        try:
            nodes, rotation = load_dump(path)
        except ET.ParseError:
            continue
        dumps[step] = (step_id, nodes, rotation)
        step_disp = dict(disp)
        override = os.path.join(report_dir, step_id + ".density")   # a step that changed it (rail)
        if os.path.exists(override):
            m = re.search(r"density=(\d+)", open(override, encoding="utf-8").read())
            if m:
                step_disp.update(density=int(m.group(1)), status_bar=0, nav_bar=0)
        for check, sev, node, detail in dump_findings(nodes, rotation, step_disp, step):
            if not allowed(rules, check, step, node.label()):
                found.append(finding(step_id, step, check, sev, node, detail))
    return disp, found, dumps


def cut_text(runs, rules):
    """Cross-profile: the same text in the same step, drawn much shorter than on another profile."""
    if Image is None or len(runs) < 2:
        return {}
    groups = {}
    for rdir, (disp, _, dumps) in runs.items():
        dpx = disp["density"] / 160.0
        for step, (step_id, nodes, rotation) in dumps.items():
            seen = {}
            for n in nodes:
                t = n.text.strip()
                if len(t) < 3 or n.clipped or n.h <= 0 or too_narrow(n, dpx, disp["font_scale"]):
                    continue   # (a box text-fit already flags is no yardstick for the others)
                k = (t, n.cls)
                seen[k] = seen.get(k, 0) + 1
                groups.setdefault((step,) + k + (seen[k],), []).append((rdir, step_id, n, dpx))
    images, out = {}, {}
    for (step, text, cls, occ), items in groups.items():
        if len(items) < 2:
            continue
        ratios = []
        for rdir, step_id, n, dpx in items:
            key = (rdir, step_id)
            if key not in images:
                png = os.path.join(rdir, step_id + ".png")
                try:
                    images[key] = Image.open(png) if os.path.exists(png) else None
                except OSError:
                    images[key] = None
            img = images[key]
            if img is None or img.size[0] < n.b[2] or img.size[1] < n.b[3]:
                continue
            box, field = n.b, "EditText" in n.cls
            if field:   # the value's line, inside the field's padding, under its floating label
                x1, y1, x2, y2 = n.b
                box = (int(x1 + 12 * dpx), int(y1 + 0.3 * n.h), int(x2 - 4 * dpx), int(y2 - 0.15 * n.h))
            ink = ink_length(img, box, glyph_pitch=field)
            if ink is not None:
                ratios.append((ink, rdir, step_id, n))
        if len(ratios) < 2:
            continue
        best = max(ratios, key=lambda r: r[0])
        if best[0] <= 0:
            continue
        for r, rdir, step_id, n in ratios:
            if r < best[0] * CUT_INK_RATIO and not allowed(rules, "cut-text", step, n.label()):
                prof = runs[best[1]][0]["profile"]
                out.setdefault(rdir, []).append(finding(
                    step_id, step, "cut-text", "warn", n,
                    "drawn %.0f%% as long as on %s: cut off or ellipsized" % (100 * r / best[0], prof)))
    return out


def annotate(report_dir, findings):
    if Image is None:
        return
    by_step = {}
    for f in findings:
        by_step.setdefault(f["step_id"], []).append(f)
    for step_id, items in by_step.items():
        png = os.path.join(report_dir, step_id + ".png")
        if not os.path.exists(png):
            continue
        try:
            img = Image.open(png).convert("RGB")
        except OSError:
            continue
        draw = ImageDraw.Draw(img)
        lw = max(3, img.size[0] // 300)
        for f in sorted(items, key=lambda f: f["severity"] == "fail"):
            colour = (255, 40, 40) if f["severity"] == "fail" else (255, 150, 0)
            x1, y1, x2, y2 = f["bounds"]
            draw.rectangle((x1 - lw, y1 - lw, x2 + lw, y2 + lw), outline=colour, width=lw)
        img.save(os.path.join(report_dir, step_id + ".checks.png"), optimize=True)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dirs", nargs="+")
    ap.add_argument("--allow", default=os.path.join(HERE, "layout-allow.txt"))
    a = ap.parse_args(argv)
    rules = load_allow(a.allow)
    runs = {}
    for d in a.dirs:
        runs[d] = check_dir(d, rules)
    for d, extra in cut_text(runs, rules).items():
        runs[d][1].extend(extra)
    for d, (disp, found, _) in runs.items():
        found.sort(key=lambda f: (f["step_id"], f["severity"] != "fail", f["check"]))
        with open(os.path.join(d, "checks.json"), "w", encoding="utf-8") as fh:
            json.dump({"display": disp, "pillow": Image is not None, "findings": found}, fh, indent=1,
                      ensure_ascii=False)
        annotate(d, found)
        fails = sum(f["severity"] == "fail" for f in found)
        print("[checks] %s: %d fail, %d warn" % (disp["profile"], fails, len(found) - fails))


if __name__ == "__main__":
    sys.exit(main())
