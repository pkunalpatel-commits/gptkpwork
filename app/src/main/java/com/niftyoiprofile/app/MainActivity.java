package com.niftyoiprofile.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import okhttp3.*;
import okio.ByteString;

public class MainActivity extends Activity {
    private WebView webView;
    private OkHttpClient http;
    private WebSocket socket;
    private volatile String clientId = "";
    private volatile String accessToken = "";
    private volatile String selectedSecurityId = "";
    private volatile String selectedSegment = "NSE_FNO";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        http = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS).build();
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(false);
        webView.addJavascriptInterface(new DhanBridge(), "DhanBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void js(String script) {
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); });
    }
    private void status(String message, boolean ok) {
        try {
            JSONObject o = new JSONObject();
            o.put("message", message); o.put("ok", ok);
            js("window.onDhanStatus && window.onDhanStatus(" + o.toString() + ")");
        } catch (Exception ignored) {}
    }
    private Request.Builder apiRequest(String url) {
        return new Request.Builder().url(url).header("access-token", accessToken)
                .header("client-id", clientId).header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }
    private void postJson(String url, JSONObject body, String callback) {
        Request req = apiRequest(url).post(RequestBody.create(body.toString(),
                MediaType.parse("application/json; charset=utf-8"))).build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                js(callback + "(false," + JSONObject.quote("Network error: " + e.getMessage()) + ")");
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String bodyText = response.body() != null ? response.body().string() : "{}";
                if (!response.isSuccessful()) {
                    js(callback + "(false," + JSONObject.quote("Dhan HTTP " + response.code() + ": " + bodyText) + ")");
                } else {
                    js(callback + "(true," + JSONObject.quote(bodyText) + ")");
                }
                response.close();
            }
        });
    }

    public class DhanBridge {
        @JavascriptInterface public void setCredentials(String cid, String token) {
            if (cid == null || token == null || cid.trim().isEmpty() || token.trim().isEmpty()) {
                status("Enter Dhan Client ID and access token.", false); return;
            }
            clientId = cid.trim(); accessToken = token.trim();
            status("Credentials held in memory. Requesting expiry list…", true);
            fetchExpiries();
        }
        @JavascriptInterface public void fetchExpiries() {
            if (!ready()) return;
            try {
                JSONObject b = new JSONObject(); b.put("UnderlyingScrip", 13); b.put("UnderlyingSeg", "IDX_I");
                postJson("https://api.dhan.co/v2/optionchain/expirylist", b, "window.onExpiryResponse");
            } catch (Exception e) { status("Expiry request error: " + e.getMessage(), false); }
        }
        @JavascriptInterface public void fetchChain(String expiry) {
            if (!ready()) return;
            try {
                JSONObject b = new JSONObject(); b.put("UnderlyingScrip", 13); b.put("UnderlyingSeg", "IDX_I"); b.put("Expiry", expiry);
                postJson("https://api.dhan.co/v2/optionchain", b, "window.onChainResponse");
            } catch (Exception e) { status("Option-chain request error: " + e.getMessage(), false); }
        }
        @JavascriptInterface public void loadCandles(String securityId, String interval) {
            if (!ready()) return;
            try {
                selectedSecurityId = securityId;
                JSONObject b = new JSONObject();
                b.put("securityId", securityId); b.put("exchangeSegment", "NSE_FNO");
                b.put("instrument", "OPTIDX"); b.put("interval", interval);
                b.put("oi", true);
                java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
                b.put("fromDate", today.minusDays(45).toString() + " 09:15:00");
                b.put("toDate", today.toString() + " 15:30:00");
                postJson("https://api.dhan.co/v2/charts/intraday", b, "window.onCandlesResponse");
            } catch (Exception e) { status("Candle request error: " + e.getMessage(), false); }
        }
        @JavascriptInterface public void connectContract(String securityId) {
            if (!ready()) return;
            selectedSecurityId = securityId;
            if (socket != null) { socket.close(1000, "switch contract"); socket = null; }
            String url = "wss://api-feed.dhan.co?version=2&token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8)
                    + "&clientId=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8) + "&authType=2";
            Request request = new Request.Builder().url(url).build();
            socket = http.newWebSocket(request, new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response response) {
                    try {
                        JSONObject item = new JSONObject(); item.put("ExchangeSegment", selectedSegment); item.put("SecurityId", securityId);
                        JSONArray list = new JSONArray(); list.put(item);
                        JSONObject sub = new JSONObject(); sub.put("RequestCode", 21); sub.put("InstrumentCount", 1); sub.put("InstrumentList", list);
                        ws.send(sub.toString());
                        status("Dhan WebSocket connected; subscribed to contract " + securityId, true);
                    } catch (Exception e) { status("Subscription error: " + e.getMessage(), false); }
                }
                @Override public void onMessage(WebSocket ws, ByteString bytes) { parsePacket(bytes.toByteArray()); }
                @Override public void onMessage(WebSocket ws, String text) { status("Dhan feed message: " + text, true); }
                @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                    status("WebSocket failed: " + t.getMessage() + ". Check token, subscription and network.", false);
                }
                @Override public void onClosed(WebSocket ws, int code, String reason) { status("WebSocket closed: " + reason, false); }
            });
        }
        @JavascriptInterface public void disconnect() {
            if (socket != null) { socket.close(1000, "user disconnect"); socket = null; }
            status("Disconnected.", true);
        }
        private boolean ready() {
            if (clientId.isEmpty() || accessToken.isEmpty()) { status("Enter credentials and connect first.", false); return false; }
            return true;
        }
    }

    private void parsePacket(byte[] packet) {
        try {
            if (packet == null || packet.length < 8) return;
            ByteBuffer b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
            int code = b.get(0) & 0xff;
            int length = b.getShort(1) & 0xffff;
            int seg = b.get(3) & 0xff;
            long sid = b.getInt(4) & 0xffffffffL;
            double ltp = Double.NaN, oi = Double.NaN, volume = Double.NaN;
            if (code == 2 && packet.length >= 17) ltp = b.getFloat(8);
            else if (code == 4 && packet.length >= 51) {
                ltp = b.getFloat(8); volume = b.getInt(23) & 0xffffffffL;
            } else if (code == 5 && packet.length >= 13) oi = b.getInt(8) & 0xffffffffL;
            else if (code == 8 && packet.length >= 63) {
                ltp = b.getFloat(8); volume = b.getInt(23) & 0xffffffffL; oi = b.getInt(35) & 0xffffffffL;
            } else if (code == 6 && packet.length >= 17) oi = b.getInt(13) & 0xffffffffL;
            if (!Double.isNaN(ltp) || !Double.isNaN(oi)) {
                JSONObject tick = new JSONObject();
                tick.put("code", code); tick.put("securityId", sid); tick.put("segment", seg);
                if (!Double.isNaN(ltp)) tick.put("ltp", ltp);
                if (!Double.isNaN(oi)) tick.put("oi", oi);
                if (!Double.isNaN(volume)) tick.put("volume", volume);
                js("window.onDhanTick && window.onDhanTick(" + tick.toString() + ")");
            }
        } catch (Exception e) { status("Could not parse a Dhan packet: " + e.getMessage(), false); }
    }
    @Override public void onDestroy() {
        if (socket != null) socket.close(1000, "app closed");
        if (http != null) http.dispatcher().cancelAll();
        webView = null; super.onDestroy();
    }
}
