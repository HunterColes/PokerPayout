#!/usr/bin/env python3
"""Compare two APKs the way F-Droid's reproducible-build check sees them.

F-Droid builds the app unsigned (signing configs stripped), then copies the
APK Signing Block from the developer's release APK onto its own build with
apksigcopier and runs `apksigner verify`. That only succeeds when every byte
outside the signing block is identical: local headers, entry data, padding,
the central directory and the end-of-central-directory record (apart from the
central directory offset, which moves with the block).

Usage: apkdiff.py REFERENCE.apk CANDIDATE.apk

Exit codes: 0 identical outside the signing block, 1 different, 2 bad input.
Uses only the Python standard library.
"""

import struct
import sys
import zipfile
import zlib

EOCD_SIG = b"PK\x05\x06"
SIG_BLOCK_MAGIC = b"APK Sig Block 42"
# v1 (JAR) signature files that apksigcopier excludes before comparing.
V1_SUFFIXES = (".SF", ".RSA", ".DSA", ".EC")


def parse(path):
    with open(path, "rb") as fh:
        data = fh.read()
    eocd = data.rfind(EOCD_SIG, max(0, len(data) - 65557))
    if eocd < 0:
        raise ValueError(f"{path}: no end-of-central-directory record")
    cd_size, cd_offset = struct.unpack_from("<II", data, eocd + 12)
    block_start = cd_offset
    has_block = data[cd_offset - 16:cd_offset] == SIG_BLOCK_MAGIC
    if has_block:
        (block_size,) = struct.unpack_from("<Q", data, cd_offset - 24)
        block_start = cd_offset - (block_size + 8)
    eocd_norm = bytearray(data[eocd:])
    struct.pack_into("<I", eocd_norm, 16, 0)  # central directory offset
    return {
        "path": path,
        "data": data,
        "entries": data[:block_start],
        "cd": data[cd_offset:cd_offset + cd_size],
        "eocd": bytes(eocd_norm),
        "signed": has_block,
    }


def is_v1_sig(name):
    if not name.startswith("META-INF/") or "/" in name[len("META-INF/"):]:
        return False
    return name == "META-INF/MANIFEST.MF" or name.upper().endswith(V1_SUFFIXES)


def entry_table(path):
    with zipfile.ZipFile(path) as zf:
        return [
            (i.filename, i.compress_type, i.CRC, i.compress_size, i.file_size, i.header_offset)
            for i in zf.infolist()
        ]


def explain(ref_path, cand_path, out):
    ref = entry_table(ref_path)
    cand = entry_table(cand_path)
    ref_names = [e[0] for e in ref if not is_v1_sig(e[0])]
    cand_names = [e[0] for e in cand if not is_v1_sig(e[0])]
    only_ref = sorted(set(ref_names) - set(cand_names))
    only_cand = sorted(set(cand_names) - set(ref_names))
    for name in only_ref:
        out.append(f"  only in reference: {name}")
    for name in only_cand:
        out.append(f"  only in candidate: {name}")
    if not only_ref and not only_cand and ref_names != cand_names:
        out.append("  same entries but in a different order")
    by_name = {e[0]: e for e in cand}
    changed = 0
    for e in ref:
        other = by_name.get(e[0])
        if other is None or is_v1_sig(e[0]):
            continue
        if e[1:5] != other[1:5]:
            changed += 1
            if changed <= 25:
                out.append(
                    f"  content differs: {e[0]} (crc {e[2]:08x}/{other[2]:08x}, "
                    f"size {e[4]}/{other[4]}, method {e[1]}/{other[1]})"
                )
        elif e[5] != other[5]:
            changed += 1
            if changed <= 25:
                out.append(f"  offset/padding differs: {e[0]} ({e[5]} vs {other[5]})")
    if changed > 25:
        out.append(f"  ... and {changed - 25} more differing entries")
    v1 = [e[0] for e in ref + cand if is_v1_sig(e[0])]
    if v1:
        out.append("  note: v1 (JAR) signature files present; F-Droid strips them before copying")


def main(argv):
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[0])
        print("usage: apkdiff.py REFERENCE.apk CANDIDATE.apk")
        return 2
    try:
        ref, cand = parse(argv[1]), parse(argv[2])
    except (OSError, ValueError, struct.error) as exc:
        print(f"apkdiff: {exc}")
        return 2
    same = ref["entries"] == cand["entries"] and ref["cd"] == cand["cd"] and ref["eocd"] == cand["eocd"]
    if same:
        crc = zlib.crc32(ref["entries"] + ref["cd"]) & 0xFFFFFFFF
        print(f"IDENTICAL outside the APK Signing Block ({len(ref['entries'])} bytes of entries, crc32 {crc:08x})")
        return 0
    lines = ["DIFFERENT: the F-Droid signature copy would fail"]
    if ref["entries"] != cand["entries"]:
        lines.append(f"  entry region: {len(ref['entries'])} vs {len(cand['entries'])} bytes")
    if ref["cd"] != cand["cd"]:
        lines.append(f"  central directory: {len(ref['cd'])} vs {len(cand['cd'])} bytes")
    try:
        explain(argv[1], argv[2], lines)
    except zipfile.BadZipFile as exc:
        lines.append(f"  (could not list entries: {exc})")
    print("\n".join(lines))
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
