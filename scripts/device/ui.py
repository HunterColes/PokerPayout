#!/usr/bin/env python3
"""Headless UI driver for the test emulator (stdlib only; wraps adb + uiautomator).

Every command dumps the live UI tree with `uiautomator dump`, so it works for Compose
(text and contentDescription are exposed; Modifier.testTag shows up as resource-id
only if the app sets `testTagsAsResourceId`).

SELECTORS (several tokens are AND-ed):
    Foo            text or content-desc == "Foo"; falls back to "contains Foo"
    text=Foo       exact text            text~=Foo   text contains (case-insensitive)
    desc=Foo       exact content-desc    desc~=Foo   content-desc contains
    id=foo         resource-id (full, or the part after "/")
    class=EditText class name suffix     re=^\\d+%$  regex on text or content-desc
    has=A|B        smallest node whose subtree contains texts/descs A and B (exact)
    clickable | scrollable | focused | checked | enabled   boolean flags
    in-scroll      inside a scroller (a ScrollView or list, even one whose content fits): a
                   screen's own "Payouts", not the tab's

COMMANDS
    dump [--out F]                print a compact node list (and save raw XML to F)
    texts                         print visible text/content-desc strings, top to bottom
    find SEL.. [--from F]         print matching nodes (exit 1 if none); --from: in a saved dump
    tap SEL.. [--index N]         wait for a match, tap its centre
        [--scroll-in SEL..]       ...scrolling inside that container until it appears
    long-press SEL..
    tap-xy X Y | swipe X1 Y1 X2 Y2 [MS]
    slide SEL.. --frac F          tap a slider (e.g. class=SeekBar) at fraction F (0..1) of its track
    type TEXT                     type into the focused field
    clear                         clear the focused field
    set-text SEL.. --value V      tap a field, clear it, type V, hide nothing
    scroll down|up [--times N] [--in SEL..]
    scroll-to SEL.. [--dir down|up] [--max N] [--in SEL..]
    wait SEL.. | wait-gone SEL..  [--timeout S]
    assert SEL..                  like wait, but prints what IS on screen on failure
    assert-text T1 [T2 ..]        every T must be visible (bare-token semantics)
    back | home | enter | key KEYCODE..
    launch [--clear]              (re)start the app's launcher activity, optionally wiping data
    top                           print the resumed activity
    page [--out F]                one dump of the whole page: the main scroller dragged to its end,
                                  the screens merged in page coordinates, then dragged back

Common options: --timeout S (default 10), --serial SERIAL (default $ANDROID_SERIAL).
Exit status: 0 ok, 1 not found / assertion failed, 2 usage or adb error.

SCROLL MODE (PP_UI_SCROLL=1; the device matrix sets it): on a small screen what a step expects can
be below the fold. Then tap, set-text, wait, assert and assert-text, after 2 s without a match,
drag the page's main scroller (the largest scrollable node) down to its end and up to its
top looking for it. assert-text counts a text seen anywhere on the way and puts the page back where
it was; the others stop where the match is. The search up goes past where it started, so a page
a step left part-way down is searched to its top too. Drags rest before lifting, so nothing flings
and a drag back returns exactly. find (a probe) and wait-gone never scroll; tap --scroll-in
searches both ways; scroll-to ignores a small --max, goes on to that end of the page, then the
other way. page starts from the page's top. Without PP_UI_SCROLL nothing scrolls by itself.
"""

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APP_ID = "com.huntercoles.pokerpayout"
SDK = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
SERIAL = os.environ.get("ANDROID_SERIAL") or "emulator-%s" % os.environ.get("PP_EMU_PORT", "5580")
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
FLAGS = ("clickable", "scrollable", "focused", "checked", "enabled", "selected", "focusable", "long-clickable")


class UiError(Exception):
    pass


# ----------------------------------------------------------------------------- adb
def adb(*args, check=True, timeout=60, binary=False):
    cmd = [ADB, "-s", SERIAL, *args]
    p = subprocess.run(cmd, capture_output=True, timeout=timeout)
    if check and p.returncode != 0:
        raise UiError("adb %s failed: %s" % (" ".join(args), (p.stderr or p.stdout).decode(errors="replace").strip()))
    return p.stdout if binary else p.stdout.decode("utf-8", errors="replace")


def shell(cmd, **kw):
    return adb("shell", cmd, **kw)


