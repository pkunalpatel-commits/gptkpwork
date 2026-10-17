# NIFTY Option OI Profile — Dhan v2 integration source

This version adds a native Android networking bridge for Dhan v2 REST APIs and WebSocket. It is designed to receive real data after the user enters their own Dhan client ID and access token in the app.

## Included live integration
- Dhan v2 expiry-list API for NIFTY (`UnderlyingScrip=13`, `UnderlyingSeg=IDX_I`)
- Dhan v2 option-chain API; select expiry, CE/PE and strike from returned contracts
- Dhan v2 intraday candle API for selected option contract
- Dhan v2 WebSocket quote subscription and little-endian binary packet parsing for LTP and OI
- Live LTP updates on chart and an OI/premium snapshot profile
- Daily/weekly/monthly VWAP overlays calculated from loaded candle volume and typical price
- Previous-day OHLC and previous-week/month high/low levels where enough loaded candle data is available
- GitHub Actions debug APK build

## Dhan API documentation
- Live Market Feed: https://dhanhq.co/docs/v2/live-market-feed/
- Option Chain: https://dhanhq.co/docs/v2/option-chain/
- Historical Data: https://dhanhq.co/docs/v2/historical-data/
- Annexure / request codes: https://dhanhq.co/docs/v2/annexure/

## Important
This source has been wired to the documented Dhan v2 endpoints, but **has not been tested with your account credentials or against a live exchange session in this environment**. You must enter your credentials in the installed app and verify that authentication succeeds. Do not rely on the app for trading until you confirm prices, candles, OI and timestamps against Dhan's own platform.

## Credentials and security
- Enter your Dhan Client ID and access token inside the app. They are held in app memory only and are not written to source files or persisted to disk by this implementation.
- Do not paste credentials into chat, GitHub issues, README, source code, or workflow secrets for this direct-device design.
- Access tokens expire according to Dhan's current authentication policy; re-enter a fresh token when required.
- Keep the repository private if you do not want to publish your app source.
- This app is market-data-only and does not place orders.

## Premium OI profile interpretation
The profile accumulates timestamped observed OI against the premium observed at those timestamps. This is a **premium-time association**, not the true entry-price/cost-basis distribution of current holders. ΔOI is calculated from successive OI snapshots and must not be interpreted as trade direction on its own.

## Build from Android
Upload all files to your GitHub repository with paths intact, then open Actions → Build Android APK → Run workflow. Download the `nifty-oi-profile-debug-apk` artifact from the successful run. A successful compile does not itself prove Dhan authentication; test live data in the app.
