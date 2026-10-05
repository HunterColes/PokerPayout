#!/usr/bin/env python3
"""Small, dependency-free helpers for the release pipeline.

Subcommands
  get-version GRADLE                 print "versionName versionCode" from app/build.gradle.kts
  set-version GRADLE NAME CODE       rewrite versionName / versionCode in place
  field YML KEY                      print a top-level scalar from an fdroiddata-style yml
  add-build YML NAME CODE COMMIT     append a Builds entry (copy of the last one, like
                                     F-Droid's checkupdates bot) and set CurrentVersion(Code)
  has-build YML CODE                 exit 0 if the yml has a build for CODE
  rotate-key YML SHA256 CODE REASON  pin only SHA256 and add `disable: REASON` to every
                                     build with a versionCode below CODE
  check-yml YML NAME CODE            sanity-check the yml (parses, has the build, current version)
  notes-title NOTES                  first "# " heading of a markdown notes file (may be empty)
  notes-body NOTES                   the notes without that heading
  fdroid-changelog NOTES LIMIT       plain-text "What's New" for F-Droid, at most LIMIT chars

Edits are text-based so the file keeps fdroiddata's exact formatting.
"""

import re
import sys

VERSION_CODE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.M)
VERSION_NAME = re.compile(r'^(\s*versionName\s*=\s*")([^"]+)("\s*)$', re.M)


def die(msg):
    print(f"metadata.py: {msg}", file=sys.stderr)
    sys.exit(1)


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def get_version(gradle):
    text = read(gradle)
    names, codes = VERSION_NAME.findall(text), VERSION_CODE.findall(text)
    if len(names) != 1 or len(codes) != 1:
        die(f"{gradle}: expected exactly one versionName and versionCode")
    return names[0][1], int(codes[0][1])


def set_version(gradle, name, code):
    text = read(gradle)
    text, n1 = VERSION_NAME.subn(lambda m: f"{m.group(1)}{name}{m.group(3)}", text)
    text, n2 = VERSION_CODE.subn(lambda m: f"{m.group(1)}{code}{m.group(3)}", text)
    if n1 != 1 or n2 != 1:
        die(f"{gradle}: could not rewrite version")
    write(gradle, text)


def field(yml_text, key):
    """Top-level value: a scalar, fdroiddata's folded form (`Key: ` then indented lines),
    or a list (`- item` lines, returned comma-separated)."""
    lines = yml_text.splitlines()
    for i, line in enumerate(lines):
        m = re.match(rf"^{re.escape(key)}:\s*(.*)$", line)
        if not m:
            continue
        parts = [m.group(1).strip()] if m.group(1).strip() else []
        items = []
        for nxt in lines[i + 1:]:
            if not (nxt.startswith((" ", "\t")) and nxt.strip()):
                break
            if nxt.lstrip().startswith("- "):
                items.append(nxt.lstrip()[2:].strip())
            else:
                parts.append(nxt.strip())
        return ",".join(items) if items and not parts else " ".join(parts)
    return ""


def builds_section(lines, yml):
    """(start, end, entry_starts) of the Builds: section in a list of lines."""
    try:
        start = next(i for i, l in enumerate(lines) if l.rstrip() == "Builds:")
    except StopIteration:
        die(f"{yml}: no Builds: section")
    end = start + 1
    while end < len(lines) and (lines[end].startswith((" ", "\t")) or not lines[end].strip()):
        end += 1
    # Entries are the "- " items at the shallowest indentation (nested lists such
    # as `gradle:` sit deeper).
    items = [(i, len(lines[i]) - len(lines[i].lstrip())) for i in range(start + 1, end)
             if re.match(r"^\s*- ", lines[i])]
    if not items:
        die(f"{yml}: Builds: has no entries")
    indent = min(ind for _, ind in items)
    return start, end, [i for i, ind in items if ind == indent]


def rotate_key(yml, key, below_code, reason):
    """Pin only the new certificate and disable every older build.

    fdroidserver drops APKs whose signer is not in AllowedAPKSigningKeys from the index,
    and `disable:` makes it delete those builds' APKs and never try to rebuild them."""
    text = read(yml)
    text, n = re.subn(r"(?m)^AllowedAPKSigningKeys:.*(?:\n[ \t]+\S.*)*$",
                      f"AllowedAPKSigningKeys: {key}", text)
    if n != 1:
        die(f"{yml}: no AllowedAPKSigningKeys")
    lines = text.splitlines(keepends=True)
    _, end, starts = builds_section(lines, yml)
    bounds = list(zip(starts, starts[1:] + [end]))
    for s, e in reversed(bounds):
        entry = lines[s:e]
        joined = "".join(entry)
        m = re.search(r"versionCode:\s*(\d+)", joined)
        if not m or int(m.group(1)) >= below_code or re.search(r"(?m)^\s*disable:", joined):
            continue
        for j, l in enumerate(entry):
            if re.match(r"^\s*-?\s*versionCode:", l):
                indent = len(l) - len(l.lstrip()) if not l.lstrip().startswith("- ") \
                    else len(l) - len(l.lstrip()) + 2
                lines.insert(s + j + 1, " " * indent + f"disable: {reason}\n")
                break
    write(yml, "".join(lines))