# ----------------------------------------------------------------------------- tree
class Node:
    __slots__ = ("attrs", "parent", "children", "bounds", "depth")

    def __init__(self, attrs, parent, depth):
        self.attrs, self.parent, self.children, self.depth = attrs, parent, [], depth
        m = BOUNDS_RE.match(attrs.get("bounds", ""))
        self.bounds = tuple(int(v) for v in m.groups()) if m else (0, 0, 0, 0)

    text = property(lambda s: s.attrs.get("text", ""))
    desc = property(lambda s: s.attrs.get("content-desc", ""))
    rid = property(lambda s: s.attrs.get("resource-id", ""))
    cls = property(lambda s: s.attrs.get("class", ""))

    def flag(self, name):
        return self.attrs.get(name) == "true"

    @property
    def center(self):
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def area(self):
        x1, y1, x2, y2 = self.bounds
        return max(0, x2 - x1) * max(0, y2 - y1)

    def labels(self):
        return [v for v in (self.text, self.desc) if v]

    def subtree(self):
        yield self
        for c in self.children:
            yield from c.subtree()

    def describe(self):
        bits = []
        if self.text:
            bits.append("text=%r" % self.text)
        if self.desc:
            bits.append("desc=%r" % self.desc)
        if self.rid:
            bits.append("id=%s" % self.rid)
        bits.append(self.cls.rsplit(".", 1)[-1] or "?")
        flags = [f for f in ("clickable", "scrollable", "checked", "focused", "selected") if self.flag(f)]
        if self.attrs.get("enabled") == "false":
            flags.append("DISABLED")
        if flags:
            bits.append("[" + ",".join(flags) + "]")
        bits.append("@%d,%d" % self.center)
        return " ".join(bits)


def kill_uiautomator():
    # A hung `uiautomator dump` keeps the UiAutomation connection registered and makes
    # every later dump fail, so clear it before retrying.
    try:
        adb("shell", "pkill -f com.android.commands.uiautomator || true", check=False, timeout=15)
    except subprocess.TimeoutExpired:
        pass


def hold_rotation():
    """uiautomator re-freezes the rotation at the display's current one when it disconnects, so under
    a portrait-only screen every dump turned a user rotation of 90 back to 0. With
    PP_UI_HOLD_ROTATION=N (steps-matrix.sh's rotate_to, a rotated matrix profile) put N back."""
    want = os.environ.get("PP_UI_HOLD_ROTATION")
    if want and shell("settings get system user_rotation", check=False).strip() != want:
        shell("settings put system accelerometer_rotation 0; settings put system user_rotation %s" % want,
              check=False)


def dump_xml(retries=4):
    last = ""
    for attempt in range(retries):
        t0 = time.time()
        # Odd attempts fall back to dumping into a file, since /dev/tty streaming is flaky on some images.
        # The old file goes first: `uiautomator dump` can fail and still exit 0, and cat would then
        # hand back an old screen as the current one (a whole profile once ran blind on one).
        cmd = ("uiautomator dump /dev/tty" if attempt % 2 == 0 else
               "rm -f /sdcard/pp_window_dump.xml; uiautomator dump /sdcard/pp_window_dump.xml >/dev/null"
               " && cat /sdcard/pp_window_dump.xml")
        try:
            out = adb("exec-out", cmd, check=False,
                      timeout=float(os.environ.get("PP_UI_DUMP_TIMEOUT", "30")))
        except subprocess.TimeoutExpired:
            if os.environ.get("PP_UI_TRACE"):
                sys.stderr.write("[ui] dump timed out after %.0fs; retrying\n" % (time.time() - t0))
            kill_uiautomator()
            last = "timed out"
            continue
        hold_rotation()
        end = out.rfind("</hierarchy>")
        if os.environ.get("PP_UI_TRACE"):
            why = "" if end != -1 else " (failed: %s)" % (" ".join(out.split())[:160] or "no output")
            sys.stderr.write("[ui] dump %.1fs%s\n" % (time.time() - t0, why))
        if end != -1:
            return out[out.find("<?xml") if "<?xml" in out else 0:end + len("</hierarchy>")]
        last = out.strip()
        # Whatever failed, a uiautomator still registered would fail every dump after it
        kill_uiautomator()
        if "null root node" in last:
            # No window to read: a screen that went off or locked. Wake it, as a user would
            shell("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard", check=False)
        time.sleep(0.5 + attempt * 0.5)
    raise UiError("uiautomator dump failed: %s" % (last[-300:] or "no output"))


def parse(xml_text):
    root = ET.fromstring(xml_text)
    nodes = []

    def walk(el, parent, depth):
        n = Node(dict(el.attrib), parent, depth)
        nodes.append(n)
        if parent:
            parent.children.append(n)
        for c in el:
            if c.tag == "node":
                walk(c, n, depth + 1)

    for top in root:
        if top.tag == "node":
            walk(top, None, 0)
    return nodes


APP_LABEL = "Poker Payout"
FOREIGN_ANR_RE = re.compile(r"^(.+) isn['’]t responding$")


