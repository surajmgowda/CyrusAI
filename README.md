# Cyrus — Hybrid Personal AI Assistant (Android 17+)

Cyrus is a native Android 17+ (`minSdk = 35`) Kotlin + Jetpack Compose personal AI assistant with an optional Python FastAPI backend, built from the OpenJarvis SRS & Development Plan.

## Key Capabilities
- **Hybrid AI Routing (`Local` / `Cloud` / `Automatic`)**: Visible route badge, strict local-only policy toggle, and graceful offline handling.
- **Persistent Chat & Voice**: Multi-conversation Room persistence, search/rename/archive/delete, tap-to-talk speech input, and TTS playback.
- **Agents & Human-in-the-Loop Approvals**: Bounded multi-step plans, 4-tier tool permission classes (`READ_ONLY`, `REVERSIBLE_WRITE`, `EXTERNAL_SIDE_EFFECT`, `HIGH_IMPACT`), pause/resume/cancel, and audit trail.
- **Memory & Document Indexing**: Searchable, user-approved long-term memory and Storage Access Framework (SAF) document indexing with provenance.
- **Research, Automation, Developer Studio & Integrations**: Full 8-screen Material 3 navigation plus an internal 81-row capability validation register.
- **Security**: Android Keystore-backed AES-GCM `SecretVault` for API keys.

## Push to GitHub for Automatic APK Download
1. Unzip `Cyrus-Android17.zip` and open a terminal inside the `Cyrus` folder.
2. Create an empty repository on GitHub, then run:
   ```bash
   git init
   git add .
   git commit -m "Initial Cyrus Android 17+ app"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git push -u origin main
   ```
3. Open the **Actions** tab in your GitHub repository. The `Build Cyrus Android 17+ APK` workflow will automatically build and attach `Cyrus-Android17-debug-apk` for download.

## Build Locally in Android Studio
1. Open the `Cyrus/` folder in Android Studio (Ladybug or newer with Android 15 / API 37 SDK installed).
2. Let Gradle sync, then run on an Android 17+ device or emulator.

## Optional Python FastAPI Backend
```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload
```
