package app.yawmi;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Alarms.ensureChannel(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFF060914);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void reply(final String id, final int status, final String body) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.__chatCb(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(body) + ")", null);
            }
        });
    }

    private final class Bridge {
        /** Replaces every pending reminder with the list the page computed. */
        @JavascriptInterface
        public void schedule(String json) {
            Alarms.save(MainActivity.this, json);
            Alarms.apply(MainActivity.this);
        }

        @JavascriptInterface
        public void askNotify() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    }
                });
            }
        }

        /** Sends one request to the Anthropic API with the key the person saved on this phone. */
        @JavascriptInterface
        public void chat(final String key, final String body, final String id) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int status = 0;
                    String out;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                        c.setRequestMethod("POST");
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(120000);
                        c.setDoOutput(true);
                        c.setRequestProperty("content-type", "application/json");
                        c.setRequestProperty("x-api-key", key);
                        c.setRequestProperty("anthropic-version", "2023-06-01");
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        status = c.getResponseCode();
                        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int n;
                        while (in != null && (n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                        out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        out = String.valueOf(e.getMessage());
                    }
                    reply(id, status, out);
                }
            }).start();
        }
    }
}
