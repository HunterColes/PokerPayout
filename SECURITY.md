# Security

## Reporting a problem

If you find a security problem, please tell us privately rather than in a public issue:
email **hunter.colesw@gmail.com** with "Poker Payout security" in the subject. Say what you found,
the app version (at the bottom of the Tools tab) and how to reproduce it.

We'll reply as soon as we can, work on a fix with you, and credit you in the release notes if
you'd like.

## Which versions get fixes

Only the latest release, on [GitHub](https://github.com/HunterColes/PokerPayout/releases/latest)
and [F-Droid](https://f-droid.org/packages/com.huntercoles.pokerpayout/). Fixes ship as a new
release; please update.

## What's in scope

Poker Payout has no internet permission, no accounts and no server, so the things that matter are
on the phone and in how the app is built and published. For example:

- another app reading or changing Poker Payout's saved data;
- the notification, its buttons or any other part of the app being driven by another app;
- the release chain: the signing key, the APKs on GitHub, or F-Droid's reproducible build.

## Checking a download

Releases are signed with this certificate (SHA-256):

```
223c397a82d6ed7b50659d30ed907ed23b200509b84fdcf8412f4ed859db35c0
```

To check an APK before installing it: `apksigner verify --print-certs PokerPayout-v….apk` and
compare the "SHA-256 digest" line. Each release's notes on GitHub repeat the certificate and give
the APK's own SHA-256. From 1.2.0 on, F-Droid publishes the same signed APK. Versions 1.1.11 and
1.1.12 were signed with an older key, which is retired.
