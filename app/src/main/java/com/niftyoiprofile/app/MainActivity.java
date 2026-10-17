package com.niftyoiprofile.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends Activity {
    private WebView webView;
    private OkHttpClient http;
    private WebSocket socket;
    private volatile String clientId = "";
    private volatile String accessToken = "";
    private volatile String selectedSecurityId = "";
    private volatile String selectedSegment = "NSE_FNO";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.addJavascriptInterface(new DhanBridge(), "DhanBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void js(String script) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    private void status(String message, boolean ok) {
        try {
            JSONObject o = new JSONObject();
            o.put("message", message);
            o.put("ok", ok);
            js("window.onDhanStatus && window.onDhanStatus(" + o.toString() + ")");
        } catch (Exception ignored) {}
    }

    private Request.Builder apiRequest(String url) {
        return new Request.Builder().url(url)
                .header("access-token", accessToken)
                .header("client-id", clientId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private void postJson(String url, JSONObject body, String callback) {
        Request req = apiRequest(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                js(callback + "(false," + JSONObject.quote(e.getMessage()) + ")");
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String text = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    js(callback + "(false," + JSONObject.quote("HTTP " + response.code() + " " + text) + ")");
                } else {
                    js(callback + "(true," + JSONObject.quote(text) + ")");
                }
            }
        });
    }

    public class DhanBridge {
        @JavascriptInterface
        public void setCredentials(String cid, String token) {
            if (cid == null || token == null || cid.trim().isEmpty() || token.trim().isEmpty()) {
                status("Enter Dhan Client ID and access token.", false);
                return;
            }
            clientId = cid.trim();
            accessToken = token.trim();
            status("Credentials held in memory. Requesting expiry list…", true);
            fetchExpiries();
        }

        @JavascriptInterface
        public void fetchExpiries() {
            if (!ready()) return;
            try {
                JSONObject b = new JSONObject();
                b.put("UnderlyingScrip", 13);
                b.put("UnderlyingSeg", "IDX_I");
                postJson("https://api.dhan.co/v2/optionchain/expirylist", b, "window.onExpiryResponse");
            } catch (Exception e) {
                status("Expiry request error: " + e.getMessage(), false);
            }
        }

        @JavascriptInterface
        public void fetchChain(String expiry) {
            if (!ready()) return;
            try {
                JSONObject b = new JSONObject();
                b.put("UnderlyingScrip", 13);
                b.put("UnderlyingSeg", "IDX_I");
                b.put("Expiry", expiry);
                postJson("https://api.dhan.co/v2/optionchain", b, "window.onChainResponse");
            } catch (Exception e) {
                status("Option-chain request error: " + e.getMessage(), false);
            }
        }

        @JavascriptInterface
        public void loadCandles(String securityId, String interval) {
            if (!ready()) return;
            try {
                selectedSecurityId = securityId;
                JSONObject b = new JSONObject();
                b.put("securityId", securityId);
                b.put("exchangeSegment", "NSE_FNO");
                b.put("instrument", "OPTIDX");
                b.put("interval", interval);
                b.put("oi", true);
                // last ~5 trading sessions worth for profile seed
                long to = System.currentTimeMillis() / 1000;
                long from = to - 5L * 24 * 3600;
                b.put("fromDate", from);
                b.put("toDate", to);
                postJson("https://api.dhan.co/v2/charts/intraday", b, "window.onCandlesResponse");
            } catch (Exception e) {
                status("Candle request error: " + e.getMessage(), false);
            }
        }

        @JavascriptInterface
        public void connectContract(String securityId) {
            if (!ready()) return;
            selectedSecurityId = securityId;
            if (socket != null) {
                socket.close(1000, "switch contract");
                socket = null;
            }
            try {
                String url = "wss://api-feed.dhan.co?version=2&token="
                        + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8)
                        + "&clientId=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
                        + "&authType=2";
                Request request = new Request.Builder().url(url).build();
                socket = http.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket ws, Response response) {
                        try {
                            // Subscribe Full packet (RequestCode 21) for LTP + OI together
                            JSONObject item = new JSONObject();
                            item.put("ExchangeSegment", selectedSegment);
                            item.put("SecurityId", securityId);
                            JSONArray list = new JSONArray();
                            list.put(item);
                            JSONObject sub = new JSONObject();
                            sub.put("RequestCode", 21);
                            sub.put("InstrumentCount", 1);
                            sub.put("InstrumentList", list);
                            ws.send(sub.toString());
                            status("WebSocket connected. Subscribed Full feed for " + securityId, true);
                        } catch (Exception e) {
                            status("Subscription error: " + e.getMessage(), false);
                        }
                    }

                    @Override
                    public void onMessage(WebSocket ws, ByteString bytes) {
                        parsePacket(bytes.toByteArray());
                    }

                    @Override
                    public void onMessage(WebSocket ws, String text) {
                        status("Dhan text: " + text, true);
                    }

                    @Override
                    public void onFailure(WebSocket ws, Throwable t, Response response) {
                        status("WebSocket failed: " + t.getMessage(), false);
                    }

                    @Override
                    public void onClosed(WebSocket ws, int code, String reason) {
                        status("WebSocket closed: " + reason, false);
                    }
                });
            } catch (Exception e) {
                status("Connect error: " + e.getMessage(), false);
            }
        }

        @JavascriptInterface
        public void disconnect() {
            if (socket != null) {
                socket.close(1000, "user disconnect");
                socket = null;
            }
            status("Disconnected.", true);
        }

        private boolean ready() {
            if (clientId.isEmpty() || accessToken.isEmpty()) {
                status("Enter credentials and connect first.", false);
                return false;
            }
            return true;
        }
    }

    /**
     * Dhan v2 binary packets (little-endian).
     * Header (8 bytes): code(u8), length(u16), segment(u8), securityId(u32)
     * Code 2 Ticker: LTP float @8
     * Code 4 Quote: LTP @8, volume @22
     * Code 5 OI:    OI int @8
     * Code 8 Full:  LTP @8, volume @22, OI @34
     */
    private void parsePacket(byte[] packet) {
        try {
            if (packet == null || packet.length < 8) return;
            ByteBuffer b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
            int code = b.get(0) & 0xff;
            long sid = b.getInt(4) & 0xffffffffL;
            double ltp = Double.NaN, oi = Double.NaN, volume = Double.NaN;

            if (code == 2 && packet.length >= 12) {
                ltp = b.getFloat(8);
            } else if (code == 4 && packet.length >= 26) {
                ltp = b.getFloat(8);
                volume = b.getInt(22) & 0xffffffffL;
            } else if (code == 5 && packet.length >= 12) {
                oi = b.getInt(8) & 0xffffffffL;
            } else if (code == 8 && packet.length >= 38) {
                ltp = b.getFloat(8);
                volume = b.getInt(22) & 0xffffffffL;
                oi = b.getInt(34) & 0xffffffffL; // fixed: was 35
            } else if (code == 6 && packet.length >= 16) {
                // prev close packet may carry prev OI at offset 12
                oi = b.getInt(12) & 0xffffffffL;
            }

            if (!Double.isNaN(ltp) || !Double.isNaN(oi)) {
                JSONObject tick = new JSONObject();
                tick.put("code", code);
                tick.put("securityId", sid);
                if (!Double.isNaN(ltp)) tick.put("ltp", ltp);
                if (!Double.isNaN(oi)) tick.put("oi", oi);
                if (!Double.isNaN(volume)) tick.put("volume", volume);
                js("window.onDhanTick && window.onDhanTick(" + tick.toString() + ")");
            }
        } catch (Exception e) {
            status("Packet parse error: " + e.getMessage(), false);
        }
    }

    @Override
    public void onDestroy() {
        if (socket != null) socket.close(1000, "app closed");
        if (http != null) http.dispatcher().cancelAll();
        webView = null;
        super.onDestroy();
    }
}
