# F-Droid Pre-Submission Checklist

Use this checklist to ensure you're ready to submit to F-Droid.

## ✅ Code Changes
- [x] Added `dependenciesInfo` block to `app/build.gradle.kts`
- [ ] Configure production signing keystore (currently using debug)
- [ ] Update `AllowedAPKSigningKeys` with production certificate SHA-256

## 📁 Metadata Files
- [x] Created `metadata/en-US/title.txt` ✅ **UPDATED** to "Poker Payout"
- [x] Created `metadata/en-US/short_description.txt` (max 80 chars) ✅ **IMPROVED** from README
- [x] Created `metadata/en-US/full_description.txt` ✅ **IMPROVED** from README
- [x] Added `metadata/en-US/icon.png` (512x512) ✅ **COPIED** from play store icon
- [x] Added `metadata/en-US/featureGraphic.png` (1024x500) - Create a banner image
- [x] Added screenshots to `metadata/en-US/phoneScreenshots/` ✅ **COPIED** 4 screenshots
- [x] Created `metadata/com.huntercoles.pokerpayout.yml` ✅ **UPDATED** AutoName to "Poker Payout"

## 🔑 Signing & Release
- [x] Built release APK
- [ ] Created production keystore
- [ ] Signed APK with production key
- [ ] Create git tag (e.g., `v1.1.1`)
- [ ] Create GitHub release with signed APK attached

## 🚀 F-Droid Submission
- [ ] Created GitLab account
- [ ] Cloned fdroiddata repository
- [ ] Created branch `com.huntercoles.pokerpayout`
- [ ] Copied metadata files to fdroiddata
- [ ] Pushed to GitLab and verified build passes
- [ ] Created RFP issue on https://gitlab.com/fdroid/rfp
- [ ] Created merge request on fdroiddata

## 📝 Current Status

**Ready for:**
1. Adding image assets (icon, feature graphic, screenshots)
2. Setting up production signing
3. Creating GitHub release
4. F-Droid submission

**See `docs/FDROID_SUBMISSION.md` for detailed instructions.**
