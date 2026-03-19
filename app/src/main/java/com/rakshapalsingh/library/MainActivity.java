package com.rakshapalsingh.library;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RSLibrary";
    private static final int WIFI_PERMISSION_REQUEST = 1001;
    private WebView webView;
    private WifiManager wifiManager;

    // Pending suggestion data (used when permission is granted after request)
    private String pendingSsid = null;
    private String pendingPass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        webView = findViewById(R.id.webview);

        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ─────────────────────────────────────────────
    //  WebView Setup
    // ─────────────────────────────────────────────
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Core settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Allow file:// to load https:// Firebase resources
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Modern web support
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // ── Inject Android Bridge ──
        // JavaScript can call: window.AndroidBridge.connectWifi(ssid, pass)
        //                      window.AndroidBridge.forgetWifi()
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Handle external links (Firebase auth popup etc.)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Let UPI payment URLs open in external apps
                if (url.startsWith("upi://")) {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "No UPI app found", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        // Enable Chrome DevTools debugging in debug builds
        
        WebView.setWebContentsDebuggingEnabled(true);
        

        webView.setWebChromeClient(new WebChromeClient());
    }

    // ─────────────────────────────────────────────
    //  Android Bridge — Called from JavaScript
    // ─────────────────────────────────────────────
    private class AndroidBridge {

        /**
         * Called from JS: window.AndroidBridge.connectWifi(ssid, pass)
         * Uses Android WiFi Suggestion API — student never sees the password.
         * The system shows a native notification: "Connect to LibraryWiFi?"
         */
        @JavascriptInterface
        public void connectWifi(String ssid, String pass) {
            Log.d(TAG, "connectWifi called: ssid=" + ssid);
            pendingSsid = ssid;
            pendingPass = pass;

            runOnUiThread(() -> {
                if (hasWifiPermissions()) {
                    suggestWifiNetwork(ssid, pass);
                } else {
                    requestWifiPermissions();
                }
            });
        }

        /**
         * Called from JS: window.AndroidBridge.forgetWifi()
         * Removes the WiFi suggestion when student leaves membership.
         */
        @JavascriptInterface
        public void forgetWifi() {
            Log.d(TAG, "forgetWifi called");
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiManager.removeNetworkSuggestions(new ArrayList<>());
                    showToast("Library Wi-Fi removed from suggestions.");
                }
            });
        }

        /**
         * Called from JS to check if running inside APK
         * window.AndroidBridge.isNative() → returns "true"
         */
        @JavascriptInterface
        public String isNative() {
            return "true";
        }
    }

    // ─────────────────────────────────────────────
    //  WiFi Suggestion API (Android 10+ / API 29+)
    //  Works on Android 8+ with legacy method fallback
    // ─────────────────────────────────────────────
    private void suggestWifiNetwork(String ssid, String pass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ── Modern: WiFi Suggestion API (Android 10+) ──
            // This shows a system notification: "Do you want to connect to <ssid>?"
            // Student taps YES — connects automatically. Password is NEVER shown.

            WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(pass)
                    .setIsAppInteractionRequired(true)   // show system notification
                    .setIsUserInteractionRequired(true)  // user confirms
                    .build();

            List<WifiNetworkSuggestion> suggestionList = new ArrayList<>();
            suggestionList.add(suggestion);

            // Remove old suggestions first to avoid duplicates
            wifiManager.removeNetworkSuggestions(suggestionList);

            int status = wifiManager.addNetworkSuggestions(suggestionList);

            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                showToast("📶 Connect to Library Wi-Fi? Check notification!");
                // Notify JS that suggestion was added successfully
                runOnMainThread("window.onWifiSuggested && window.onWifiSuggested('success')");
            } else if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {
                showToast("📶 Library Wi-Fi already suggested. Check connections.");
            } else {
                showToast("Wi-Fi suggestion failed (code: " + status + ")");
                Log.e(TAG, "addNetworkSuggestions failed: " + status);
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // ── Fallback: Android 8 & 9 — WifiConfiguration (deprecated but works) ──
            try {
                android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
                config.SSID = "\"" + ssid + "\"";
                config.preSharedKey = "\"" + pass + "\"";
                config.status = android.net.wifi.WifiConfiguration.Status.ENABLED;
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);

                int netId = wifiManager.addNetwork(config);
                if (netId != -1) {
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(netId, true);
                    wifiManager.reconnect();
                    showToast("📶 Connecting to Library Wi-Fi...");
                } else {
                    showToast("Could not add network. Please connect manually.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Legacy WiFi connect failed", e);
                showToast("Wi-Fi connection error.");
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Permissions
    // ─────────────────────────────────────────────
    private boolean hasWifiPermissions() {
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearby = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            return fine || nearby;
        }
        return fine;
    }

    private void requestWifiPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        ActivityCompat.requestPermissions(this,
                perms.toArray(new String[0]),
                WIFI_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            if (granted && pendingSsid != null) {
                suggestWifiNetwork(pendingSsid, pendingPass);
                pendingSsid = null;
                pendingPass = null;
            } else {
                showToast("Location permission needed for Wi-Fi connection.");
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────
    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private void runOnMainThread(String js) {
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    // Handle back button — navigate WebView back instead of closing app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
