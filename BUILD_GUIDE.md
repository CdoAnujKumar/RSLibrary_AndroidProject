# 📱 RS Library — Build Your APK in 10 Minutes

## What's inside this project
- Full native Android app (WebView-based, like Coursera/PhonePe)
- Your complete library UI (index.html) embedded inside
- Real WiFi Suggestion API — student taps "Connect", system asks permission, connects WITHOUT seeing password
- WiFi forgotten automatically when student leaves membership
- UPI payment deep-links work natively
- Back button navigates inside app (doesn't close it)

---

## Step 1 — Install Android Studio (Free)
Download from: https://developer.android.com/studio
Install with default settings. Takes ~10 minutes.

---

## Step 2 — Get the SHA-1 key for Google Sign-In (IMPORTANT)

Google Sign-In won't work in the APK unless you register your app's SHA-1 fingerprint in Firebase.

After installing Android Studio, open Terminal inside it and run:

**Windows:**
```
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

**Mac/Linux:**
```
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA-1** value that appears.

---

## Step 3 — Add SHA-1 to Firebase Console

1. Go to: https://console.firebase.google.com
2. Select project: **rakshapal-singh-library-ded2e**
3. Click ⚙️ Settings → Project Settings
4. Scroll to "Your apps" → Click "Add fingerprint"
5. Paste your SHA-1 → Save
6. Download the new **google-services.json**
7. Replace the file at: `app/google-services.json`

---

## Step 4 — Open Project in Android Studio

1. Open Android Studio
2. Click **File → Open**
3. Select the **RSLibrary** folder (this folder)
4. Wait for Gradle sync to complete (~2-3 minutes first time)

---

## Step 5 — Build the APK

### For testing (debug APK — install directly on any phone):
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### For release (signed APK for sharing/Play Store):
```
Build → Generate Signed Bundle / APK → APK
```
Create a keystore when prompted (save it safely!).

---

## Step 6 — Install on Android Phone

**Option A — USB:**
1. Enable Developer Options on phone (tap Build Number 7 times)
2. Enable USB Debugging
3. Connect phone via USB
4. In Android Studio: Run → Run 'app'

**Option B — File Transfer:**
1. Copy `app-debug.apk` to your phone
2. Open it → tap Install
3. If blocked: Settings → Security → Allow unknown sources

---

## How WiFi Suggestion Works (for students)

1. Student opens app → logs in → joins library
2. Taps **"🛜 Connect to Internet"**
3. Android shows a **system notification**: *"Connect to LibraryWiFi?"*
4. Student taps **Yes** → phone connects automatically
5. **Password is NEVER shown** — it's fetched from Firebase and passed directly to Android
6. When student exits membership → WiFi is automatically removed from suggestions

**Admin side:** Go to Manage tab → Wi-Fi → Enter your router SSID & password → Save

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Google Sign-In fails | Add SHA-1 to Firebase (Step 3) |
| Gradle sync fails | File → Invalidate Caches → Restart |
| WiFi button shows "Bridge not found" | You're testing in browser, not APK |
| App crashes on launch | Check Logcat for errors |
| Build fails with SDK error | SDK Manager → Install API 34 |

---

## Project Structure
```
RSLibrary/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml       ← permissions
│   │   ├── assets/
│   │   │   └── index.html            ← YOUR FULL APP UI
│   │   ├── java/com/rakshapalsingh/library/
│   │   │   └── MainActivity.java     ← WebView + WiFi bridge
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── drawable/ic_launcher.xml
│   │       └── values/styles.xml
│   ├── build.gradle
│   └── google-services.json          ← REPLACE with yours from Firebase
├── build.gradle
├── settings.gradle
└── gradle.properties
```
