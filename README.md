<p align="center">
  <img src="TMessagesProj/src/main/res/drawable-nodpi/bygram_user_badge.png" width="96" alt="BYgram">
</p>

<h1 align="center">BYgram Android</h1>

BYgram — клиент Telegram для Android на основе [exteraGram](https://github.com/exteraSquad/exteraGram).
Документация Python SDK находится в [plugins-pysdk](plugins-pysdk/README.md).

[Документация BYgram](docs/readme.md) · [История изменений](changelog.md) · [Канал BYgram](https://t.me/BYgram_official)

## Editions

| Flavor | What is inside                                              | ABIs                                  |
|--------|-------------------------------------------------------------|---------------------------------------|
| `full` | Everything, including the Python runtime and plugins        | `armeabi-v7a`, `arm64-v8a`            |
| `lite` | The same client without Python, plugins and method hooking  | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |

The second flavor dimension picks the ABI: `armv7`, `arm64`, `x86`, `x64`, or `afat` for a universal
ARM build. `beta` is a universal ARM build with beta naming.

## Building

### Requirements

- Android Studio with support for Android Gradle Plugin 9.1, or just JDK 17 and the command line
- Android SDK 37, Build Tools 36.0.0, NDK 29.0.14206865 and CMake 4.x
- Python 3.11 for the `full` flavor, on `PATH` as `python3.11` or set as `python.build` in
  `local.properties`

### Steps

1. Clone the repository with its submodules:
   ```bash
   git clone --branch bygram/exteragram-base --recursive --shallow-submodules https://github.com/BYsquad/exteraGram.git
   ```
   If you cloned without `--recursive`:
   ```bash
   git submodule update --init --recursive --depth=1
   ```
2. Create a file named `API_KEYS` in the repository root:
   ```
   APP_ID = 123456
   APP_HASH = "abcdef0123456789abcdef0123456789"
   MAPS_V2_API = your-google-maps-key

   SIGNING_KEY_PASSWORD = key-password
   SIGNING_KEY_ALIAS = key-alias
   SIGNING_KEY_STORE_PASSWORD = keystore-password
   ```
   Get your own `APP_ID` and `APP_HASH` at <https://core.telegram.org/api/obtaining_api_id> and a
   Maps key in the [Google Cloud console](https://console.cloud.google.com/google/maps-apis/credentials).
   Do not ship builds with someone else's keys.
3. [Generate a signing key](https://developer.android.com/studio/publish/app-signing#generate-key)
   and save the keystore as `TMessagesProj/config/release.keystore`. Debug builds are signed with it
   too. To update an existing BYgram installation, use the signing key of that installation.
4. Build one universal Full debug APK for `armeabi-v7a` and `arm64-v8a`:
   ```bash
   ./gradlew :TMessagesProj:assembleFullAfatDebug
   ```
   The APK appears as `BYgram-full-universal-YYYYMMDD.apk` in
   `TMessagesProj/build/outputs/apk/fullAfat/debug/`. Other variants follow the same pattern:
   `assembleLiteArm64Release`, `assembleFullAfatRelease`, `assembleFullBetaDebug`.

A `full` build compiles the plugins SDK from `plugins-pysdk/` and downloads a prebuilt plugin
analyzer from GitHub. Gradle uses the analyzer cached in `Tools/temp` for offline builds.

On Windows with Java on `PATH`, run the wrapper with PowerShell.

```powershell
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj:assembleFullAfatDebug
```

Set `python.build` in `local.properties` if the default launcher does not find Python 3.11.
The analyzer downloader accepts `GH_TOKEN` when GitHub rate-limits unauthenticated downloads.

## Translations

Telegram's own strings come from the
[official translations](https://translations.telegram.org/en/android/). Strings that exist only in
exteraGram are translated on [Crowdin](https://crowdin.com/project/exteralocales).
BYgram strings are maintained alongside them in the resource files for
[English](TMessagesProj/src/main/res/values/extera.xml),
[Russian](TMessagesProj/src/main/res/values-ru/extera.xml) and
[Ukrainian](TMessagesProj/src/main/res/values-uk/extera.xml).

## Thanks

- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [Telegram for Android](https://github.com/DrKLO/Telegram)
- [Catogram](https://github.com/Catogram/Catogram)
- [Nekogram](https://gitlab.com/Nekogram/Nekogram)
- [OwlGram](https://github.com/OwlGramDev/OwlGram)

## License

[GNU General Public License v3.0 or later](LICENSE), with [additional terms](ADDITIONAL_TERMS.md)
about attribution and the use of the exteraGram name and logo.