def dismiss_foreign_anr(nodes):
    """Tap "Wait" on another app's "X isn't responding" dialog; True if one was dismissed.

    On a loaded host the emulator's own apps (usually Pixel Launcher, right after a quick boot)
    can ANR, and the system dialog then covers the app under test. That is not the app's fault, so
    wait it out. An ANR of the app under test is left on screen: tour.sh fails the step on it.
    """
    for n in nodes:
        m = FOREIGN_ANR_RE.match(n.text)
        if m and m.group(1) != APP_LABEL:
            wait = next((w for w in nodes if w.text == "Wait" and w.area > 0), None)
            if wait is None:
                return False
            x, y = wait.center
            shell("input tap %d %d" % (x, y))
            sys.stderr.write("[ui] dismissed system dialog: %r (tapped Wait)\n" % n.text)
            time.sleep(1)
            return True
    return False


def snapshot():
    xml_text = dump_xml()
    for _ in range(3):
        if not dismiss_foreign_anr(parse(xml_text)):
            break
        xml_text = dump_xml()
    last = os.environ.get("PP_UI_LAST_XML")  # tour.sh reuses the final dump of a step
    if last:
        with open(last, "w", encoding="utf-8") as f:
            f.write(xml_text)
    return xml_text, parse(xml_text)


# ----------------------------------------------------------------------------- selectors
def _scroller(n):
    # Compose marks a scroller scrollable only while its content overflows; its class stays.
    return n.flag("scrollable") or n.cls.endswith(("ScrollView", "ListView", "RecyclerView", "GridView"))


def _ancestors(n):
    p = n.parent
    while p is not None:
        yield p
        p = p.parent


def compile_selector(tokens):
    preds = []
    loose = []  # bare tokens: exact text/desc, else contains
    for tok in tokens:
        if tok in FLAGS:
            preds.append(lambda n, f=tok: n.flag(f))
            continue
        if tok == "in-scroll":   # inside a scrolling container: a screen's own text, not a tab's
            preds.append(lambda n: any(_scroller(p) for p in _ancestors(n)))
            continue
        m = re.match(r"^(text|desc|id|class|re|has)(~?=)(.*)$", tok, re.S)
        if not m:
            loose.append(tok)
            continue
        key, op, val = m.groups()
        if key == "text":
            preds.append((lambda n, v=val: n.text == v) if op == "=" else (lambda n, v=val.lower(): v in n.text.lower()))
        elif key == "desc":
            preds.append((lambda n, v=val: n.desc == v) if op == "=" else (lambda n, v=val.lower(): v in n.desc.lower()))
        elif key == "id":
            preds.append(lambda n, v=val, c=(op == "~="): n.rid == v or n.rid.endswith("/" + v) or (c and v in n.rid))
        elif key == "class":
            preds.append(lambda n, v=val: n.cls == v or n.cls.endswith("." + v))
        elif key == "re":
            rx = re.compile(val)
            preds.append(lambda n, rx=rx: any(rx.search(s) for s in n.labels()))
        elif key == "has":
            parts = [p for p in val.split("|") if p]
            preds.append(lambda n, parts=parts: all(any(p in d.labels() for d in n.subtree()) for p in parts))
            preds.append("__smallest__")
    smallest = "__smallest__" in preds
    preds = [p for p in preds if p != "__smallest__"]

    def match(nodes):
        cands = [n for n in nodes if n.area > 0 and all(p(n) for p in preds)]
        for tok in loose:
            exact = [n for n in cands if tok in (n.text, n.desc)]
            if exact:
                cands = exact
            else:
                low = tok.lower()
                cands = [n for n in cands if low in n.text.lower() or low in n.desc.lower()]
        if smallest:
            cands.sort(key=lambda n: n.area)
            cands = cands[:1] if cands else []
        else:
            cands.sort(key=lambda n: (n.bounds[1], n.bounds[0]))
        return cands

    return match


def visible_texts(nodes):
    seen = []
    for n in sorted(nodes, key=lambda n: (n.bounds[1], n.bounds[0])):
        for s in n.labels():
            s = s.replace("\n", " ")
            if s not in seen:
                seen.append(s)
    return seen


def find(tokens, timeout, index=0, want=True, scroll=True):
    """Poll until the selector matches (want=True) or stops matching (want=False)."""
    matcher = compile_selector(tokens)
    deadline = time.time() + timeout
    scroll_at = time.time() + min(timeout, SCROLL_AFTER) if SCROLL_MODE and want and scroll else None
    nodes = []
    while True:
        _, nodes = snapshot()
        found = matcher(nodes)
        if want and len(found) > index:
            return found[index], found, nodes
        if not want and not found:
            return None, [], nodes
        if scroll_at is not None and time.time() >= scroll_at:
            scroll_at = None
            hit = drag_search(lambda ns: len(matcher(ns)) > index, nodes)
            if hit is not None:
                found = matcher(hit)
                return found[index], found, hit
            continue
        if time.time() >= deadline:
            return None, found, nodes
        time.sleep(0.3)


