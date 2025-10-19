# F-Droid Metadata Assets

This directory contains the required metadata assets for F-Droid listing.

## Required Files

### Images
- **icon.png** (512x512) - App icon for F-Droid store listing ✅ **COPIED** from `ic_launcher-playstore.png`
- **featureGraphic.png** (1024x500) - Feature graphic banner
- **phoneScreenshots/** - Directory containing app screenshots ✅ **COPIED** 4 screenshots from docs/

### Text Files
- **title.txt** - App title ✅ **UPDATED** to "Poker Payout"
- **short_description.txt** - Short description (max 80 chars) ✅ **GOOD** from README
- **full_description.txt** - Full app description ✅ **GOOD** from README

## TODO: Add Images

You need to add the following image files to this directory:

1. **icon.png** - Export your app icon at 512x512 resolution
2. **featureGraphic.png** - Create a banner image at 1024x500 resolution
3. Add screenshots to **phoneScreenshots/** directory

### Getting Your App Icon
You can export your app icon from:
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (resize to 512x512)
- Or use your original icon source file

### Creating Feature Graphic
Create a 1024x500 banner image that represents your app. This appears at the top of your F-Droid listing.

### Adding Screenshots
Add screenshots of your app to the `phoneScreenshots/` directory. These will be displayed in your F-Droid listing.
