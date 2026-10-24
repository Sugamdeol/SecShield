# SecShield

An on-device Android security audit. It scans the apps on your phone,
scores what they can do, and shows you the risks in plain language. No
internet permission, no account, no cloud. Nothing leaves the phone, because
nothing can: the app cannot reach the network at all.

Package: `in.sugam.secshield` · version 2.4 (versionCode 11) · minSdk 24,
targetSdk 33 · Kotlin, no Gradle (see BUILD.md).

## What it does

A scan runs 22 checks and produces one score plus a list of what failed,
what warned, and what is safe. Everything below runs on the phone itself.

- **Capability scoring.** Every installed app is scored on what it can
  actually do: permissions, combinations (camera + mic, location + mic,
  SMS + contacts), background behaviour, sideloading, and more.
- **Known-bad app check.** A bundled offline list of 12 package names
  reported in public disclosures (Joker family, Pradeo, Sep 2020; SpyLoan
  family, McAfee, Dec 2023) is matched against installed apps. A match is
  worded exactly as "matches a public offline list of reported-bad
  packages", never as confirmed malware, and it can be dismissed with
  "Mark as trusted". The list is frozen in the app and never updates over
  the network.
- **Scam advisor.** Paste a link or a message and offline heuristics flag
  URL shorteners, raw-IP links, APK download links, lookalike brand
  domains, urgency pressure, and OTP/password requests. It is labelled on
  screen as a heuristic check only, not a verdict.
- **Wi-Fi audit.** Open networks, WEP, and VPN status, checked on-device.
- **Play Protect card.** Explains that Play Protect is the free built-in
  baseline and what SecShield adds on top of it. The button to security
  settings appears only when that screen actually resolves on the phone;
  otherwise the card is guidance text only, never a dead button.
- **Install verdicts.** Every newly installed app gets a verdict
  notification.
- **History and trend.** Past scores with deltas, plus a trend view of
  first, latest, and best scores once at least two scans are recorded.
- **Daily scan.** A scan runs automatically at 09:00, re-armed on boot,
  and notifies only on a critical issue or a 10+ point drop.
- **Report export.** Share text or a PDF report including reported-bad
  matches, the score trend, and the top 3 risks.
- **Home widget.** The live score on the home screen.
- **Per-app detail.** Trust score, risky permissions, and a guided fix
  that opens the right system screen. Android does not let one app revoke
  another app's permissions, so "Revoke" opens the app info screen where
  the user revokes them.
- **Apps search and filters.** Search, filter by risk level, and a Privacy
  Combinations section.

The design is white surfaces, near-black text, a lime accent, and a bottom
nav of Shield / Apps / History / Guard. The look is deliberately calm:
a security tool should not look like an alarm.

## What it is not

SecShield is not an antivirus and does not replace Play Protect. It has no
cloud signature database, no VPN, no anti-theft, no breach or dark-web
lookups, no app lock, no photo vault, no junk cleaner, and no call
blocker. Those need accounts and server infrastructure, or permissions
that do not belong in a scanner. The competitive comparison behind v2.4
was done from public sources only (store listings, vendor pages,
AV-TEST/AV-Comparatives results, reviews). No competitor app was
downloaded, decompiled, or inspected.

## How to build

See BUILD.md. In short: Android build-tools 34 (`aapt2`), Kotlin 2.1.0,
kotlinx-coroutines 1.8.1 (core + android jars), `d8`, `zipalign`,
`apksigner`. No Gradle, no Android Studio project files.

Two things are not in this repo and are needed before linking:

- **Type.** The app loads Inter Regular + Bold from `assets/fonts/`
  (OFL, free from Google Fonts). Drop both files there to match the
  shipped look. Without them it falls back to the system typeface; it
  still builds and runs.
- **Launcher icon.** The manifest references `@mipmap/ic_launcher`.
  Place an `ic_launcher.png` in each of `res/mipmap-mdpi` (48 px),
  `res/mipmap-hdpi` (72 px), `res/mipmap-xhdpi` (96 px),
  `res/mipmap-xxhdpi` (144 px), and `res/mipmap-xxxhdpi` (192 px)
  before linking, or the link step fails on the missing resource.

Release signing needs the developer's own keystore, which is not in this
repo. An unsigned or debug build works without it.

## Status

v2.4 published 2026-10-24. Device testing is ongoing, and this README
does not claim the app is device-tested. If you build it and find a
problem on a real phone, that report is worth more than any build log.

## License

MIT. See LICENSE.