# ----------------------------------------------------------------------------- scroll mode
SCROLL_MODE = os.environ.get("PP_UI_SCROLL") == "1"
SCROLL_AFTER = 2.0   # seconds of plain polling before scroll mode drags the page
MAX_DRAGS = 12       # per direction
_density = []


def touch_slop():
    """The system's touch slop in px (8 dp): a drag moves the content this much less than the finger."""
    if not _density:
        m = re.findall(r"density: (\d+)", shell("wm density", check=False))
        _density.append(int(m[-1]) if m else 420)
    return int(round(8 * _density[0] / 160.0))


def main_container(nodes):
    """The page's main scroller: the app's largest scrollable node. Never another app's: dragging
    the launcher, the lock screen or the shade opens the shade or the app drawer (a drag that
    reveals what is above is a swipe down), so with the app out of sight scroll mode drags nothing."""
    cands = [n for n in nodes if n.flag("scrollable") and n.area > 0 and n.attrs.get("package", APP_ID) == APP_ID]
    return max(cands, key=lambda n: n.area) if cands else None


def drag(bounds, direction, amount=None):
    """Drag the content of `bounds` so it moves `amount` px (default 65 % of the box) up ("down":
    reveal what is below) or down ("up"). The finger rests before lifting, so nothing flings and
    the same drag the other way comes back exactly. Returns the distance the content should move."""
    x1, y1, x2, y2 = bounds
    h, slop = y2 - y1, touch_slop()
    move = int(h * 0.65) if amount is None else max(1, min(int(amount), int(h * 0.7)))
    dist = move + slop
    x = x1 + max(8, (x2 - x1) * 6 // 100)     # near the left edge: off sliders and centred buttons
    mid = (y1 + y2) // 2
    y_from, y_to = (mid + dist // 2, mid - dist // 2) if direction == "down" else (mid - dist // 2, mid + dist // 2)
    moves = "; ".join("input motionevent MOVE %d %d" % (x, y_from + (y_to - y_from) * k // 4) for k in (1, 2, 3, 4))
    shell("input motionevent DOWN %d %d; %s; sleep 0.25; input motionevent UP %d %d" % (x, y_from, moves, x, y_to))
    return move if direction == "down" else -move


def _inside(nodes, cont, labelled=True):
    """The (labelled) nodes in the subtree of the scroller that matches `cont` in this dump."""
    c = main_container(nodes)
    if c is None or (cont is not None and abs(c.bounds[1] - cont.bounds[1]) > 4):
        return [], c
    return [n for n in c.subtree() if n is not c and (n.labels() or not labelled) and n.area > 0], c


def content_shift(before, after):
    """How far the scroller's content moved up between two dumps (px): the median move of the nodes
    that show once in each, whole, with the same label, x and size (labelled ones first; on a
    screen with no such text, any node by its class and geometry). None if nothing matches."""
    _, cont = _inside(before, None)
    if cont is None:
        return None

    def whole(n):
        return n.bounds[1] > cont.bounds[1] + 1 and n.bounds[3] < cont.bounds[3] - 1

    def deltas_by(key, labelled):
        b_nodes, _ = _inside(before, None, labelled)
        a_nodes, _ = _inside(after, cont, labelled)
        b_map = {}
        for n in b_nodes:
            if whole(n):
                b_map.setdefault(key(n), []).append(n.bounds[1])
        out = []
        for n in a_nodes:
            ys = b_map.get(key(n))
            if whole(n) and ys and len(ys) == 1:
                out.append(ys[0] - n.bounds[1])
        return out, b_nodes, a_nodes

    def size(n):
        return (n.bounds[0], n.bounds[2] - n.bounds[0], n.bounds[3] - n.bounds[1])
    deltas, b_nodes, a_nodes = deltas_by(lambda n: (n.text, n.desc) + size(n), True)
    if not deltas:
        deltas, _, _ = deltas_by(lambda n: (n.cls,) + size(n), False)
    if not deltas:
        # Nothing to measure against: unmoved if the scroller shows the same texts as before
        same = [n.labels() for n in b_nodes] == [n.labels() for n in a_nodes] and b_nodes
        return 0 if same else None
    deltas.sort()
    return deltas[len(deltas) // 2]


def drag_search(pred, nodes, visit=None, restore=False):
    """Drag the main scroller down to its end, then up to its top, until pred(nodes). Returns the
    nodes where it held (the page left there) or None. With restore, the page goes back to where it
    was in any case (and the nodes returned are the restored screen's). visit(nodes) sees every screen."""
    offset = 0   # how far the content moved up from where it started
    hit = None
    downs = 0
    for direction in ("down", "up"):
        # Up goes back over the drags down first, then on: a page left part-way down (a step that
        # scrolled to what it needed) has more above where the search started
        for _ in range(MAX_DRAGS if direction == "down" else downs + MAX_DRAGS):
            cont = main_container(nodes)
            if cont is None:
                break
            before = nodes
            expected = drag(cont.bounds, direction)
            downs += direction == "down"
            _, nodes = snapshot()
            shift = content_shift(before, nodes)
            offset += expected if shift is None else shift
            if visit:
                visit(nodes)
            if pred(nodes):
                hit = nodes
                break
            if shift == 0:
                break   # that end of the page
        if hit is not None:
            break
    if os.environ.get("PP_UI_TRACE"):
        sys.stderr.write("[ui] scroll mode: dragged the page %d px, %s\n" % (offset, "found" if hit else "not found"))
    if restore and offset:
        nodes = scroll_back(nodes, offset)
        return nodes if hit is not None else None
    return hit


def scroll_back(nodes, offset):
    """Drag the content back down by `offset` px (negative: up), measuring as it goes. (As many
    drags as a search can make: it can end far from where it started.)"""
    for _ in range(3 * MAX_DRAGS):
        if abs(offset) <= 12:
            break
        cont = main_container(nodes)
        if cont is None:
            break
        before = nodes
        expected = drag(cont.bounds, "up" if offset > 0 else "down", abs(offset))
        _, nodes = snapshot()
        shift = content_shift(before, nodes)
        moved = -expected if shift is None else -shift   # positive: content came back down
        if shift == 0:
            break
        offset -= moved
    return nodes


def fail(msg, nodes=None):
    sys.stderr.write("[ui] FAIL: %s\n" % msg)
    if nodes is not None:
        texts = visible_texts(nodes)
        sys.stderr.write("[ui] on screen: %s\n" % " | ".join(texts[:60]))
    sys.exit(1)


# ----------------------------------------------------------------------------- input
def input_text_escape(s):
    out = []
    for ch in s:
        if ch == " ":
            out.append("%s")
        elif ch in "()<>|;&*\\~\"'`$?#![]{}":
            out.append("\\" + ch)
        else:
            out.append(ch)
    return "".join(out)


def type_text(s):
    # `input text` chokes on very long strings; chunk it.
    for i in range(0, len(s), 40):
        shell("input text %s" % input_text_escape(s[i:i + 40]))


def clear_focused(nodes=None):
    if nodes is None:
        _, nodes = snapshot()
    focused = [n for n in nodes if n.flag("focused") and "EditText" in n.cls]
    count = (len(focused[0].text) if focused else 0) + 4
    count = max(count, 8)
    shell(clear_keys(count))


def clear_keys(count):
    # Compose places the cursor where the tap landed a frame or two after the tap, so a
    # MOVE_END sent right behind it can be overtaken and DEL alone leaves the text after
    # the cursor ("Player 1" became "Alice1"). FORWARD_DEL removes that tail too.
    keys = ["KEYCODE_MOVE_END"] + ["KEYCODE_DEL"] * count + ["KEYCODE_FORWARD_DEL"] * count
    return "input keyevent " + " ".join(keys)


# ----------------------------------------------------------------------------- commands
def cmd_dump(a):
    xml_text, nodes = snapshot()
    if a.out:
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        with open(a.out, "w", encoding="utf-8") as f:
            f.write(xml_text)
    for n in nodes:
        if n.text or n.desc or n.rid or n.flag("clickable") or n.flag("scrollable") or "EditText" in n.cls:
            if n.rid.startswith("android:id/") and not (n.text or n.desc):
                continue
            print("  " * min(n.depth, 12) + n.describe())


def cmd_texts(a):
    _, nodes = snapshot()
    print("\n".join(visible_texts(nodes)))


def cmd_find(a):
    if a.dump_from:   # a saved dump (a page from `page`), not the screen
        with open(a.dump_from, encoding="utf-8") as f:
            nodes = parse(f.read())
        found = compile_selector(a.selector)(nodes)
    else:             # a probe: never scrolls, even in scroll mode
        node, found, nodes = find(a.selector, a.timeout, a.index, scroll=False)
    if not found:
        fail("no match for %s" % a.selector, nodes)
    for n in found:
        print(n.describe())


def _tap_node(a, long=False):
    if getattr(a, "scroll_in", None) and not SCROLL_MODE:   # scroll mode searches both ways
        _, nodes = scroll_until(a.selector, a.scroll_in, "down", 10)
        found = compile_selector(a.selector)(nodes)
        node = found[a.index] if len(found) > a.index else None
    else:
        node, found, nodes = find(a.selector, a.timeout, a.index)
    if node is None:
        fail("no match for %s (index %d, %d matches)" % (a.selector, a.index, len(found)), nodes)
    x, y = node.center
    if long:
        shell("input swipe %d %d %d %d 800" % (x, y, x, y))
    else:
        shell("input tap %d %d" % (x, y))
    print("[ui] %s %s -> (%d,%d)" % ("long-press" if long else "tap", " ".join(a.selector), x, y))
    if a.settle:
        time.sleep(a.settle)


def cmd_tap(a):
    _tap_node(a)


def cmd_long_press(a):
    _tap_node(a, long=True)


def cmd_tap_xy(a):
    shell("input tap %d %d" % (a.x, a.y))
    print("[ui] tap (%d,%d)" % (a.x, a.y))


def cmd_slide(a):
    node, found, nodes = find(a.selector, a.timeout, a.index)
    if node is None:
        fail("no slider matching %s" % a.selector, nodes)
    x1, y1, x2, y2 = node.bounds
    # Compose/M3 sliders inset the track by roughly half the touch-target height.
    inset = min((y2 - y1) // 2, (x2 - x1) // 8)
    x = int(x1 + inset + max(0.0, min(1.0, a.frac)) * (x2 - x1 - 2 * inset))
    y = (y1 + y2) // 2
    shell("input tap %d %d" % (x, y))
    print("[ui] slide %s to %.2f -> (%d,%d)" % (" ".join(a.selector), a.frac, x, y))
    if a.settle:
        time.sleep(a.settle)


def cmd_swipe(a):
    shell("input swipe %d %d %d %d %d" % (a.x1, a.y1, a.x2, a.y2, a.ms))


def cmd_type(a):
    type_text(a.text)
    print("[ui] typed %r" % a.text)


def cmd_clear(a):
    clear_focused()


def cmd_set_text(a):
    node, found, nodes = find(a.selector, a.timeout, a.index)
    if node is None:
        fail("no field matching %s" % a.selector, nodes)
    # If the match is a label inside a text field, target the enclosing EditText.
    target = node
    p = node
    while p is not None and "EditText" not in p.cls:
        p = p.parent
    if p is not None:
        target = p
    x, y = target.center
    shell("input tap %d %d" % (x, y))
    time.sleep(0.3)   # let focus and the tap's cursor placement land before the keys
    # Clear using the length we already know (saves a second ~2s dump).
    count = max(len(target.text) + 4, 8) if "EditText" in target.cls else 32
    shell(clear_keys(count))
    if a.value:
        type_text(a.value)
    print("[ui] set %s = %r" % (" ".join(a.selector), a.value))


def screen_size():
    out = shell("wm size")
    m = re.findall(r"(\d+)x(\d+)", out)
    w, h = (int(v) for v in m[-1])
    return w, h


def _swipe_dir(direction, area=None):
    if area is None:
        w, h = screen_size()
        area = (0, int(h * 0.15), w, int(h * 0.85))
    x1, y1, x2, y2 = area
    cx = (x1 + x2) // 2
    top, bottom = y1 + (y2 - y1) // 4, y2 - (y2 - y1) // 4
    if direction == "down":   # reveal content further down
        shell("input swipe %d %d %d %d 300" % (cx, bottom, cx, top))
    else:
        shell("input swipe %d %d %d %d 300" % (cx, top, cx, bottom))


def cmd_scroll(a):
    area = None
    if a.within:
        node, _, nodes = find(a.within, a.timeout)
        if node is None:
            fail("no scroll container matching %s" % a.within, nodes)
        area = node.bounds
    for _ in range(a.times):
        _swipe_dir(a.direction, area)
        time.sleep(0.3)
    print("[ui] scrolled %s x%d" % (a.direction, a.times))


def scroll_until(selector, within, direction, max_swipes):
    """Swipe (inside the `within` container if given) until `selector` matches. In scroll mode it
    goes on to that end of the page however long it is, then the other way: on a small screen or
    with large text a page is longer than a step's --max allows for, and what it looks for can be
    above where the step left the page."""
    matcher = compile_selector(selector)
    container = compile_selector(within) if within else None
    directions = [direction]
    if SCROLL_MODE:
        max_swipes = max(max_swipes, 3 * MAX_DRAGS)
        directions.append("up" if direction == "down" else "down")
    swipes = 0
    for d in directions:
        prev = None
        for _ in range(max_swipes + 1):
            _, nodes = snapshot()
            if matcher(nodes):
                return swipes, nodes
            sig = tuple(visible_texts(nodes))
            if sig == prev:
                break  # reached the end; nothing moved
            prev = sig
            area = None
            if container:
                boxes = container(nodes)
                if not boxes:
                    fail("%s isn't on screen and nothing scrolls (no %s)" % (" ".join(selector), within), nodes)
                area = boxes[0].bounds
            _swipe_dir(d, area)
            swipes += 1
    fail("scrolled %s but never found %s" % (" then ".join(directions), selector), nodes)


def cmd_scroll_to(a):
    n, _ = scroll_until(a.selector, a.within, a.dir, a.max)
    print("[ui] found %s after %d scroll(s)" % (" ".join(a.selector), n))


def cmd_wait(a):
    node, _, nodes = find(a.selector, a.timeout, a.index)
    if node is None:
        fail("timed out after %.0fs waiting for %s" % (a.timeout, a.selector), nodes)
    print("[ui] visible: %s" % node.describe())


def cmd_wait_gone(a):
    _, found, nodes = find(a.selector, a.timeout, want=False)
    if found:
        fail("still visible after %.0fs: %s" % (a.timeout, a.selector), nodes)
    print("[ui] gone: %s" % " ".join(a.selector))


def cmd_assert_text(a):
    deadline = time.time() + a.timeout
    scroll_at = time.time() + min(a.timeout, SCROLL_AFTER) if SCROLL_MODE else None
    matchers = {t: compile_selector([t]) for t in a.texts}
    seen = set()
    while True:
        _, nodes = snapshot()
        missing = [t for t in a.texts if t not in seen and not matchers[t](nodes)]
        if not missing or time.time() >= deadline and scroll_at is None:
            break
        if scroll_at is not None and time.time() >= scroll_at:
            # Scroll mode: a text seen anywhere on the page counts; then the page goes back.
            scroll_at = None
            seen = {t for t in a.texts if t not in missing}

            def visit(ns):
                seen.update(t for t in a.texts if matchers[t](ns))
            back = drag_search(lambda ns: len(seen) == len(a.texts), nodes, visit=visit, restore=True)
            if len(seen) == len(a.texts):
                _, nodes = snapshot() if back is None else (None, back)
                missing = []
                break
            continue
        time.sleep(0.3)
    if missing:
        fail("missing text: %s" % missing, nodes)
    print("[ui] ok: %s%s" % (", ".join(a.texts), " (scrolled)" if seen else ""))


def cmd_page(a):
    """One dump of the whole page: drag the main scroller to its top, then to its end, place each
    screen's nodes in the page's coordinates (the top screen's), and drag back to where it was.
    Only labelled nodes are kept; the ones outside the scroller (bars, headers) come from the top
    screen. (It starts at the top because a step often leaves the page part-way down, having
    scrolled to what it checks: on a small screen, part of a list is then above it.)"""
    xml_text, nodes = snapshot()
    root_el = ET.fromstring(xml_text)
    cont = main_container(nodes)
    lead, ups = 0, 0   # how far the content moved up while going to the top (so 0 or less)
    while cont is not None and ups < MAX_DRAGS * 2:
        before = nodes
        expected = drag(cont.bounds, "up")
        ups += 1
        _, nodes = snapshot()
        shift = content_shift(before, nodes)
        if shift is None:
            lead += expected    # it moved, but by how much can't be told: merge from here
            break
        if shift >= 0:
            break               # the top of the page
        lead += shift
    cont = main_container(nodes)
    entries = [[dict(n.attrs), list(n.bounds)] for n in nodes if n.labels() and n.area > 0]
    offset, drags = 0, 0
    while cont is not None and drags < MAX_DRAGS * 2:
        before = nodes
        expected = drag(cont.bounds, "down")
        drags += 1
        _, nodes = snapshot()
        shift = content_shift(before, nodes)
        if shift is None:
            offset += expected  # it moved, but this screen can't be placed: stop, don't guess
            break
        if shift <= 0:
            break               # the end of the page
        offset += shift
        inner, c = _inside(nodes, cont)
        for n in inner:
            x1, y1, x2, y2 = n.bounds
            if y1 <= c.bounds[1] + 1:
                continue        # cut by the scroller's top: seen whole on an earlier screen
            box = [x1, y1 + offset, x2, y2 + offset]
            label = (n.text, n.desc)
            same = next((e for e in entries if (e[0].get("text", ""), e[0].get("content-desc", "")) == label
                         and abs(e[1][0] - x1) <= 3 and abs(e[1][1] - box[1]) <= max(6, (y2 - y1) // 3)), None)
            if same is None:
                entries.append([dict(n.attrs), box])
            elif box[3] - box[1] > same[1][3] - same[1][1]:
                same[1] = box   # a node cut by the bottom edge before, whole now
    if lead + offset:
        scroll_back(nodes, lead + offset)
    out = ET.Element("hierarchy", {"rotation": root_el.get("rotation", "0"), "page-offset": str(offset)})
    top = nodes[0] if nodes else None
    page = ET.SubElement(out, "node", dict(top.attrs) if top else {})
    w = max([e[1][2] for e in entries] + [top.bounds[2] if top else 0])
    h = max([e[1][3] for e in entries] + [top.bounds[3] if top else 0])
    page.set("bounds", "[0,0][%d,%d]" % (w, h))
    for attrs, b in entries:
        attrs["bounds"] = "[%d,%d][%d,%d]" % tuple(b)
        ET.SubElement(page, "node", attrs)
    data = ET.tostring(out, encoding="unicode")
    if a.out:
        with open(a.out, "w", encoding="utf-8") as f:
            f.write(data)
    print("[ui] page: %d labelled nodes over %d px (%d drags)" % (len(entries), h, drags))


def cmd_key(a):
    shell("input keyevent " + " ".join(a.keys))


def cmd_launch(a):
    # A screen that is off or locked, or a shade left open, keeps the app from the front: clear
    # them first
    shell("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; cmd statusbar collapse", check=False)
    if a.clear:
        shell("pm clear %s" % APP_ID)
    else:
        shell("am force-stop %s" % APP_ID)
    out = shell("monkey -p %s -c android.intent.category.LAUNCHER 1" % APP_ID, check=False)
    if "No activities found" in out or "monkey aborted" in out:
        raise UiError("could not launch %s: %s" % (APP_ID, out.strip()))
    deadline = time.time() + a.timeout
    while time.time() < deadline:
        if APP_ID in top_activity():
            print("[ui] launched %s" % APP_ID)
            return
        time.sleep(0.3)
    fail("%s did not come to the foreground; in front: %s; screen: %s" % (
        APP_ID, top_activity() or "nothing", shell("dumpsys power | grep -m1 -E 'mWakefulness='", check=False).strip()))


def top_activity():
    out = shell("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'", check=False)
    return out.strip().splitlines()[0].strip() if out.strip() else ""


def cmd_top(a):
    print(top_activity())


def main(argv=None):
    global SERIAL
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--timeout", type=float, default=10.0)
    common.add_argument("--index", type=int, default=0)
    common.add_argument("--settle", type=float, default=0.0,
                        help="seconds to sleep after a tap (dumps already wait for UI idle)")
    common.add_argument("--serial", default=None)

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    def add(name, fn, *args, **kw):
        p = sub.add_parser(name, parents=[common], **kw)
        p.set_defaults(fn=fn)
        return p

    add("dump", cmd_dump).add_argument("--out")
    add("texts", cmd_texts)
    p = add("find", cmd_find); p.add_argument("selector", nargs="+")
    p.add_argument("--from", dest="dump_from", help="search this saved dump instead of the screen")
    p = add("tap", cmd_tap); p.add_argument("selector", nargs="+")
    p.add_argument("--scroll-in", nargs="+", help="scroll this container until the target appears")
    add("long-press", cmd_long_press).add_argument("selector", nargs="+")
    p = add("tap-xy", cmd_tap_xy); p.add_argument("x", type=int); p.add_argument("y", type=int)
    p = add("slide", cmd_slide); p.add_argument("selector", nargs="+"); p.add_argument("--frac", type=float, required=True)
    p = add("swipe", cmd_swipe)
    for k in ("x1", "y1", "x2", "y2"):
        p.add_argument(k, type=int)
    p.add_argument("ms", type=int, nargs="?", default=300)
    add("type", cmd_type).add_argument("text")
    add("clear", cmd_clear)
    p = add("set-text", cmd_set_text); p.add_argument("selector", nargs="+"); p.add_argument("--value", required=True)
    p = add("scroll", cmd_scroll); p.add_argument("direction", choices=("down", "up"))
    p.add_argument("--times", type=int, default=1); p.add_argument("--in", dest="within", nargs="+")
    p = add("scroll-to", cmd_scroll_to); p.add_argument("selector", nargs="+")
    p.add_argument("--dir", choices=("down", "up"), default="down"); p.add_argument("--max", type=int, default=8)
    p.add_argument("--in", dest="within", nargs="+")
    add("wait", cmd_wait).add_argument("selector", nargs="+")
    add("assert", cmd_wait).add_argument("selector", nargs="+")
    add("wait-gone", cmd_wait_gone).add_argument("selector", nargs="+")
    add("assert-text", cmd_assert_text).add_argument("texts", nargs="+")
    add("back", lambda a: cmd_key(argparse.Namespace(keys=["KEYCODE_BACK"])))
    add("home", lambda a: cmd_key(argparse.Namespace(keys=["KEYCODE_HOME"])))
    add("enter", lambda a: cmd_key(argparse.Namespace(keys=["KEYCODE_ENTER"])))
    add("key", cmd_key).add_argument("keys", nargs="+")
    add("launch", cmd_launch).add_argument("--clear", action="store_true")
    add("top", cmd_top)
    add("page", cmd_page).add_argument("--out")

    a = ap.parse_args(argv)
    if a.serial:
        SERIAL = a.serial
    try:
        a.fn(a)
    except UiError as e:
        sys.stderr.write("[ui] ERROR: %s\n" % e)
        sys.exit(2)
    except subprocess.TimeoutExpired as e:
        sys.stderr.write("[ui] ERROR: adb timed out: %s\n" % e)
        sys.exit(2)


if __name__ == "__main__":
    main()
