# Releasing Poker Payout

One command tests, builds, signs, verifies and ships a release to GitHub. F-Droid then
picks it up from the git tag without anyone touching fdroiddata, except in the few cases
listed under [When F-Droid needs a merge request](#when-f-droid-needs-a-merge-request).

```bash
# 1. Write the blurb (outside git, or under build/ which is ignored)
mkdir -p build/notes && $EDITOR build/notes/1.2.0.md

# 2. Rehearse: changes nothing, builds in a throwaway clone with the debug key
scripts/release/release.sh --bump minor --notes-file build/notes/1.2.0.md --repro-check

# 3. Ship it (from the main checkout, on master)
scripts/release/release.sh --publish --bump minor --notes-file build/notes/1.2.0.md

# 4. Follow F-Droid
scripts/release/fdroid-status.sh 1.2.0
```

Every script prints one line per stage. Full logs and artifacts go to
`build/release/<tag>/` (or `<tag>-dry-run/`).

## One-time setup

| What | How | Status |
|---|---|---|
| Release signing key | `scripts/release/new-signing-key.sh` creates `pokerpayout-release-2026.keystore` and `keystore.properties` (both gitignored, chmod 600) and pins the new fingerprint in `metadata/com.huntercoles.pokerpayout.yml`. **Back up both files together.** `scripts/release/verify-signing.sh` checks them without printing secrets. | Owner, once |
| JDK 21 | `sudo apt install openjdk-21-jdk-headless`. It's already installed here as `/usr/lib/jvm/java-21-openjdk-amd64`. | Done |
| GitHub CLI | `gh auth login` with the `repo` scope | Done |
| GitLab token | Needed only for fdroiddata merge requests. See [GitLab token](#gitlab-token). | Owner, before the first new-key release |

Nothing goes in `~/.gradle/gradle.properties`. Gradle reads signing from
`keystore.properties` at the repo root. It falls back to `RELEASE_*` Gradle properties or
`ORG_GRADLE_PROJECT_RELEASE_*` environment variables.

## Release notes

The notes file is Markdown. If the first line is `# Title`, it becomes the release title
(`v1.2.0 Title`), the commit message and the tag message. The rest becomes the GitHub release
body. The script appends the APK's SHA-256 and the signing certificate fingerprint.

```markdown
# Smoother tournament timer

- Timer keeps running when the screen is off
- Fixed payout rounding for odd player counts
```

From the same notes, `release.sh` writes F-Droid's "What's New" to
`metadata/en-US/changelogs/<versionCode>.txt`. It turns the Markdown into plain text, keeps
whole bullets, and stays under F-Droid's 500-character limit (`char_limits.whatsNew` in
fdroidserver; F-Droid would otherwise cut the text mid-word). F-Droid reads that file from
the tagged commit: `fdroid update` scans `metadata/<locale>/` in the app's source checkout,
alongside fastlane and Triple-T layouts. Pass `--fdroid-notes file.txt` to write it yourself.
Without `--notes-file`, the script drafts notes from `git log <last tag>..HEAD`.

## What `release.sh` does

| Stage | Dry run (default) | `--publish` |
|---|---|---|
| preflight | Problems are warnings, except missing tools or JDK | Every check must pass: main checkout, `master`, clean tree, in sync with `origin`, `gh` logged in, tag and release free, versionCode above F-Droid's, `verify-signing.sh` passes, F-Droid's buildserver has the JDK the build needs |
| unit tests | `testDebugUnitTest` (`--skip-tests`) | same |
| device tour | `scripts/device/tour.sh` if present (`--skip-tour`) | same |
| bump | versionName (`--bump` / `--version`), versionCode +1, metadata mirror rebuilt from fdroiddata's current file plus the new `Builds` entry, What's New file, commit | same, in the main checkout |
| build | `clean :app:assembleRelease`, signed with the debug key | signed with the release key |
| APK checks | name, package/version, not debuggable, v2 signature, no baseline profile, embedded commit = release commit, signer vs `AllowedAPKSigningKeys` (a mismatch is a warning; `--require-release-key` makes it fatal, exit 3) | signer mismatch is fatal |
| repro check | `--repro-check`: fresh clone, F-Droid's signing-config strip, `gradle clean` + `gradle assembleRelease` from `app/`, byte compare outside the signing block | on by default |
| ship | prints what it would do | annotated tag, `git push --atomic` of master + tag, `gh release create` with the APK, then checks that F-Droid's `Binaries:` URL serves the same bytes |

If anything fails before the push, `--publish` rolls back its own commit and tag and puts
the files back as they were. After the push nothing is rolled back. The script prints the
exact `gh release create …` command to retry with.

## How F-Droid picks up a release

Measured on 1.1.12:

| When (UTC) | What |
|---|---|
| 2025-12-14 01:29 | GitHub release + tag `v1.1.12` |
| 07:41 (+6 h) | `checkupdates bot` commits "Update Poker Payout to 24" to fdroiddata (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`: it reads versionName/versionCode from `app/build.gradle.kts` at the newest tag and copies the previous `Builds` entry) |
| 18:04 (+16.5 h) | buildserver builds `v1.1.12` from source, downloads the GitHub APK named by `Binaries:`, copies its signature onto its own unsigned build with apksigcopier and verifies it: "compared built binary to supplied reference binary successfully" |
| same day | f-droid.org serves our signed APK (byte-identical to the GitHub asset) |

Expect 1–3 days in general. `fdroid-status.sh` shows each stage: tag on origin, GitHub asset,
fdroiddata build entry and pinned key, buildserver log (monitor.f-droid.org) and publication.

## Reproducible builds

F-Droid only publishes our APK if its own build from the tag is byte-identical outside the
APK Signing Block. What that takes, with evidence from this repo:

* **Same JDK major as F-Droid.** F-Droid's buildserver is Debian 13 (trixie) since 2026, and
  its only JDK is OpenJDK 21. Toolchain auto-download is off (`buildserver/provision-gradle`).
  javac 21 and javac 17 produce different dex: rebuilding v1.1.12 with JDK 21 changed
  `classes2.dex`, because javac 21 emits `MethodParameters` for mandated/synthetic
  parameters. Rebuilding with JDK 17 at a different path, with Temurin instead of
  Debian/Ubuntu OpenJDK, gave a byte-identical APK. Vendor, patch level and path don't
  matter; the major version does. See [JDK](#jdk).
* **Build from the exact tagged commit, tree clean.** AGP writes
  `META-INF/version-control-info.textproto` with the commit hash into the APK. The
  `packaging.excludes` entry for it does not remove it. `release.sh` commits first, then
  builds, and checks the embedded hash.
* **Clean build, no caches.** `--no-build-cache --no-configuration-cache --no-daemon`.
  F-Droid deletes `build-cache/` via `scandelete`.
* **No baseline profiles** (already stripped in `app/build.gradle.kts`).
* **v2 signing, one signer.** minSdk 26 means AGP signs v2 only. F-Droid's signature copy
  and its `AllowedAPKSigningKeys` check both work on v2.
* **Signing config may be stripped.** F-Droid deletes `signingConfigs { … }` blocks and some
  `signingConfig = …` lines before building (`remove_signing_keys` in fdroidserver; regexes
  `^[\t ]*signingConfigs[ \t]*{[ \t]*$`, `^[\t ]*signingConfig\s*[= ]\s*[^ ]*$`,
  `.*android\.signingConfigs\.[^{]*$`, `.*release\.signingConfig *= *`; lines starting with
  `//` are kept). In our `app/build.gradle.kts` that removes the whole `signingConfigs`
  block. The top-level `keystoreProperties`/`keystoreValue` code and the multi-line
  `signingConfig = if (…) {…} else {…}` are kept, and the latter falls back to the debug
  config. The script stays valid Kotlin: `--repro-check` builds exactly that stripped file.
  The signature is ignored by the comparison.
* No container is needed: no build step depends on the OS, and the path doesn't leak. The
  old `Dockerfile` (Ubuntu 22.04 + OpenJDK 17) is how 1.1.0–1.1.12 were built on Windows.
  It now needs JDK 21 if anyone uses it again.

## JDK

All modules use `jvmToolchain(21)`; bytecode targets stay at 17. `release.sh` picks a JDK of
that major, preferring Debian/Ubuntu OpenJDK (`PP_JDK_HOME` overrides). It runs Gradle with
it and makes it the only toolchain Gradle may use. If the modules ever ask for a JDK that
F-Droid's buildserver doesn't have, `--publish` refuses (`--ignore-fdroid-jdk` overrides).

If F-Droid's buildserver moves to a newer Debian with a different default JDK, there are
two fixes:

* move `jvmToolchain` to that JDK in all five modules and release; or
* add a `sudo:` block to the fdroiddata recipe that installs the old JDK, as `cube.run.yml`
  does for 17 on trixie: `echo "deb https://deb.debian.org/debian bookworm main" >
  /etc/apt/sources.list.d/bookworm.list`, `apt-get update`, `apt-get install -y -t bookworm
  openjdk-17-jdk-headless`, `update-java-alternatives -s java-1.17.0-openjdk-amd64`.

## When F-Droid needs a merge request

Version bumps never need one. `checkupdates` handles tags, versionName/versionCode,
changelogs, descriptions and screenshots (from `metadata/en-US/` in this repo), new
dependencies, targetSdk/compileSdk bumps (the buildserver installs SDK platforms on demand)
and Gradle/AGP updates that still run on its JDK.

You need an fdroiddata MR when the recipe or the pinned facts change:

| Change | MR content |
|---|---|
| **Signing key** (the 2026 key change) | `AllowedAPKSigningKeys`, `disable:` on old-key builds |
| JDK the build needs ≠ buildserver JDK | `sudo:` block (see [JDK](#jdk)) |
| APK name or tag scheme (`Binaries:`, `UpdateCheckMode`) | those fields |
| Product flavors, `subdir`, NDK, extra build steps, `prebuild`, `srclibs` | build entry fields |
| versionCode scheme (e.g. per-ABI codes) | `VercodeOperation` |
| New anti-feature (ads, tracking, non-free network service) | `AntiFeatures` |
| Categories, donation links, license, author fields | those fields |
| Builds that keep failing (to stop retries) | `disable:` on that build |

If F-Droid's build fails its reproducibility check, you don't need an MR. Fix the cause and
release a new version: the next tag gets a new build entry.

### Opening an MR without cloning fdroiddata

`scripts/release/fdroid-mr.sh` (dry run by default) downloads fdroiddata's current file,
applies the change and prints the YAML diff with the MR title and body. It saves them to
`build/fdroid-mr/<tag>/` for the manual route. With `--publish` it uses the GitLab REST API:
it reuses or creates your fork of fdroid/fdroiddata, makes one commit on a new branch started
from fdroiddata's `master` (`start_project`), and opens the MR. It won't open a second MR
while one for Poker Payout is still open.

Manual route (GitLab web editor, also needs no clone):

1. Sign in on gitlab.com and open
   <https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.huntercoles.pokerpayout.yml>.
2. **Edit → Edit single file.** GitLab offers to fork the project. Accept.
3. Replace the content with `build/fdroid-mr/<tag>/com.huntercoles.pokerpayout.yml`.
4. Commit message: the MR title. **Commit changes** creates a branch in your fork.
5. **Create merge request** to `fdroid/fdroiddata:master`. Paste `build/fdroid-mr/<tag>/mr.md`
   as the description.

### GitLab token

Create it at gitlab.com → Preferences → Access tokens:

* scope: **`api`**. `write_repository` alone can't fork or open MRs. No other scopes.
* expiry: short (e.g. 30 days). It's only needed while the MR is opened.

```bash
umask 077; $EDITOR gitlab.token     # paste the token alone; file is gitignored
chmod 600 gitlab.token
```

`fdroid-mr.sh` refuses a token file that isn't chmod 600 or isn't gitignored. It sends the
token through a private curl config file, never on a command line, and never prints it.

## The 2026 signing key change (one time)

The password of `pokerpayout-release.keystore` is lost. That key signed 1.1.11 and 1.1.12
(certificate SHA-256 `0558e114…de7`). From the next release on, a new key signs the APKs.
Android won't update an app over a different signing key, so each user must uninstall and
reinstall once. Uninstalling deletes the app's saved data.

1. Owner: `scripts/release/new-signing-key.sh`. It creates the 2026 keystore and
   `keystore.properties`, and pins the new fingerprint in the in-repo mirror. Back up both
   files. Commit the mirror change, or leave it: `release.sh` accepts exactly that one-line
   change and folds it into the release commit.
2. Rehearse: `scripts/release/release.sh --bump minor --notes-file build/notes/1.2.0.md`.
   Preflight reports `rotation: F-Droid pins 0558e114…, this release pins <new>…`. The GitHub
   notes and the F-Droid What's New are prefixed with a plain reinstall notice, unless your
   notes already mention reinstalling. The mirror gets the new key and `disable:` on
   1.1.11/1.1.12.
3. Ship: `scripts/release/release.sh --publish --bump minor --notes-file build/notes/1.2.0.md`.
4. Right away: `scripts/release/fdroid-mr.sh`, then `scripts/release/fdroid-mr.sh --publish`.
   The MR:
   * sets `AllowedAPKSigningKeys` to **only the new fingerprint**. `fdroid update` drops every
     APK whose signer isn't listed (`get_apks_without_allowed_signatures`), so 1.1.11/1.1.12
     leave the index. Keeping both fingerprints would keep the old versions installable,
     which we don't want.
   * adds `disable: signing key lost; …` to the 1.1.11 and 1.1.12 builds. `fdroid update`
     then deletes their APKs (`delete_disabled_builds`), and the buildserver never tries to
     rebuild them (`trybuild` skips disabled builds; without `disable:`, removing their APKs
     would make it retry and fail every cycle on the old-key reference binary).
   * adds the new `Builds` entry and `CurrentVersion`/`CurrentVersionCode`, unless the
     checkupdates bot got there first.
5. Until the MR is merged, F-Droid's build of the new version fails its signer check
   ("supplied reference binary signed with … instead of …"). It isn't disabled, so the next
   cycle after the merge builds it. `fdroid-status.sh` shows the open MR and the pinned key.

## Files

| Path | Role |
|---|---|
| `scripts/release/release.sh` | the pipeline |
| `scripts/release/fdroid-status.sh` | where a version is in F-Droid's pipeline |
| `scripts/release/fdroid-mr.sh` | fdroiddata MR via the GitLab API |
| `scripts/release/new-signing-key.sh` | one-time key creation (owner runs it) |
| `scripts/release/verify-signing.sh`, `VerifySigning.java` | checks `keystore.properties` against the keystore and the pinned fingerprint, prints pass/fail only |
| `scripts/release/lib/apkdiff.py` | F-Droid-equivalent APK comparison (stdlib only) |
| `scripts/release/lib/fdroid_strip_signing.py` | re-implementation of fdroidserver's signing-config strip |
| `scripts/release/lib/metadata.py` | version bump, fdroiddata YAML edits, notes → What's New |
| `metadata/com.huntercoles.pokerpayout.yml` | mirror of fdroiddata's recipe, rebuilt from upstream on every release |
| `metadata/en-US/` | title, descriptions, images, `changelogs/<versionCode>.txt`, read by F-Droid from the tag |