def add_build(yml, name, code, commit):
    lines = read(yml).splitlines(keepends=True)
    start, end, entry_starts = builds_section(lines, yml)
    last = entry_starts[-1]
    last_end = end
    while last_end > last and not lines[last_end - 1].strip():
        last_end -= 1
    block = "".join(lines[last:last_end])
    if re.search(rf"^\s*-?\s*versionCode:\s*{code}\s*$", "".join(lines[start:end]), re.M):
        die(f"{yml}: already has a build for versionCode {code}")
    new = re.sub(r"(versionName:\s*).*", lambda m: m.group(1) + name, block, count=1)
    new = re.sub(r"(versionCode:\s*).*", lambda m: m.group(1) + str(code), new, count=1)
    new = re.sub(r"(\n\s*commit:\s*).*", lambda m: m.group(1) + commit, new, count=1)
    # A build-specific disable flag must not be inherited.
    new = re.sub(r"\n\s*disable:.*", "", new)
    out = lines[:last_end] + ["\n", new if new.endswith("\n") else new + "\n"] + lines[last_end:]
    text = "".join(out)
    text, n1 = re.subn(r"(?m)^CurrentVersion:.*$", f"CurrentVersion: {name}", text)
    text, n2 = re.subn(r"(?m)^CurrentVersionCode:.*$", f"CurrentVersionCode: {code}", text)
    if n1 != 1 or n2 != 1:
        die(f"{yml}: missing CurrentVersion / CurrentVersionCode")
    write(yml, text)


def check_yml(yml, name, code):
    text = read(yml)
    try:
        import yaml  # optional; the text checks below still run without it
    except ImportError:
        yaml = None
    if yaml is not None:
        data = yaml.safe_load(text)
        builds = data.get("Builds") or []
        if not any(str(b.get("versionName")) == name and int(b.get("versionCode")) == code for b in builds):
            die(f"{yml}: no Builds entry for {name} ({code})")
        if str(data.get("CurrentVersion")) != name or int(data.get("CurrentVersionCode")) != code:
            die(f"{yml}: CurrentVersion/CurrentVersionCode not {name}/{code}")
        codes = [int(b["versionCode"]) for b in builds]
        if codes != sorted(codes) or len(set(codes)) != len(codes):
            die(f"{yml}: Builds versionCodes are not strictly increasing: {codes}")
    elif f"CurrentVersionCode: {code}" not in text:
        die(f"{yml}: CurrentVersionCode is not {code}")


def notes_split(path):
    lines = read(path).strip("\n").splitlines()
    title = ""
    if lines and lines[0].startswith("# "):
        title = lines[0][2:].strip()
        lines = lines[1:]
    return title, "\n".join(lines).strip("\n")


def to_plain(md):
    out = []
    for line in md.splitlines():
        s = line.rstrip()
        s = re.sub(r"^#{1,6}\s*", "", s)                       # headings
        s = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", s)               # images
        s = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", s)           # links -> text
        s = re.sub(r"(\*\*|__|`)", "", s)                        # bold / code marks
        s = re.sub(r"^(\s*)[*+]\s+", r"\1- ", s)                 # bullets -> "- "
        if s.startswith("<!--") or s.lower().startswith(("full changelog", "**full changelog")):
            continue
        out.append(s)
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()
    return text


def fdroid_changelog(path, limit):
    _, body = notes_split(path)
    text = to_plain(body)
    if len(text) <= limit:
        return text
    # Keep whole lines (bullets) that fit, then an ellipsis line.
    kept, size = [], 0
    for line in text.splitlines():
        if size + len(line) + 1 > limit - 2:
            break
        kept.append(line)
        size += len(line) + 1
    return ("\n".join(kept).rstrip() + "\n…").strip()


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd, args = argv[1], argv[2:]
    if cmd == "get-version":
        name, code = get_version(args[0])
        print(name, code)
    elif cmd == "set-version":
        set_version(args[0], args[1], int(args[2]))
    elif cmd == "field":
        print(field(read(args[0]), args[1]))
    elif cmd == "add-build":
        add_build(args[0], args[1], int(args[2]), args[3])
    elif cmd == "has-build":
        text = read(args[0])
        return 0 if re.search(rf"(?m)^\s*-?\s*versionCode:\s*{int(args[1])}\s*$", text) else 1
    elif cmd == "rotate-key":
        rotate_key(args[0], args[1], int(args[2]), args[3])
    elif cmd == "check-yml":
        check_yml(args[0], args[1], int(args[2]))
    elif cmd == "notes-title":
        print(notes_split(args[0])[0])
    elif cmd == "notes-body":
        print(notes_split(args[0])[1])
    elif cmd == "fdroid-changelog":
        print(fdroid_changelog(args[0], int(args[1])))
    else:
        die(f"unknown command {cmd}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
