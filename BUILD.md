# Building SecShield

No Gradle, no Android Studio project files. The pipeline is a small set of
command-line tools, in this order:

## Tools

- `aapt2` from Android build-tools 34 (resource compile + link)
- `kotlinc` 2.1.0
- kotlinx-coroutines 1.8.1: the core jar and the android jar, both on the
  compiler classpath and both passed to the dexer (the android jar is what
  provides the Main dispatcher; leaving it out crashes on launch)
- `d8` (dexing)
- `zipalign` and `apksigner` (packaging and signing)

## Steps

1. Compile resources with `aapt2 compile`, then link with `aapt2 link`
   against the Android platform jar. Linking generates `R.java` and the
   base APK.
2. Compile `R.java` with `javac`.
3. Compile the Kotlin sources under `src/` with `kotlinc`, with the
   coroutines core and android jars on the classpath. Note the package
   name segment `in` is a Kotlin keyword, so it is backtick-escaped in
   source.
4. Dex the compiled classes with `d8`, including the coroutines jars.
   `d8` prints Info-level noise about malformed `kotlin.Metadata`;
   dexing still succeeds.
5. Add the dex output to the APK, then `zipalign` it.
6. Sign with `apksigner`.

## Before linking

Two resources are not in this repo and must be added first:

- **Type.** Drop Inter Regular + Bold (OFL, free from Google Fonts)
  into `assets/fonts/` to match the shipped look. Without them the app
  falls back to the system typeface.
- **Launcher icon.** Place an `ic_launcher.png` in each of
  `res/mipmap-mdpi` (48 px), `res/mipmap-hdpi` (72 px),
  `res/mipmap-xhdpi` (96 px), `res/mipmap-xxhdpi` (144 px), and
  `res/mipmap-xxxhdpi` (192 px). The manifest references
  `@mipmap/ic_launcher`, so linking fails without these files.

## Signing

Release signing needs the developer's own keystore, which is not included
in this repo and is not needed to build. An unsigned or debug-signed
build works without it; only a release install that must match the
published signing certificate needs the original keystore.

## Notes

- SharedPreferences keys are stable across versions, so history, toggles,
  and trusted apps survive an update install.
- The manifest declares no internet permission. Keep it that way: adding
  one would break the app's core privacy promise.
