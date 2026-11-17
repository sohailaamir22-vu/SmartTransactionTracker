# Smart Transaction Tracker

Android app (Kotlin, Jetpack Compose + Material 3, MVVM, Room, ML Kit OCR, Photo Picker).
Works offline. Amounts are shown in rupees (Rs).

## Features
- Manual entry: amount, optional note, auto date/time, unique ID (`TXN-YYYYMMDD-XXXXXX`), edit/delete
- Screenshot import (1 to 100 at a time): offline OCR reads amount + date + time, you review/correct in a table, then save all together
  - no date on the screenshot -> upload time is used
  - same screenshot can never be added twice (SHA-256 check)
  - low-confidence / missing amounts are flagged in the review list
- Live totals: manual, screenshot, grand total, today, this month, daily / weekly / monthly
- History table: all 8 columns, sort by date/time, search, date-range filter, screenshot preview
- Dashboard cards + charts, light/dark mode
- Export XLSX, CSV, PDF report; backup / restore (.zip with database + screenshots)

## Get the APK (pick ONE way)

### A) GitHub (no Android Studio needed)
1. Create a new repository on github.com and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build APK** -> wait ~5-8 minutes.
3. Open the finished run -> **Artifacts** -> download `SmartTransactionTracker-apk` -> unzip -> `app-release.apk`.

### B) Android Studio
1. Install Android Studio (Koala or newer) and open this folder.
2. Wait for Gradle sync, then **Build > Build Bundle(s) / APK(s) > Build APK(s)**,
   or in a terminal: `./gradlew assembleRelease`
3. APK: `app/build/outputs/apk/release/app-release.apk`

## Install on the phone
1. Copy `app-release.apk` to the phone.
2. Open it; when asked, allow "Install unknown apps" for your file manager / browser.
3. Install and open **Smart Transaction Tracker**. No permissions are required.

## Roman Urdu mukhtasar
1. Poora folder GitHub repository mein upload karein (`.github` folder samet).
2. Actions tab > Build APK > run khatam hone par Artifacts se `app-release.apk` download karein.
3. APK phone mein copy karke install karein ("Install unknown apps" allow karna hoga).
4. Screenshots ke liye **Import** tab > "Select transaction screenshots" > amounts check/theek karein > "Save verified transactions".

## Signing key
`keystore/smart-tracker.jks` (password `smarttracker`) is a personal-use key included so every build has the
same signature and a new APK installs over the old one without losing data. Do not use it for Play Store
publishing; create your own key for that and change `signingConfigs` in `app/build.gradle.kts`.

## Notes
- OCR accuracy depends on screenshot quality. The review table exists so you can correct any wrong amount.
- Tip: make a backup (More > Create backup) before uninstalling the app.
