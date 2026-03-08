# Android Release Signing

## 1) Generate keystore and key.properties

Run in `mobile` directory:

```powershell
.\scripts\generate_keystore.ps1 -StorePass "your_store_password" -KeyPass "your_key_password"
```

Or from `cmd`:

```bat
scripts\generate_keystore.bat -StorePass "your_store_password" -KeyPass "your_key_password"
```

This creates:

- `android/app/upload-keystore.jks`
- `android/key.properties`

## 2) Build release APK

```powershell
.\scripts\build_release_apk.ps1 -ApiBaseUrl "http://192.168.2.101:8080"
```

Or split per ABI:

```powershell
.\scripts\build_release_apk.ps1 -ApiBaseUrl "http://192.168.2.101:8080" -SplitPerAbi
```

Output directory:

- `build/app/outputs/flutter-apk/`

## Notes

- `android/key.properties` and `*.jks` are ignored by git.
- If release signing is missing, Gradle will stop release build with a clear error.
