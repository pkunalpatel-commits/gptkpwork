# NIFTY Option OI Profile (Android)

A free, open-source Android WebView app starter for a Dhan-connected NIFTY options chart.

## Included
- Dark Android WebView app shell
- TradingView Lightweight Charts candlestick demo
- Illustrative premium-bucket OI profile
- Daily/weekly VWAP and prior day/week/month level placeholders
- GitHub Actions workflow that builds and uploads a debug APK

## Build from an Android phone
1. Upload these files into your GitHub repository, preserving the directory structure.
2. Open the repository's Actions tab and enable Actions if prompted.
3. Run **Build Android APK**, or push to `main` to trigger it automatically.
4. Open the completed workflow run and download artifact `nifty-oi-profile-debug-apk`.
5. Extract the ZIP and install the APK.

## Important
This initial build is a UI scaffold with sample data. It is **not yet connected to Dhan live data**. The official Dhan WebSocket protocol, instrument identifiers, authentication, and binary packet parsing must be implemented and tested before using live values.

## Security
- Never commit Dhan access tokens, API secrets, or GitHub tokens.
- Do not hard-code secrets in source code or the APK.
- This project does not currently collect or store a Dhan token.
- Lightweight Charts attribution is included in the app.
