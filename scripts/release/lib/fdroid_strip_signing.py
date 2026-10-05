#!/usr/bin/env python3
"""Strip signing configuration from Gradle build files the way F-Droid does.

Before building, fdroidserver (common.remove_signing_keys) rewrites every
build.gradle / build.gradle.kts in the checkout: it drops each
`signingConfigs {` block (counting braces until it closes), drops single
lines that assign a signing config, and keeps lines that start with `//`.
This is an independent re-implementation of that behaviour, using the same
patterns as fdroidserver, so a local reproducibility build sees the same build
script F-Droid compiles.

Usage: fdroid_strip_signing.py PROJECT_DIR   (edits files in place, prints what changed)
"""

import os
import re
import sys

# Patterns as used by fdroidserver/common.py (gradle_comment,
# gradle_signing_configs, gradle_line_matches).
COMMENT = re.compile(r"[ ]*//")
SIGNING_CONFIGS = re.compile(r"^[\t ]*signingConfigs[ \t]*{[ \t]*$")
LINE_MATCHES = [
    re.compile(r"^[\t ]*signingConfig\s*[= ]\s*[^ ]*$"),
    re.compile(r".*android\.signingConfigs\.[^{]*$"),
    re.compile(r".*release\.signingConfig *= *"),
]


def strip(lines):
    out, opened, changed, i = [], 0, False, 0
    while i < len(lines):
        line = lines[i]
        i += 1
        while line.endswith("\\\n") and i < len(lines):
            line = line[:-2] + lines[i]
            i += 1
        if COMMENT.match(line):
            out.append(line)
            continue
        if opened > 0:
            opened += line.count("{") - line.count("}")
            continue
        if SIGNING_CONFIGS.match(line):
            changed, opened = True, 1
            continue
        if any(p.match(line) for p in LINE_MATCHES):
            changed = True
            continue
        out.append(line)
    return out, changed


def main(argv):
    if len(argv) != 2:
        print("usage: fdroid_strip_signing.py PROJECT_DIR")
        return 2
    for root, dirs, files in os.walk(argv[1]):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle")]
        name = "build.gradle" if "build.gradle" in files else (
            "build.gradle.kts" if "build.gradle.kts" in files else None)
        if not name:
            continue
        path = os.path.join(root, name)
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        new, changed = strip(lines)
        if changed:
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(new)
            print(f"stripped signing config: {os.path.relpath(path, argv[1])} "
                  f"({len(lines) - len(new)} lines removed)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
