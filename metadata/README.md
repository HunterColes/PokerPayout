# F-Droid Metadata

This directory contains all metadata files required for F-Droid submission.

## Directory Structure

```
metadata/
├── CHECKLIST.md                          # Pre-submission checklist
├── com.huntercoles.pokerpayout.yml       # Main metadata file for F-Droid
└── en-US/                                # Localized content (English-US)
    ├── README.md                         # Instructions for adding images
    ├── title.txt                         # App title
    ├── short_description.txt             # Short description (max 80 chars)
    ├── full_description.txt              # Full app description
    ├── icon.png                          # TODO: 512x512 app icon
    ├── featureGraphic.png                # TODO: 1024x500 banner image
    ├── phoneScreenshots/                 # ✅ COPIED: 4 app screenshots (config, bank, timer, odds)
    └── changelogs/                       # TODO: Version changelogs (future)
        └── {versionCode}.txt
```

## Quick Start

### 1. Add Required Images

Before F-Droid submission, add these images to `en-US/`:

- **icon.png** (512x512) - Your app icon
- **featureGraphic.png** (1024x500) - Banner for store listing  
- **phoneScreenshots/** - 3-5 screenshots of your app

### 2. Review Metadata YAML

Edit `com.huntercoles.pokerpayout.yml` and verify:
- Author information
- Repository URLs
- Binary download URL pattern
- Build configuration
- Signing key fingerprint (use production key!)

### 3. Follow Submission Guide

See `../docs/FDROID_SUBMISSION.md` for complete step-by-step instructions.

## Files Explained

### `com.huntercoles.pokerpayout.yml`
The main metadata file that F-Droid uses to:
- Build your app from source
- Verify reproducible builds
- Display app information in the F-Droid catalog
- Auto-update when new versions are released

### `en-US/` Directory
Contains all user-facing content that appears in the F-Droid app listing:
- **title.txt**: App name displayed in F-Droid
- **short_description.txt**: Tagline (max 80 characters)
- **full_description.txt**: Detailed description with features
- **icon.png**: App icon shown in store
- **featureGraphic.png**: Banner image at top of listing
- **phoneScreenshots/**: Screenshots displayed in listing

### `CHECKLIST.md`
A quick checklist to track your F-Droid submission progress.

## Important Notes

⚠️ **Production Signing Required**
- The current `AllowedAPKSigningKeys` uses the **debug keystore**
- You must create and use a production keystore before submission
- See `../docs/FDROID_SUBMISSION.md` for instructions

📦 **GitHub Release Required**
- F-Droid downloads your signed APK from GitHub releases
- The `Binaries` field in the YAML points to your release URL
- Tag format: `v{versionName}` (e.g., `v1.1.0`)
- APK filename: `PokerPayout-v{versionName}-release.apk`

🔄 **Future Updates**
- Create changelogs in `en-US/changelogs/{versionCode}.txt`
- F-Droid auto-detects new tags if `AutoUpdateMode: Version` is set
- Updates typically appear in F-Droid within a few days

## Resources

- [F-Droid Submission Guide](../docs/FDROID_SUBMISSION.md)
- [F-Droid Official Docs](https://f-droid.org/en/docs/)
- [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)
