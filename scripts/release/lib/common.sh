# shellcheck shell=bash
# Shared helpers for scripts/release/*.sh. Source it; do not execute it.
#
# Environment knobs (all optional):
#   PP_JDK_HOME     use this JDK for Gradle (must match the project's jvmToolchain)
#   PP_GRADLE_XMX   Gradle daemon heap (default 3g; another agent may share the machine)
#   ANDROID_HOME    Android SDK (default $HOME/Android/Sdk)

APP_ID="com.huntercoles.pokerpayout"
APK_PREFIX="PokerPayout"
META_REL="metadata/$APP_ID.yml"
CHANGELOG_DIR_REL="metadata/en-US/changelogs"
# F-Droid's buildserver is Debian 13 "trixie" (since 2026), whose only packaged JDK is 21.
# Its Gradle setup disables toolchain auto-download, so jvmToolchain(N) must equal this
# unless the fdroiddata recipe installs another JDK via `sudo:`.
FDROID_JDK=21
# F-Droid truncates "What's New" (whatsNew) at this many characters (fdroidserver char_limits).
FDROID_WHATSNEW_LIMIT=500
FDROIDDATA_RAW="https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/$APP_ID.yml"
FDROIDDATA_API="https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# ---------------------------------------------------------------- output
if [[ -t 1 ]]; then C_OK=$'\e[32m' C_BAD=$'\e[31m' C_WARN=$'\e[33m' C_DIM=$'\e[2m' C_OFF=$'\e[0m'
else C_OK='' C_BAD='' C_WARN='' C_DIM='' C_OFF=''; fi

line_ok()   { printf '  %s✔%s %-15s %s\n' "$C_OK" "$C_OFF" "$1" "$2"; }
line_bad()  { printf '  %s✘%s %-15s %s\n' "$C_BAD" "$C_OFF" "$1" "$2"; }
line_warn() { printf '  %s!%s %-15s %s\n' "$C_WARN" "$C_OFF" "$1" "$2"; }
line_skip() { printf '  %s-%s %-15s %s\n' "$C_DIM" "$C_OFF" "$1" "$2"; }
note()      { printf '    %s%s%s\n' "$C_DIM" "$*" "$C_OFF"; }
die()       { printf '%sERROR:%s %s\n' "$C_BAD" "$C_OFF" "$*" >&2; exit 1; }

# Show where the log is and its last lines after a failed step.
show_log_tail() {
  local log=$1
  note "log: $log"
  if [[ -s $log ]]; then
    tail -n "${2:-15}" "$log" | sed 's/^/      /'
  fi
}

elapsed() { echo "$(( $(date +%s) - $1 ))s"; }

# ---------------------------------------------------------------- repo paths
# Root of the checkout that contains this script.
checkout_root() { git -C "$1" rev-parse --show-toplevel; }

# Root of the main checkout (the one owning .git), even when called from a linked worktree.
main_root() {
  local common
  common="$(git -C "$1" rev-parse --path-format=absolute --git-common-dir)"
  dirname "$common"
}

# owner/repo from the origin URL (https or ssh form).
github_repo() {
  git -C "$1" remote get-url origin 2>/dev/null \
    | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##'
}

# ---------------------------------------------------------------- JDK
jdk_major() { sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$1/release" 2>/dev/null; }
jdk_vendor() { sed -n 's/^IMPLEMENTOR="\(.*\)"/\1/p' "$1/release" 2>/dev/null; }
jdk_version() { sed -n 's/^JAVA_VERSION="\(.*\)"/\1/p' "$1/release" 2>/dev/null; }

# The JDK major every module asks for via jvmToolchain(N); fails if modules disagree.
required_jdk() {
  local root=$1 majors
  majors="$(grep -ho 'jvmToolchain([0-9]*)' "$root"/*/build.gradle.kts 2>/dev/null \
    | grep -o '[0-9]\+' | sort -u)"
  if [[ -z $majors ]]; then echo ""; return 0; fi
  if [[ $(wc -l <<<"$majors") -ne 1 ]]; then
    echo "modules disagree on jvmToolchain: $(tr '\n' ' ' <<<"$majors")" >&2
    return 1
  fi
  echo "$majors"
}

# Pick a JDK of the given major. Distro OpenJDK (Debian/Ubuntu) is preferred because
# F-Droid builds with Debian's OpenJDK; then Gradle-provisioned and IDE JDKs.
find_jdk() {
  local want=$1 cand env_var="JAVA${1}_HOME"
  local -a cands=("${PP_JDK_HOME:-}" "${!env_var:-}")
  for cand in /usr/lib/jvm/*; do
    [[ -L $cand ]] && continue
    case "$(jdk_vendor "$cand")" in Debian|Ubuntu) cands+=("$cand") ;; esac
  done
  cands+=(/usr/lib/jvm/* "${GRADLE_USER_HOME:-$HOME/.gradle}"/jdks/* "$HOME"/.jdks/* /opt/android-studio/jbr)
  for cand in "${cands[@]}"; do
    [[ -n $cand && -x $cand/bin/javac ]] || continue
    if [[ "$(jdk_major "$cand")" == "$want" ]]; then
      readlink -f "$cand"
      return 0
    fi
  done
  return 1
}

# ---------------------------------------------------------------- Android tools
latest_build_tool() { # $1 = tool name (apksigner, aapt2, zipalign)
  local d
  for d in $(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V -r); do
    [[ -x $d/$1 ]] && { echo "$d/$1"; return 0; }
  done
  return 1
}

# ---------------------------------------------------------------- Gradle
# gradle_run <project dir to run in> <wrapper path> <args...>
# Hermetic-ish: one-shot daemon, no build cache, no configuration cache, the chosen JDK
# both runs Gradle and is the only toolchain Gradle may use (as on F-Droid).
gradle_run() {
  local dir=$1 wrapper=$2; shift 2
  (
    cd "$dir"
    export JAVA_HOME="$RELEASE_JDK" PATH="$RELEASE_JDK/bin:$PATH" TZ=UTC LANG=C.UTF-8
    "$wrapper" --no-daemon --console=plain --no-build-cache --no-configuration-cache \
      "-Dorg.gradle.jvmargs=-Xmx${PP_GRADLE_XMX:-3g} -Dfile.encoding=UTF-8 -XX:+UseParallelGC" \
      -Pkotlin.daemon.jvmargs=-Xmx2g \
      -Porg.gradle.java.installations.auto-download=false \
      -Porg.gradle.java.installations.auto-detect=false \
      "-Porg.gradle.java.installations.paths=$RELEASE_JDK" \
      "$@"
  )
}

write_local_properties() { # $1 = project root
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" >"$1/local.properties"
}

# ---------------------------------------------------------------- misc
semver_gt() { # true if $1 > $2 (X.Y.Z)
  [[ $1 != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n1)" == "$1" ]]
}

bump_version() { # $1 = X.Y.Z, $2 = patch|minor|major
  local major minor patch
  IFS=. read -r major minor patch <<<"$1"
  case "$2" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "$major.$((minor + 1)).0" ;;
    patch) echo "$major.$minor.$((patch + 1))" ;;
    *) return 1 ;;
  esac
}

# fetch <url> <out file> : quiet curl with a timeout; returns curl's status
fetch() { curl -fsSL --max-time "${3:-30}" -o "$2" "$1"; }
