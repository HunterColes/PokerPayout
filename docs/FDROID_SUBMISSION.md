# F-Droid Submission Guide for Poker Payout Calculator

This guide will walk you through submitting Poker Payout Calculator to F-Droid.

## ✅ Completed Steps

1. **Dependency Metadata Disabled** - Added `dependenciesInfo` block to `app/build.gradle.kts`
2. **Metadata Created** - Created `metadata/en-US/` directory with required text files
3. **Release APK Built** - Built release APK: `app/build/outputs/apk/release/PokerPayout-v1.1.0-release.apk`
4. **Metadata YAML Created** - Created `metadata/com.huntercoles.pokerpayout.yml`

## 🎨 TODO: Add Required Images

Before submitting, you need to add these images to `metadata/en-US/`:

### 1. App Icon (`icon.png` - 512x512)
- Export your app icon at 512x512 resolution
- Source: Use your original icon artwork or export from `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` and resize

### 2. Feature Graphic (`featureGraphic.png` - 1024x500)
- Create a banner image showcasing your app
- Should be visually appealing and represent the poker theme
- Displays at the top of your F-Droid listing

### 3. Screenshots (`phoneScreenshots/` directory)
- Add 3-5 screenshots showing key features:
  - Payout calculator in action
  - Bank tracker with player list
  - Blind timer running
  - Tools page
- Any resolution is acceptable

## ⚠️ IMPORTANT: Production Signing Key

**Current Status**: The app is signed with the **debug keystore**.

For F-Droid submission, you should:
1. Create a production keystore (if you don't have one)
2. Configure release signing in `app/build.gradle.kts`
3. Rebuild and get the new SHA-256 fingerprint
4. Update the `AllowedAPKSigningKeys` field in `metadata/com.huntercoles.pokerpayout.yml`

### Creating a Production Keystore

```bash
keytool -genkey -v -keystore pokerpayout-release.keystore -alias pokerpayout -keyalg RSA -keysize 2048 -validity 10000
```

**IMPORTANT**: Store this keystore securely and back it up. You'll need it for all future releases.

### Configure Release Signing

Add to `app/build.gradle.kts` before the `buildTypes` block:

```kotlin
signingConfigs {
    create("release") {
        // Option 1: Store in gradle.properties (recommended)
        storeFile = file(project.findProperty("RELEASE_STORE_FILE") ?: "release.keystore")
        storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
        keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
        keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
    }
}
```

Then update the release build type:
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")  // Change this line
    }
}
```

Add to your `gradle.properties` (don't commit these values!):
```properties
RELEASE_STORE_FILE=path/to/pokerpayout-release.keystore
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=pokerpayout
RELEASE_KEY_PASSWORD=your_key_password
```

## 📦 Next Steps: GitHub Release

### 1. Create a Git Tag
```bash
git tag -a v1.1.1 -m "Release version 1.1.1"
git push origin v1.1.1
```

### 2. Create GitHub Release
1. Go to https://github.com/HunterColes/PokerPayout/releases/new
2. Choose the tag `v1.1.1`
3. Title: `v1.1.1 - [Brief description]`
4. Add release notes describing changes
5. Upload the APK: `app/build/outputs/apk/release/PokerPayout-v1.1.1-release.apk`
6. Publish the release

**Important**: The APK filename should match the pattern in the metadata YAML file:
- Current pattern: `PokerPayout-v%v-release.apk`
- For v1.1.1: `PokerPayout-v1.1.1-release.apk`

## 🔧 F-Droid Repository Setup

### 1. Create GitLab Account
- Sign up at https://gitlab.com/users/sign_in#register-pane

### 2. Clone F-Droid Data Repository
```bash
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
```

### 3. Create Your Branch
```bash
git checkout -b com.huntercoles.pokerpayout
```

### 4. Copy Your Metadata Files
```bash
# Copy the YAML file
cp /path/to/PokerPayout/metadata/com.huntercoles.pokerpayout.yml metadata/

# Copy the en-US directory
cp -r /path/to/PokerPayout/metadata/en-US metadata/com.huntercoles.pokerpayout/en-US
```

### 5. Test Your Build
```bash
git add metadata/com.huntercoles.pokerpayout.yml
git add metadata/com.huntercoles.pokerpayout/
git commit -m "Add Poker Payout Calculator"
git push origin com.huntercoles.pokerpayout
```

This will trigger a build in GitLab CI/CD. Check the pipeline status at:
- Build → Pipelines

### Common Build Issues:

1. **Metadata Linting Failure**
   - Properties must be in a specific order
   - Check the build output for the expected order

2. **Version Mismatch**
   - Ensure git tag exists
   - Verify version in `app/build.gradle.kts` matches metadata YAML

3. **APK Integrity Check Failed**
   - The built APK doesn't match your uploaded APK
   - Ensure reproducible builds are configured correctly
   - Check that all dependencies are specified correctly

## 📝 Create F-Droid Submission

### 1. Create Request for Packaging (RFP) Issue
1. Go to https://gitlab.com/fdroid/rfp/-/issues
2. Click "New Issue"
3. Fill in:
   - Title: `Poker Payout Calculator`
   - Description: Brief description and link to your app
   - Include GitHub repo: https://github.com/HunterColes/PokerPayout

### 2. Create Merge Request
1. Go to your fdroiddata fork
2. Create merge request from your branch `com.huntercoles.pokerpayout`
3. Title: `New app: Poker Payout Calculator`
4. Description: Link to the RFP issue you created
5. Submit the merge request

### 3. Wait for Review
- F-Droid team will review your submission
- They may request changes
- Once approved, your app will be published!

## 🔄 Future Updates

When you release a new version:

1. **Update Version**
   ```kotlin
   // In app/build.gradle.kts
   versionCode = 14
   versionName = "1.1.2"
   ```

2. **Create Changelog**
   ```bash
   # Create file: metadata/en-US/changelogs/14.txt
   echo "- Fixed bugs\n- Added new features" > metadata/en-US/changelogs/14.txt
   ```

3. **Tag and Release on GitHub**
   ```bash
   git tag -a v1.1.2 -m "Release version 1.1.2"
   git push origin v1.1.2
   ```
   Create GitHub release with new APK

4. **F-Droid Auto-Update**
   - F-Droid will automatically detect your new release
   - May take a few days to appear in F-Droid
   - If configured correctly with `AutoUpdateMode: Version`

## 📚 Helpful Resources

- [F-Droid Official Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/)
- [Full Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)

## ✨ Benefits of F-Droid

- **More Exposure**: Smaller catalog means your app stands out
- **FLOSS Community**: Support open-source software
- **No Fees**: Unlike Google Play Store
- **Privacy Focused**: Users who care about privacy use F-Droid
- **Donation Links**: Include cryptocurrency donation links in your listing
- **Learning Experience**: Strict requirements improve code quality

---

**Current Status**: ✅ Ready for image assets and production signing setup
