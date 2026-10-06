#!/usr/bin/env python3
"""Writes the device matrix's report from its profile runs (stdlib; Pillow optional, for thumbnails).

    matrix_report.py MATRIX_DIR [--key-screens "launch bank ..."] [--variant debug] [--mode smoke]
                     [--wall SECONDS] [--interrupted 0|1] [--display-after "1080x2400 @ 420dpi, ..."]

Reads MATRIX_DIR/profiles.tsv and, per profile, <profile>/steps.tsv, summary.env, display.env and
checks.json (layout_check.py). Writes MATRIX_DIR/index.md, MATRIX_DIR/index.html (the contact
sheet: rows = profiles, columns = key screens), MATRIX_DIR/contact-sheet.png (the same grid as
one picture, with Pillow) and MATRIX_DIR/summary.env (verdict=PASS|FAIL).
A profile fails when one of its steps fails, the tour saw a crash or ANR, or a layout check fails;
warnings never fail it.
"""

import argparse
import csv
import datetime
import html
import json
import os
import sys

try:
    from PIL import Image
except ImportError:
    Image = None

THUMB = (216, 384)


def read_tsv(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8", newline="") as fh:
        return list(csv.DictReader(fh, delimiter="\t", quoting=csv.QUOTE_NONE))


def read_env(path):
    env = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if "=" in line:
                k, v = line.rstrip("\n").split("=", 1)
                env[k] = v
    return env


def thumb(mdir, prof, step_id, has_findings):
    """Relative path of the picture to show for a step (annotated if it has findings)."""
    src = "%s/%s%s.png" % (prof, step_id, ".checks" if has_findings else "")
    if not os.path.exists(os.path.join(mdir, src)):
        src = "%s/%s.png" % (prof, step_id)
    if not os.path.exists(os.path.join(mdir, src)):
        return None, None
    if Image is None:
        return src, src
    rel = "thumbs/%s/%s.jpg" % (prof, step_id)
    out = os.path.join(mdir, rel)
    if not os.path.exists(out) or os.path.getmtime(out) < os.path.getmtime(os.path.join(mdir, src)):
        os.makedirs(os.path.dirname(out), exist_ok=True)
        try:
            im = Image.open(os.path.join(mdir, src)).convert("RGB")
            im.thumbnail(THUMB)
            im.save(out, quality=82)
        except OSError:
            return src, src
    return rel, src


def load(mdir):
    profiles = []
    for row in read_tsv(os.path.join(mdir, "profiles.tsv")):
        name = row["profile"]
        pdir = os.path.join(mdir, name)
        steps = read_tsv(os.path.join(pdir, "steps.tsv"))
        summary = read_env(os.path.join(pdir, "summary.env"))
        disp = read_env(os.path.join(pdir, "display.env"))
        checks = {}
        if os.path.exists(os.path.join(pdir, "checks.json")):
            checks = json.load(open(os.path.join(pdir, "checks.json"), encoding="utf-8"))
        findings = checks.get("findings", [])
        by_step = {}
        for f in findings:
            by_step.setdefault(f["step_id"], []).append(f)
        failed_steps = [s for s in steps if s["status"] != "PASS"]
        check_fails = [f for f in findings if f["severity"] == "fail"]
        tour_ok = summary.get("verdict") == "PASS"
        verdict = "PASS" if tour_ok and not failed_steps and not check_fails else "FAIL"
        profiles.append(dict(row=row, name=name, steps=steps, summary=summary, disp=disp,
                             findings=findings, by_step=by_step, failed_steps=failed_steps,
                             check_fails=check_fails, verdict=verdict))
    return profiles


def dp_size(p):
    d = p["disp"]
    try:
        dens = int(d["density"])
        return "%d x %d dp" % (round(int(d["width"]) * 160 / dens), round(int(d["height"]) * 160 / dens))
    except (KeyError, ValueError, ZeroDivisionError):
        return "?"


def screen(p):
    r = p["row"]
    size = r["size"] if r["size"] != "-" else "1080x2400"
    dens = r["density"] if r["density"] != "-" else "420"
    return "%s @ %s" % (size, dens)


def md_escape(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


def unique_warnings(profiles):
    """(check, label) -> list of (profile, step_id, finding), warnings only."""
    groups = {}
    for p in profiles:
        for f in p["findings"]:
            if f["severity"] != "warn":
                continue
            # a node without a label is told apart by what was found (its size, say)
            groups.setdefault((f["check"], f["label"] or "(no label: %s)" % f["detail"]), []).append((p, f))
    return sorted(groups.items(), key=lambda kv: (kv[0][0], -len(kv[1]), kv[0][1]))


def write_md(mdir, profiles, a, verdict):
    lines = []
    w = lines.append
    w("# Device matrix: %s" % verdict)
    w("")
    w("- When: %s" % datetime.datetime.now().astimezone().isoformat(timespec="seconds"))
    w("- App: %s build; %s steps; %d profiles; wall time %s" % (
        a.variant, a.mode, len(profiles), fmt_secs(a.wall)))
    if a.interrupted:
        w("- **Interrupted**: the run was stopped before every profile ran.")
    w("- Display after the run: %s" % (a.display_after or "?"))
    w("- Contact sheet: [index.html](index.html) (rows = profiles, columns = key screens), and the same grid "
      "as one picture: [contact-sheet.png](contact-sheet.png)")
    w("- A profile fails on a failing step, a crash or ANR, or a failed layout check. Warnings are "
      "heuristics: look at the picture (boxed in orange) before calling one a bug.")
    w("")
    w("## Profiles")
    w("")
    w("| Profile | Screen | dp | Font | Rot | Result | Steps | Check fails | Warnings | Time |")
    w("|---|---|---|---|---|---|---|---|---|---|")
    for p in profiles:
        r = p["row"]
        warns = len(p["findings"]) - len(p["check_fails"])
        w("| `%s` | %s | %s | %s | %s | **%s** | %d/%d | %d | %d | %s |" % (
            p["name"], screen(p), dp_size(p), r["font"], r["rotation"], p["verdict"],
            len(p["steps"]) - len(p["failed_steps"]), len(p["steps"]), len(p["check_fails"]), warns,
            fmt_secs(r["seconds"])))
    w("")
    fails = [(p, s) for p in profiles for s in p["failed_steps"]]
    w("## Failing steps (%d)" % len(fails))
    w("")
    if fails:
        w("| Profile | Step | Detail | Screenshot |")
        w("|---|---|---|---|")
        for p, s in fails:
            w("| `%s` | `%s` | %s | [%s.png](%s/%s.png) |" % (
                p["name"], s["name"], md_escape(s["detail"])[:300], s["id"], p["name"], s["id"]))
    else:
        w("None.")
    w("")
    cf = [(p, f) for p in profiles for f in p["check_fails"]]
    w("## Layout check failures (%d)" % len(cf))
    w("")
    if cf:
        w("| Profile | Step | Check | Node | Detail | Picture |")
        w("|---|---|---|---|---|---|")
        for p, f in cf:
            w("| `%s` | `%s` | %s | %s | %s | [%s](%s/%s.checks.png) |" % (
                p["name"], f["step"], f["check"], md_escape(f["label"])[:60], md_escape(f["detail"]),
                f["step_id"], p["name"], f["step_id"]))
    else:
        w("None.")
    w("")
    groups = unique_warnings(profiles)
    w("## Warnings (%d findings, %d distinct)" % (sum(len(v) for _, v in groups), len(groups)))
    w("")
    w("One row per check and node; the profiles where it showed, with the step and an example.")
    w("")
    if groups:
        w("| Check | Node | Detail (example) | Where | Example picture |")
        w("|---|---|---|---|---|")
        for (check, label), items in groups:
            where = {}
            for p, f in items:
                where.setdefault(p["name"], set()).add(f["step"])
            where_s = "; ".join("%s (%s)" % (n, ", ".join(sorted(s))[:80]) for n, s in where.items())
            p, f = items[0]
            w("| %s | %s | %s | %s | [%s](%s/%s.checks.png) |" % (
                check, md_escape(label)[:60] or "(no label)", md_escape(f["detail"]), md_escape(where_s)[:400],
                f["step_id"], p["name"], f["step_id"]))
    w("")
    w("## Steps per profile")
    w("")
    for p in profiles:
        w("### %s: %s" % (p["name"], p["verdict"]))
        w("")
        w("%s, %s, font %s, rotation %s. %s. Tour log: [%s/tour.log](%s/tour.log)." % (
            screen(p), dp_size(p), p["row"]["font"], p["row"]["rotation"], p["row"]["description"],
            p["name"], p["name"]))
        w("")
        w("| Step | Result | Time | Checks | Detail |")
        w("|---|---|---|---|---|")
        for s in p["steps"]:
            fs = p["by_step"].get(s["id"], [])
            nf = sum(f["severity"] == "fail" for f in fs)
            checks = ("%d fail, %d warn" % (nf, len(fs) - nf)) if fs else ""
            w("| [`%s`](%s/%s.png) | %s | %ss | %s | %s |" % (
                s["id"], p["name"], s["id"], s["status"], s["seconds"], checks, md_escape(s["detail"])[:200]))
        w("")
    open(os.path.join(mdir, "index.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")


def fmt_secs(s):
    try:
        s = int(float(s))
    except (TypeError, ValueError):
        return "?"
    return "%dm %02ds" % (s // 60, s % 60) if s >= 60 else "%ds" % s


CSS = """
:root { --bg:#f6f5f1; --panel:#ffffff; --ink:#1d1f1c; --muted:#5e645c; --line:#d9dbd4;
  --pass:#1f7a3f; --fail:#c62828; --warn:#c77700; --skip:#9aa096; --accent:#0d5c3d; }
@media (prefers-color-scheme: dark) { :root { --bg:#101411; --panel:#18201b; --ink:#e8ece6;
  --muted:#9aa59c; --line:#2c3a31; --pass:#4caf6e; --fail:#ef5350; --warn:#ffb74d; --skip:#5f6b62;
  --accent:#ffd54f; } }
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--ink); font:14px/1.45 system-ui, sans-serif; }
header, section { padding:16px 20px; }
h1 { margin:0 0 4px; font-size:22px; } h2 { font-size:17px; margin:24px 0 8px; }
.muted { color:var(--muted); }
.verdict { display:inline-block; padding:2px 10px; border-radius:12px; color:#fff; font-weight:600; }
.PASS { background:var(--pass); } .FAIL { background:var(--fail); }
table { border-collapse:collapse; background:var(--panel); }
th, td { border:1px solid var(--line); padding:6px 8px; text-align:left; vertical-align:top; }
th { font-weight:600; }
.scroll { overflow-x:auto; max-width:100%; }
.sheet th.prof, .sheet td.prof { position:sticky; left:0; background:var(--panel); z-index:1; min-width:150px; }
.sheet td.cell { padding:4px; text-align:center; min-width:120px; }
.sheet img { display:block; margin:0 auto; max-width:216px; max-height:384px; border:3px solid var(--pass); border-radius:4px; }
.sheet img.warn { border-color:var(--warn); } .sheet img.fail { border-color:var(--fail); }
.badge { display:inline-block; font-size:11px; padding:0 6px; border-radius:8px; margin-top:3px; color:#fff; }
.badge.fail { background:var(--fail); } .badge.warn { background:var(--warn); color:#222; }
.none { color:var(--skip); }
details { margin:8px 0; } summary { cursor:pointer; font-weight:600; }
code { font-size:12px; }
.small { font-size:12px; }
"""


def write_html(mdir, profiles, a, verdict, key_screens):
    h = []
    e = html.escape
    h.append("<!doctype html><html lang='en'><head><meta charset='utf-8'>"
             "<meta name='viewport' content='width=device-width, initial-scale=1'>"
             "<title>Device matrix</title><style>%s</style></head><body>" % CSS)
    h.append("<header><h1>Device matrix <span class='verdict %s'>%s</span></h1>" % (verdict, verdict))
    h.append("<div class='muted'>%s · %s build · %s steps · %d profiles · %s%s<br>Display after the run: %s</div>" % (
        e(datetime.datetime.now().strftime("%Y-%m-%d %H:%M")), e(a.variant), e(a.mode), len(profiles),
        fmt_secs(a.wall), " · <b>interrupted</b>" if a.interrupted else "", e(a.display_after or "?")))
    h.append("<p class='small muted'>Border: green = step passed, orange = layout warnings (boxed in the "
             "picture), red = the step or a layout check failed. Click a picture for the full screenshot. "
             "Warnings are heuristics; see docs/TESTING.md.</p></header>")
    # Profiles table
    h.append("<section><h2>Profiles</h2><div class='scroll'><table><tr><th>Profile</th><th>Screen</th>"
             "<th>dp</th><th>Font</th><th>Rot</th><th>Result</th><th>Steps</th><th>Check fails</th>"
             "<th>Warnings</th><th>Time</th></tr>")
    for p in profiles:
        r = p["row"]
        h.append("<tr><td><a href='#p-%s'><code>%s</code></a><div class='small muted'>%s</div></td><td>%s</td>"
                 "<td>%s</td><td>%s</td><td>%s</td><td><span class='verdict %s'>%s</span></td><td>%d/%d</td>"
                 "<td>%d</td><td>%d</td><td>%s</td></tr>" % (
                     e(p["name"]), e(p["name"]), e(r["description"]), e(screen(p)), e(dp_size(p)), e(r["font"]),
                     e(r["rotation"]), p["verdict"], p["verdict"], len(p["steps"]) - len(p["failed_steps"]),
                     len(p["steps"]), len(p["check_fails"]), len(p["findings"]) - len(p["check_fails"]),
                     fmt_secs(r["seconds"])))
    h.append("</table></div></section>")
    # Contact sheet
    h.append("<section><h2>Key screens</h2><div class='scroll'><table class='sheet'><tr><th class='prof'>Profile</th>")
    for k in key_screens:
        h.append("<th><code>%s</code></th>" % e(k))
    h.append("</tr>")
    for p in profiles:
        by_name = {s["name"]: s for s in p["steps"]}
        h.append("<tr><td class='prof'><a href='#p-%s'><code>%s</code></a><div class='small muted'>%s<br>%s · font %s</div>"
                 "<span class='verdict %s'>%s</span></td>" % (
                     e(p["name"]), e(p["name"]), e(screen(p)), e(dp_size(p)), e(p["row"]["font"]),
                     p["verdict"], p["verdict"]))
        for k in key_screens:
            s = by_name.get(k)
            if not s:
                h.append("<td class='cell none'>not run</td>")
                continue
            h.append("<td class='cell'>%s</td>" % cell(mdir, p, s))
        h.append("</tr>")
    h.append("</table></div></section>")
    # Problems
    fails = [(p, s) for p in profiles for s in p["failed_steps"]]
    cf = [(p, f) for p in profiles for f in p["check_fails"]]
    h.append("<section><h2>Failing steps (%d) and layout check failures (%d)</h2>" % (len(fails), len(cf)))
    if fails or cf:
        h.append("<div class='scroll'><table><tr><th>Profile</th><th>Step</th><th>What</th><th>Picture</th></tr>")
        for p, s in fails:
            h.append("<tr><td><code>%s</code></td><td><code>%s</code></td><td>step failed: %s</td><td>%s</td></tr>" % (
                e(p["name"]), e(s["id"]), e(s["detail"][:300]), cell(mdir, p, s)))
        for p, f in cf:
            s = next((x for x in p["steps"] if x["id"] == f["step_id"]), {"id": f["step_id"], "name": f["step"], "status": "PASS"})
            h.append("<tr><td><code>%s</code></td><td><code>%s</code></td><td>%s: %s %s</td><td>%s</td></tr>" % (
                e(p["name"]), e(f["step_id"]), e(f["check"]), e(f["label"][:60]), e(f["detail"]), cell(mdir, p, s)))
        h.append("</table></div>")
    else:
        h.append("<p>None.</p>")
    h.append("</section>")
    groups = unique_warnings(profiles)
    h.append("<section><h2>Warnings: %d distinct</h2><div class='scroll'><table><tr><th>Check</th><th>Node</th>"
             "<th>Detail (example)</th><th>Where</th><th>Example</th></tr>" % len(groups))
    for (check, label), items in groups:
        where = {}
        for p, f in items:
            where.setdefault(p["name"], set()).add(f["step"])
        p, f = items[0]
        s = next((x for x in p["steps"] if x["id"] == f["step_id"]), {"id": f["step_id"], "name": f["step"], "status": "PASS"})
        h.append("<tr><td>%s</td><td>%s</td><td>%s</td><td class='small'>%s</td><td>%s</td></tr>" % (
            e(check), e(label[:60]) or "<i>no label</i>", e(f["detail"]),
            "<br>".join("<code>%s</code>: %s" % (e(n), e(", ".join(sorted(st)))) for n, st in where.items()),
            cell(mdir, p, s, small=True)))
    h.append("</table></div></section>")
    # Per profile
    h.append("<section><h2>Every step, per profile</h2>")
    for p in profiles:
        h.append("<details id='p-%s'><summary>%s <span class='verdict %s'>%s</span> <span class='muted small'>%s, %s, font %s</span></summary>" % (
            e(p["name"]), e(p["name"]), p["verdict"], p["verdict"], e(screen(p)), e(dp_size(p)), e(p["row"]["font"])))
        h.append("<p class='small'><a href='%s/index.md'>tour report</a> · <a href='%s/tour.log'>tour.log</a> · "
                 "<a href='%s/checks.json'>checks.json</a></p><div class='scroll'><table><tr><th>Step</th><th>Result</th>"
                 "<th>Time</th><th>Checks</th><th>Picture</th></tr>" % (e(p["name"]), e(p["name"]), e(p["name"])))
        for s in p["steps"]:
            fs = p["by_step"].get(s["id"], [])
            items = "".join("<li class='%s'>%s: %s, %s</li>" % (f["severity"], e(f["check"]), e(f["label"][:50]), e(f["detail"]))
                            for f in fs[:12])
            more = "<li>... %d more</li>" % (len(fs) - 12) if len(fs) > 12 else ""
            h.append("<tr><td><code>%s</code><div class='small muted'>%s</div></td><td>%s</td><td>%ss</td>"
                     "<td class='small'><ul>%s%s</ul></td><td>%s</td></tr>" % (
                         e(s["id"]), e(s["detail"][:200]), e(s["status"]), e(s["seconds"]), items, more,
                         cell(mdir, p, s, small=True)))
        h.append("</table></div></details>")
    h.append("</section></body></html>")
    open(os.path.join(mdir, "index.html"), "w", encoding="utf-8").write("".join(h))


def cell(mdir, p, s, small=False):
    fs = p["by_step"].get(s["id"], [])
    nf = sum(f["severity"] == "fail" for f in fs)
    nw = len(fs) - nf
    t, full = thumb(mdir, p["name"], s["id"], bool(fs))
    if not t:
        return "<span class='none'>no picture</span>"
    cls = "fail" if s["status"] != "PASS" or nf else ("warn" if nw else "")
    badges = ""
    if s["status"] != "PASS":
        badges += "<span class='badge fail'>step failed</span> "
    if nf:
        badges += "<span class='badge fail'>%d fail</span> " % nf
    if nw:
        badges += "<span class='badge warn'>%d warn</span>" % nw
    style = " style='max-width:120px'" if small else ""
    return "<a href='%s' target='_blank'><img loading='lazy' class='%s' src='%s' alt='%s'%s></a>%s" % (
        html.escape(full), cls, html.escape(t), html.escape(s["id"]), style,
        ("<div>%s</div>" % badges) if badges else "")


def write_png_sheet(mdir, profiles, key_screens):
    """contact-sheet.png: the grid as one picture (for an agent's image viewer). Pillow only."""
    if Image is None or not profiles:
        return
    from PIL import ImageDraw, ImageFont
    try:
        font = ImageFont.load_default(size=15)
    except TypeError:   # Pillow < 10.1
        font = ImageFont.load_default()
    cw, ch, left, top, pad = 132, 236, 170, 46, 6
    colours = {"pass": (46, 125, 50), "warn": (230, 150, 0), "fail": (211, 47, 47)}
    sheet = Image.new("RGB", (left + cw * len(key_screens), top + ch * len(profiles)), (250, 250, 247))
    draw = ImageDraw.Draw(sheet)
    for j, k in enumerate(key_screens):
        draw.text((left + j * cw + pad, 8), k[:16], fill=(30, 30, 30), font=font)
        if len(k) > 16:
            draw.text((left + j * cw + pad, 25), k[16:32], fill=(30, 30, 30), font=font)
    for i, p in enumerate(profiles):
        y0 = top + i * ch
        draw.text((8, y0 + 8), p["name"], fill=(30, 30, 30), font=font)
        draw.text((8, y0 + 28), screen(p), fill=(90, 90, 90), font=font)
        draw.text((8, y0 + 48), "%s, font %s" % (dp_size(p), p["row"]["font"]), fill=(90, 90, 90), font=font)
        draw.text((8, y0 + 72), p["verdict"], fill=colours["pass" if p["verdict"] == "PASS" else "fail"], font=font)
        by_name = {s["name"]: s for s in p["steps"]}
        for j, k in enumerate(key_screens):
            s = by_name.get(k)
            x0 = left + j * cw
            if not s:
                draw.text((x0 + pad, y0 + ch // 2), "not run", fill=(150, 150, 150), font=font)
                continue
            fs = p["by_step"].get(s["id"], [])
            nf = sum(f["severity"] == "fail" for f in fs)
            state = "fail" if s["status"] != "PASS" or nf else ("warn" if fs else "pass")
            src = os.path.join(mdir, p["name"], s["id"] + (".checks.png" if fs else ".png"))
            if not os.path.exists(src):
                src = os.path.join(mdir, p["name"], s["id"] + ".png")
            try:
                im = Image.open(src).convert("RGB")
            except OSError:
                continue
            im.thumbnail((cw - 2 * pad - 6, ch - 2 * pad - 24))
            draw.rectangle((x0 + pad - 3, y0 + pad - 3, x0 + pad + im.size[0] + 2, y0 + pad + im.size[1] + 2),
                           fill=colours[state])
            sheet.paste(im, (x0 + pad, y0 + pad))
            label = "FAILED" if s["status"] != "PASS" else ("%d fail" % nf if nf else ("%d warn" % len(fs) if fs else ""))
            if label:
                draw.text((x0 + pad, y0 + pad + im.size[1] + 4), label, fill=colours[state], font=font)
    sheet.save(os.path.join(mdir, "contact-sheet.png"), optimize=True)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mdir")
    ap.add_argument("--key-screens", default="")
    ap.add_argument("--variant", default="debug")
    ap.add_argument("--mode", default="smoke")
    ap.add_argument("--wall", default="0")
    ap.add_argument("--interrupted", type=int, default=0)
    ap.add_argument("--display-after", default="")
    a = ap.parse_args(argv)
    profiles = load(a.mdir)
    verdict = "PASS" if profiles and all(p["verdict"] == "PASS" for p in profiles) and not a.interrupted else "FAIL"
    ran = {s["name"] for p in profiles for s in p["steps"]}
    # A key screen no profile ran (a step renamed or not in this run) gets no column
    keys = [k for k in a.key_screens.split() if k in ran] or sorted(ran)
    write_md(a.mdir, profiles, a, verdict)
    write_html(a.mdir, profiles, a, verdict, keys)
    write_png_sheet(a.mdir, profiles, keys)
    with open(os.path.join(a.mdir, "summary.env"), "w", encoding="utf-8") as fh:
        fh.write("verdict=%s\nprofiles=%d\nfailed_profiles=%d\n" % (
            verdict, len(profiles), sum(p["verdict"] != "PASS" for p in profiles)))
    print("[matrix] report written: %s verdict %s" % (os.path.join(a.mdir, "index.md"), verdict))


if __name__ == "__main__":
    sys.exit(main())
