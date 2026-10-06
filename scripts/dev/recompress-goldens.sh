#!/usr/bin/env bash
# Recompresses screenshot goldens losslessly with oxipng (PP-090) and proves it: each file is
# compared with the file it replaced, pixel for pixel, the way Roborazzi reads a golden
# (SamePixels.java). If any pixel differs, or a tool fails, every file is put back.
#
#   scripts/dev/recompress-goldens.sh --changed   what a recording run changed or added. A changed
#       golden whose pixels match the committed one is put back as committed (only the encoding
#       differed), so a screen that didn't change never adds a blob to the history.
#   scripts/dev/recompress-goldens.sh --all       every committed golden a test still records (the
#       retired ones, see retired-goldens.sh, are left alone: they are to be deleted).
#   scripts/dev/recompress-goldens.sh FILE...     these files.
#
# Prints one summary line on stdout (how many files, their size before and after); progress goes
# to stderr. Needs git, java 11+ and oxipng ($OXIPNG, default `oxipng` on PATH; goldens.yml installs
# a pinned release). oxipng runs with --ng, so a golden never becomes greyscale: Java reads a
# greyscale PNG through a linear colour space, and Roborazzi would see other colours.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
oxipng="${OXIPNG:-oxipng}"
cd "$(git rev-parse --show-toplevel)"

goldens=':(glob)**/src/test/screenshots/**/*.png'
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
log() { echo "recompress-goldens: $*" >&2; }

# Writes the verdict ("same" or "diff", then the two paths) of each "left<TAB>right" line of $1 to $2.
compare() { java -Djava.awt.headless=true "$here/SamePixels.java" "$1" > "$2"; }

files=()
restored=0
case "${1:-}" in
  --all)
    retired_list="$("$here/retired-goldens.sh" --paths)"
    declare -A retired=()
    while IFS= read -r f; do
      if [[ -n $f ]]; then retired["$f"]=1; fi
    done <<< "$retired_list"
    while IFS= read -r -d '' f; do
      if [[ -z ${retired["$f"]:-} ]]; then files+=("$f"); fi
    done < <(git ls-files -z -- "$goldens")
    log "${#files[@]} committed goldens (${#retired[@]} retired ones left alone)"
    ;;
  --changed)
    mapfile -d '' -t modified < <(git diff --name-only -z HEAD -- "$goldens")
    mapfile -d '' -t added < <(git ls-files -z --others --exclude-standard -- "$goldens")
    : > "$work/head-pairs"
    for i in "${!modified[@]}"; do
      path="${modified[$i]}"
      [[ -f $path ]] || continue # deleted: nothing to recompress
      if git cat-file -e "HEAD:$path" 2> /dev/null; then
        git show "HEAD:$path" > "$work/head-$i.png"
        printf '%s\t%s\n' "$work/head-$i.png" "$path" >> "$work/head-pairs"
      else
        files+=("$path") # staged but not in HEAD: new
      fi
    done
    if [[ -s $work/head-pairs ]]; then
      compare "$work/head-pairs" "$work/head-verdicts"
      while IFS=$'\t' read -r verdict _ path; do
        if [[ $verdict == same ]]; then
          git checkout -q HEAD -- "$path" # only the encoding changed
          restored=$((restored + 1))
        else
          files+=("$path")
        fi
      done < "$work/head-verdicts"
    fi
    files+=("${added[@]}")
    log "${#modified[@]} changed and ${#added[@]} new goldens; $restored of the changed ones only re-encoded, put back as committed"
    ;;
  -h | --help)
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  '' | -*)
    log "usage: recompress-goldens.sh --changed | --all | FILE... (try --help)"
    exit 2
    ;;
  *) files=("$@") ;;
esac

if ((${#files[@]} == 0)); then
  note=""
  if ((restored > 0)); then note="; $restored re-encoded but unchanged goldens put back as committed"; fi
  echo "No goldens to recompress$note."
  exit 0
fi
command -v "$oxipng" > /dev/null || { log "oxipng not found (set OXIPNG to its path)"; exit 2; }

: > "$work/pairs"
before=0
for i in "${!files[@]}"; do
  cp -p -- "${files[$i]}" "$work/orig-$i.png"
  printf '%s\t%s\n' "$work/orig-$i.png" "${files[$i]}" >> "$work/pairs"
  before=$((before + $(stat -c %s -- "${files[$i]}")))
done
put_back() {
  for i in "${!files[@]}"; do cp -p -- "$work/orig-$i.png" "${files[$i]}"; done
  log "every file put back as it was"
}

log "$("$oxipng" --version) on ${#files[@]} files"
if ! printf '%s\0' "${files[@]}" | xargs -0 -n 100 "$oxipng" -q -o 4 --strip safe --ng --; then
  log "oxipng failed"
  put_back
  exit 1
fi
if ! compare "$work/pairs" "$work/verdicts"; then
  log "the pixel check failed to run"
  put_back
  exit 1
fi
if grep -q -v '^same' "$work/verdicts"; then
  log "pixels changed in:"
  grep -v '^same' "$work/verdicts" | cut -f3 >&2
  put_back
  exit 1
fi

after=0
for f in "${files[@]}"; do after=$((after + $(stat -c %s -- "$f"))); done
awk -v n="${#files[@]}" -v b="$before" -v a="$after" -v r="$restored" 'BEGIN {
  printf "Recompressed %d goldens losslessly: %.2f MB -> %.2f MB (%.1f%% smaller); pixels identical as Roborazzi reads them", \
    n, b / 1e6, a / 1e6, (b > 0 ? 100 * (b - a) / b : 0)
  if (r > 0) printf "; %d re-encoded but unchanged goldens put back as committed", r
  print "."
}'
