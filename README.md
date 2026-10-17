# NIFTY Option OI Profile — Android source

This is a free Android WebView starter with:
- Candlestick chart using TradingView Lightweight Charts
- Daily, weekly and monthly VWAP overlays on the same chart (calculated from candle OHLCV typical price × volume)
- Previous day OHLC, previous week high/low, previous month high/low level toggles
- Side-by-side premium OI histogram panel with rows aligned to the chart's premium scale
- Dhan API setup guide and an Actions workflow to compile a debug APK

## Important implementation status
The chart and VWAP/profile rendering are implemented as a demonstration with sample candles and sample profile data. **The Dhan live-data adapter is not yet implemented**, so the app does not currently provide real-time data. Do not use demo values for trading decisions.

## Dhan APIs to implement
Official docs:
- Live feed: https://dhanhq.co/docs/v2/live-market-feed/
- Option chain + expiry list: https://dhanhq.co/docs/v2/option-chain/
- Historical/intraday candles: https://dhanhq.co/docs/v2/historical-data/
- Instrument master: https://dhanhq.co/docs/v2/instruments/
- API annexure/segment enums: https://dhanhq.co/docs/v2/annexure/

The live-feed endpoint documented by Dhan is `wss://api-feed.dhan.co?version=2&token=...&clientId=...&authType=2`. Dhan's market feed responses are binary and little-endian; the adapter must parse the documented packet headers and packet types. Do not assume WebSocket messages are JSON.

REST endpoints:
- `POST https://api.dhan.co/v2/optionchain`
- `POST https://api.dhan.co/v2/optionchain/expirylist`
- `POST https://api.dhan.co/v2/charts/intraday`
- `POST https://api.dhan.co/v2/charts/historical`

Option-chain requests are limited to one unique request every 3 seconds. Implement throttling and backoff.

## Credentials
Never commit a Dhan access token, API secret, or GitHub token to source control. This scaffold intentionally does not ask for credentials or connect to Dhan. Before enabling live access, implement Android encrypted storage or a backend secret store, validate auth, handle expiry, and test the exact packet format. A public repository must never contain credentials.

## Build APK from an Android phone
1. Upload the project contents into your GitHub repository preserving the folders.
2. Open Actions and enable workflows if prompted.
3. Run `Build Android APK`.
4. Download artifact `nifty-oi-profile-debug-apk` from the completed run.
5. Extract the ZIP and install the APK.

## Chart/profile interpretation
The premium OI profile groups *observed OI snapshots* into premium buckets. Aggregate OI does not disclose the exact premium at which current holders opened their positions. The app must label this as a premium-time association, not as true holder cost basis. ΔOI should be computed from timestamped snapshots and should not repeatedly count unchanged readings.
