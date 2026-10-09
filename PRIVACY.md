# Privacy

Short version: **Poker Payout keeps everything on your phone and sends nothing anywhere.** It has
no ads, no accounts, no analytics and no crash reporting, and it doesn't have Android's internet
permission, so it can't go online at all.

## What the app saves

Only what you enter or choose, in the app's own private storage on your phone:

- **The tournament:** players, buy-in, bounty, rebuys, add-ons and food; the blind settings; where
  the clock is.
- **The bank:** player names you type, who has paid what, knockouts and bounties, payouts, and
  cash game buy-ins and chip counts.
- **Presets** you save, and your **chip set**.
- **History:** the nights you choose to save, with names, places, amounts and season points.
- **Settings:** sound, vibration and flash, payout choices, the four-colour deck, and similar.

Other apps can't read this storage. Uninstalling Poker Payout, or clearing its storage in
Android's settings, deletes all of it.

## When something leaves the app

Only when you ask:

- **Share** (payouts, the settle-up, the setup, seats, a night) and **Export as CSV** (History)
  open Android's share sheet. You pick the app it goes to, and it gets that text and nothing else.
- **Your phone's own backup.** Like most apps, Poker Payout lets Android include its saved data in
  your phone's backup and in phone-to-phone transfers. That's done by Android, under your phone's
  backup settings, not by the app. If backup is off, nothing is copied.

## Permissions

| Permission | Why |
|---|---|
| Notifications | The running clock in the notifications and on the lock screen. Android 13 and up asks once, at the first Start; say no and the clock still works in the app. |
| Foreground service | Keeps the clock and its level-change cues on time while the app is in the background. Only while the clock runs. |
| Keep awake | So a level-change cue isn't late while the screen is off. Only while the clock runs. |
| Vibrate | The optional vibration at each new level. |

Not on the list: internet, location, contacts, camera, microphone, storage.

The live clock notification shows the level, the time left and the blinds, so anyone who can see
your lock screen can see those while the clock runs.

## Checking it yourself

The app is open source, so all of this can be checked in the code. The permissions are in the
`AndroidManifest.xml` files, and the release script refuses to publish an APK that asks for
internet access. F-Droid builds each release from source on its own servers and confirms it matches
the APK published here.

## Questions

Open an [issue](https://github.com/HunterColes/PokerPayout/issues), or write to the maintainer at
the address in [SECURITY.md](SECURITY.md).
