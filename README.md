# Relay Coordination Test App

Small Android app for checking time-grade coordination of downstream/upstream
overcurrent protection relays. Built for a 480x640 phone screen as three pages.

## Pages

1. **Downstream relay** — curve selector, pickup current `Is` (A), time multiplier
   setting (TMS), definite time delay (s), and a live trip-time preview at 3x Is.
2. **Upstream relay** — same inputs.
3. **Curves** — log-log plot (X = current in A, Y = time in s) of both relays and a
   bidirectional solver:
   - enter current -> trip time of each relay, which trips first, coordination margin
   - enter time -> current at which each relay trips (and pickup multiple)
   - tap the plot to pick a current

Settings persist in SharedPreferences.

## Curves

- IEC 60255-151: standard, very, extremely, long-time inverse
  `t = TMS * K / ((I/Is)^alpha - 1) + delay`
- IEEE C37.112: moderately, very, extremely inverse
  `t = TMS * A / ((I/Is)^p - B) + delay`
- Definite time: `t = delay` for `I > Is`

The inverse function is analytic, so entering a time gives the corresponding
current directly rather than by numerical search.

## Build

`./build.sh` compiles resources, Java sources and DEX, then signs the APK.
It reads the toolchain from environment variables (defaults below are the paths
used on the build machine):

- `JDK` — JDK 21 (`/tmp/opencode/android/jdk`)
- `BT` — Android build-tools with `aapt2`, `zipalign`, `apksigner`
- `AJ` — `android.jar` from platform API 30
- `R8` — current R8 jar (bundled D8 in old build-tools fails with modern javac)

Output: `build/app.apk`.

## Install

Normal devices: `adb install -r build/app.apk`.

On the F21 Pro the ROM blocks the `pm` shell command, so the APK is installed as
a system app through a minimal Magisk module at
`/data/adb/modules/relaycoord/system/priv-app/RelayCoord/RelayCoord.apk`, followed
by a reboot. `tools/Installer.java` is an experimental PackageInstaller-based
alternative.
