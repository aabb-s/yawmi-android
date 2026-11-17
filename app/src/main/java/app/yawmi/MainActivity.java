package app.yawmi;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int SPACE = 0xFF060914;
    private static final long RELOCK_MS = 60_000;
    private WebView web;
    private boolean unlocked, asking;
    private long leftAt;

    private SharedPreferences prefs() {
        return getSharedPreferences("yawmi", Context.MODE_PRIVATE);
    }

    private boolean lockOn() {
        return prefs().getBoolean("lock", true) && getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Alarms.ensureChannel(this);
        if (lockOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        web = new WebView(this);
        web.setBackgroundColor(SPACE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(false);
        s.setSaveFormData(false);
        web.addJavascriptInterface(new Bridge(), "Android");

        // Android 15+ draws apps behind the system bars: keep the page clear of them and of the keyboard.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SPACE);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                } else {
                    v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                }
                return in;
            }
        });
        setContentView(root);
        web.setVisibility(lockOn() ? View.INVISIBLE : View.VISIBLE);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (leftAt != 0 && SystemClock.elapsedRealtime() - leftAt > RELOCK_MS) unlocked = false;
        if (lockOn() && !unlocked) {
            web.setVisibility(View.INVISIBLE);
            ask();
        } else {
            web.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!asking) leftAt = SystemClock.elapsedRealtime();
    }

    private void open() {
        asking = false;
        unlocked = true;
        leftAt = 0;
        web.setVisibility(View.VISIBLE);
    }

    /** Asks for the phone's own fingerprint, face or screen-lock code. */
    private void ask() {
        if (asking) return;
        asking = true;
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt p = new BiometricPrompt.Builder(this)
                    .setTitle("يومي")
                    .setSubtitle("افتح القفل للمتابعة")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            p.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    open();
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    asking = false;
                    finish();
                }
            });
        } else {
            Intent i = getSystemService(KeyguardManager.class).createConfirmDeviceCredentialIntent("يومي", "افتح القفل للمتابعة");
            if (i == null) open();
            else startActivityForResult(i, 7);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 7) return;
        if (res == RESULT_OK) open();
        else {
            asking = false;
            finish();
        }
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
        public boolean getLock() {
            return prefs().getBoolean("lock", true);
        }

        @JavascriptInterface
        public void setLock(final boolean on) {
            prefs().edit().putBoolean("lock", on).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (on && lockOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }

        @JavascriptInterface
        public void askNotify() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!asking) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
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
