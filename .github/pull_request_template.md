## What this changes

<!-- In a sentence or two, what a player notices. Link the issue: "Fixes #12". -->

## Screens

<!-- Changed a screen? Re-record the goldens (CONTRIBUTING.md, "Screenshot tests") and paste a
     before/after, or point at the golden PNGs in the diff. -->

## Checklist

- [ ] `./gradlew testDebugUnitTest lintDebug detekt` passes (CI runs it on every push)
- [ ] New text is in `strings.xml`, plain and short
- [ ] Everything tappable is at least 48 dp and has a TalkBack label
- [ ] Nothing that worked before is gone; if something moved, it says where below
- [ ] No preference key renamed (a change of saved data migrates once, with a test)
- [ ] Changed screens: goldens re-recorded and looked at
- [ ] `versionName`, `versionCode` and `CHANGELOG.md` left alone (the maintainer sets them when it lands)

## Moved or removed

<!-- "None", or each thing and where it went. -->
