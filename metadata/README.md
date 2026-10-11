# F-Droid metadata

What F-Droid shows for Poker Payout, read from the release tag, and a mirror of its build recipe.

```
metadata/
├── com.huntercoles.pokerpayout.yml       # mirror of fdroiddata's recipe, rebuilt by release.sh
└── en-US/
    ├── title.txt                         # the app's name
    ├── short_description.txt             # the summary: 80 characters at most, no full stop
    ├── full_description.txt              # the feature list
    ├── images/
    │   ├── icon.png                      # 512 x 512
    │   ├── featureGraphic.png            # 1024 x 500: scripts/listing/feature_graphic.py
    │   └── phoneScreenshots/             # 8 pictures: scripts/device/listing.sh (listing.yml)
    └── changelogs/<versionCode>.txt      # What's New, written by release.sh
```

- **Pictures** are made by scripts, never by hand, and carry no metadata (no EXIF, no text
  chunks): see "Refreshing the store listing" in [docs/RELEASING.md](../docs/RELEASING.md#refreshing-the-store-listing).
- **Words:** plain and short, leading with free, no ads, offline and no internet permission. When a
  feature lands or goes, `full_description.txt` changes with it.
- **The recipe** (`com.huntercoles.pokerpayout.yml`) is fdroiddata's file as of the last release,
  plus that release's build entry. `AllowedAPKSigningKeys` pins the release key's certificate (the
  2026 key, since 1.2.0). `Binaries:` points at the GitHub release's APK, which F-Droid compares
  with its own build byte for byte. Tags are `v<versionName>`; the APK is
  `PokerPayout-v<versionName>-release.apk`.
- **Changes to the recipe** go to fdroiddata as a merge request (`scripts/release/fdroid-mr.sh`);
  a new version needs none. See [docs/RELEASING.md](../docs/RELEASING.md).

[docs/FDROID_SUBMISSION.md](../docs/FDROID_SUBMISSION.md) is the guide from the first submission
(2025), kept for history.
