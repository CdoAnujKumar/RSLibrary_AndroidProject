package com.rakshapalsingh.library;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RSLibrary";
    private static final int RC_SIGN_IN = 9001;
    private static final int WIFI_PERMISSION_REQUEST = 1001;

    private WebView webView;
    private WifiManager wifiManager;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private String pendingSsid = null;
    private String pendingPass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("upi://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "No UPI app found", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) passUserToJS(user);
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void passUserToJS(FirebaseUser user) {
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail().replace("'", "\\'") : "";
        String name = user.getDisplayName() != null ? user.getDisplayName().replace("'", "\\'") : "";
        String photo = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        String js = "window.onNativeSignIn && window.onNativeSignIn('" + uid + "','" + email + "','" + name + "','" + photo + "')";
        runOnMainThread(js);
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void startGoogleLogin() {
            runOnUiThread(() -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        }

        @JavascriptInterface
        public void logout() {
            runOnUiThread(() -> {
                mAuth.signOut();
                mGoogleSignInClient.signOut().addOnCompleteListener(task ->
                    runOnMainThread("window.onNativeLogout && window.onNativeLogout()"));
            });
        }

        @JavascriptInterface
        public void connectWifi(String ssid, String pass) {
            pendingSsid = ssid;
            pendingPass = pass;
            runOnUiThread(() -> {
                if (hasWifiPermissions()) suggestWifiNetwork(ssid, pass);
                else requestWifiPermissions();
            });
        }

        @JavascriptInterface
        public void forgetWifi() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiManager.removeNetworkSuggestions(new ArrayList<>());
                    showToast("Library Wi-Fi removed.");
                }
            });
        }

        @JavascriptInterface
        public String isNative() { return "true"; }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential).addOnCompleteListener(this, t -> {
                    if (t.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) passUserToJS(user);
                    } else {
                        String err = t.getException() != null ? t.getException().getMessage() : "Auth failed";
                        showToast("Sign-in failed: " + err);
                        runOnMainThread("window.onNativeSignInError && window.onNativeSignInError('" + err + "')");
                    }
                });
            } catch (ApiException e) {
                showToast("Google sign-in failed: " + e.getMessage());
                runOnMainThread("window.onNativeSignInError && window.onNativeSignInError('" + e.getMessage() + "')");
            }
        }
    }

    private void suggestWifiNetwork(String ssid, String pass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid).setWpa2Passphrase(pass)
                    .setIsAppInteractionRequired(true).setIsUserInteractionRequired(true).build();
            List<WifiNetworkSuggestion> list = new ArrayList<>();
            list.add(suggestion);
            wifiManager.removeNetworkSuggestions(list);
            int status = wifiManager.addNetworkSuggestions(list);
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                showToast("📶 Check notification to connect to Library Wi-Fi!");
            else if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE)
                showToast("📶 Library Wi-Fi already suggested.");
            else showToast("Wi-Fi suggestion failed: " + status);
        } else {
            try {
                android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
                config.SSID = "\"" + ssid + "\"";
                config.preSharedKey = "\"" + pass + "\"";
                config.status = android.net.wifi.WifiConfiguration.Status.ENABLED;
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);
                int netId = wifiManager.addNetwork(config);
                if (netId != -1) { wifiManager.disconnect(); wifiManager.enableNetwork(netId, true); wifiManager.reconnect(); showToast("📶 Connecting..."); }
            } catch (Exception e) { Log.e(TAG, "Legacy WiFi failed", e); }
        }
    }

    private boolean hasWifiPermissions() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearby = ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            return fine || nearby;
        }
        return fine;
    }

    private void requestWifiPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), WIFI_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            if (granted && pendingSsid != null) { suggestWifiNetwork(pendingSsid, pendingPass); pendingSsid = null; pendingPass = null; }
            else showToast("Location permission needed for Wi-Fi.");
        }
    }

    private void showToast(String msg) { runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()); }
    private void runOnMainThread(String js) { runOnUiThread(() -> webView.evaluateJavascript(js, null)); }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
                }
