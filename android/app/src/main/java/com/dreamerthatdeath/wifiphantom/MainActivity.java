package com.dreamerthatdeath.wifiphantom;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private static final int WIFI_PERMISSION_REQUEST = 41;
    private static final String WEBSITE = "https://dreamerthatdeath.github.io/WIFI-PHANTOM/";
    private WebView webView;
    private WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;
    private boolean receiverRegistered = false;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback wifiNetworkCallback;
    private boolean appRequestedNetwork = false;
    private static final String WIFI_KEY_ALIAS = "WIFI_PHANTOM_WIFI_CREDENTIALS";
    private static final String WIFI_PREFS = "wifi_phantom_saved_networks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07120D);
        getWindow().setNavigationBarColor(0xFF07120D);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF07120D);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WifiBridge(), "AndroidWifi");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("dreamerthatdeath.github.io".equalsIgnoreCase(host)) return false;
                if ("192.168.1.1".equals(host) || isPrivateRouterHost(host)) {
                    openExternal(uri.toString());
                    return true;
                }
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    openExternal(uri.toString());
                    return true;
                }
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl(WEBSITE);
        prepareScanReceiver();
    }

    private boolean isPrivateRouterHost(String host) {
        if (host == null) return false;
        return host.matches("192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}")
                || host.matches("10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9]{1,3}\\.[0-9]{1,3}");
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this address.", Toast.LENGTH_SHORT).show();
        }
    }

    private void prepareScanReceiver() {
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                publishScanResults(updated, null);
            }
        };
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        receiverRegistered = true;
    }

    private boolean hasWifiPermissions() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean nearby = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        return fine && nearby;
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            }, WIFI_PERMISSION_REQUEST);
        } else {
            requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, WIFI_PERMISSION_REQUEST);
        }
    }

    private boolean isLocationEnabled() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return manager.isLocationEnabled();
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void beginScan() {
        if (!hasWifiPermissions()) {
            requestWifiPermissions();
            return;
        }
        if (!isLocationEnabled()) {
            sendToPage(errorPayload("location_off", "Turn on Android Location to allow Wi-Fi scan results."));
            return;
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            sendToPage(errorPayload("wifi_off", "Turn on Wi-Fi first, then scan again."));
            return;
        }
        try {
            boolean started = wifiManager.startScan();
            if (!started) {
                // Android may throttle scans. Show the latest system-cached results.
                publishScanResults(false, "Android limited a fresh scan. Showing the latest available results.");
            }
        } catch (SecurityException e) {
            sendToPage(errorPayload("permission_denied", "Wi-Fi scan permission was not granted."));
        }
    }

    private void publishScanResults(boolean updated, String note) {
        if (!hasWifiPermissions()) {
            sendToPage(errorPayload("permission_denied", "Grant Nearby Wi-Fi and Location permissions to scan."));
            return;
        }
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            Collections.sort(results, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    return Integer.compare(b.level, a.level);
                }
            });
            JSONArray networks = new JSONArray();
            for (ScanResult result : results) {
                if (result.SSID == null || result.SSID.trim().isEmpty() || "<unknown ssid>".equals(result.SSID)) continue;
                JSONObject item = new JSONObject();
                item.put("name", result.SSID);
                item.put("signalDbm", result.level);
                item.put("signalPercent", Math.max(0, Math.min(100, ((result.level + 100) * 2))));
                item.put("frequency", result.frequency);
                item.put("security", securityFor(result.capabilities));
                networks.put(item);
            }
            JSONObject payload = new JSONObject();
            payload.put("status", "success");
            payload.put("updated", updated);
            payload.put("networks", networks);
            if (note != null) payload.put("note", note);
            sendToPage(payload.toString());
        } catch (SecurityException e) {
            sendToPage(errorPayload("permission_denied", "Android did not allow access to scan results."));
        } catch (Exception e) {
            sendToPage(errorPayload("scan_error", "Could not read Wi-Fi scan results."));
        }
    }

    private String securityFor(String capabilities) {
        String c = capabilities == null ? "" : capabilities.toUpperCase();
        if (c.contains("WPA3") || c.contains("SAE")) return "WPA3";
        if (c.contains("WPA2")) return "WPA2";
        if (c.contains("WPA")) return "WPA";
        if (c.contains("WEP")) return "WEP";
        return "OPEN";
    }

    private String errorPayload(String status, String message) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("status", status);
            payload.put("message", message);
        } catch (Exception ignored) { }
        return payload.toString();
    }

    private void sendToPage(String json) {
        runOnUiThread(() -> {
            if (webView == null) return;
            String quoted = JSONObject.quote(json);
            webView.evaluateJavascript("if(window.onNativeWifiResults){window.onNativeWifiResults(" + quoted + ");}", null);
        });
    }

    private void sendConnectionStatus(String status, String message) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("status", status);
            if (message != null) payload.put("message", message);
        } catch (Exception ignored) { }
        String json = payload.toString();
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("if(window.onNativeWifiConnectionStatus){window.onNativeWifiConnectionStatus(" + JSONObject.quote(json) + ");}", null);
        });
    }

    private SharedPreferences wifiPrefs() {
        return getSharedPreferences(WIFI_PREFS, MODE_PRIVATE);
    }

    private String credentialKey(String ssid) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(ssid.getBytes(StandardCharsets.UTF_8));
        return "network_" + Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE);
    }

    private SecretKey getOrCreateCredentialKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(WIFI_KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(WIFI_KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(WIFI_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private boolean saveWifiCredential(String ssid, String password) {
        if (ssid == null || ssid.trim().isEmpty() || password == null || password.length() < 8 || password.length() > 63) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateCredentialKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return wifiPrefs().edit().putString(credentialKey(ssid), Base64.encodeToString(combined, Base64.NO_WRAP)).commit();
        } catch (Exception e) {
            return false;
        }
    }

    private String readWifiCredential(String ssid) {
        try {
            String stored = wifiPrefs().getString(credentialKey(ssid), null);
            if (stored == null) return null;
            byte[] combined = Base64.decode(stored, Base64.NO_WRAP);
            if (combined.length < 13) return null;
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateCredentialKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasSavedWifiCredential(String ssid) {
        try { return wifiPrefs().contains(credentialKey(ssid)); }
        catch (Exception e) { return false; }
    }

    private void requestWifiConnection(String ssid, String password, String security) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            sendConnectionStatus("unsupported", "This Android version needs the system Wi-Fi settings to connect.");
            runOnUiThread(() -> openWifiSettings());
            return;
        }
        if (ssid == null || ssid.trim().isEmpty() || ssid.length() > 32) {
            sendConnectionStatus("error", "Invalid Wi-Fi network name.");
            return;
        }
        String kind = security == null ? "WPA2" : security.toUpperCase();
        try {
            WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder().setSsid(ssid);
            if ("OPEN".equals(kind) || "NONE".equals(kind)) {
                // Open networks need no passphrase; Android still displays its approval UI.
            } else if (kind.contains("WPA3")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (password == null || password.length() < 8 || password.length() > 63) {
                        sendConnectionStatus("error", "Enter the correct Wi-Fi password.");
                        return;
                    }
                    specifierBuilder.setWpa3Passphrase(password);
                } else {
                    sendConnectionStatus("unsupported", "WPA3 connection is not supported on this Android version.");
                    return;
                }
            } else if (kind.contains("WPA2") || kind.equals("WPA")) {
                if (password == null || password.length() < 8 || password.length() > 63) {
                    sendConnectionStatus("error", "Wi-Fi passwords must be 8 to 63 characters.");
                    return;
                }
                specifierBuilder.setWpa2Passphrase(password);
            } else {
                sendConnectionStatus("unsupported", "This security type is not supported by the in-app connector. Use Android Wi-Fi settings.");
                runOnUiThread(() -> openWifiSettings());
                return;
            }

            releaseRequestedNetwork(false);
            WifiNetworkSpecifier specifier = specifierBuilder.build();
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();
            wifiNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    appRequestedNetwork = true;
                    // Route this app's traffic through the requested Wi-Fi network only.
                    if (connectivityManager != null) connectivityManager.bindProcessToNetwork(network);
                    sendConnectionStatus("available", "Android approved the Wi-Fi connection request.");
                }

                @Override
                public void onUnavailable() {
                    appRequestedNetwork = false;
                    if (connectivityManager != null) connectivityManager.bindProcessToNetwork(null);
                    sendConnectionStatus("unavailable", "Android could not connect. Check the password or approve the system prompt.");
                }

                @Override
                public void onLost(Network network) {
                    appRequestedNetwork = false;
                    if (connectivityManager != null) connectivityManager.bindProcessToNetwork(null);
                    sendConnectionStatus("lost", "The requested Wi-Fi network is no longer available.");
                }
            };
            connectivityManager.requestNetwork(request, wifiNetworkCallback);
            sendConnectionStatus("requesting", "Waiting for Android's network approval.");
        } catch (SecurityException e) {
            sendConnectionStatus("error", "Android denied the connection request. Check app permissions and try again.");
        } catch (IllegalArgumentException e) {
            sendConnectionStatus("error", "The Wi-Fi details were not accepted by Android.");
        } catch (Exception e) {
            sendConnectionStatus("error", "Could not start the Wi-Fi connection request.");
        }
    }

    private void releaseRequestedNetwork(boolean notifyPage) {
        if (connectivityManager != null && wifiNetworkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(wifiNetworkCallback); } catch (Exception ignored) { }
            wifiNetworkCallback = null;
        }
        appRequestedNetwork = false;
        if (connectivityManager != null) {
            try { connectivityManager.bindProcessToNetwork(null); } catch (Exception ignored) { }
        }
        if (notifyPage) sendConnectionStatus("disconnected", "WIFI PHANTOM released its network request.");
    }

    private void disconnectWifiRequest() {
        if (wifiNetworkCallback != null) {
            releaseRequestedNetwork(true);
        } else {
            sendConnectionStatus("disconnected", "No WIFI PHANTOM connection request is active. Opening Android Wi-Fi settings.");
            runOnUiThread(() -> openWifiSettings());
        }
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Wi-Fi settings.", Toast.LENGTH_SHORT).show();
        }
    }

    private class WifiBridge {
        @JavascriptInterface
        public void scanWifi() {
            runOnUiThread(() -> beginScan());
        }

        @JavascriptInterface
        public void openWifiSettings() {
            runOnUiThread(() -> MainActivity.this.openWifiSettings());
        }

        @JavascriptInterface
        public void connectWifi(String ssid, String password, String security) {
            runOnUiThread(() -> requestWifiConnection(ssid, password, security));
        }

        @JavascriptInterface
        public boolean saveWifiPassword(String ssid, String password) {
            return saveWifiCredential(ssid, password);
        }

        @JavascriptInterface
        public boolean hasSavedWifiPassword(String ssid) {
            return hasSavedWifiCredential(ssid);
        }

        @JavascriptInterface
        public void connectSavedWifi(String ssid, String security) {
            runOnUiThread(() -> {
                String saved = readWifiCredential(ssid);
                if (saved == null) {
                    sendConnectionStatus("error", "No saved password was found. Enter it again.");
                    return;
                }
                requestWifiConnection(ssid, saved, security);
            });
        }

        @JavascriptInterface
        public void disconnectWifi() {
            runOnUiThread(() -> disconnectWifiRequest());
        }

        @JavascriptInterface
        public void openRouter(String url) {
            runOnUiThread(() -> {
                Uri uri = Uri.parse(url);
                if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return;
                if (!isPrivateRouterHost(uri.getHost())) {
                    Toast.makeText(MainActivity.this, "Only a private local router address is allowed.", Toast.LENGTH_SHORT).show();
                    return;
                }
                openExternal(uri.toString());
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_PERMISSION_REQUEST) {
            if (hasWifiPermissions()) beginScan();
            else sendToPage(errorPayload("permission_denied", "Allow Location and Nearby Wi-Fi permissions to scan."));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered && scanReceiver != null) {
            try { unregisterReceiver(scanReceiver); } catch (Exception ignored) { }
        }
        releaseRequestedNetwork(false);
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidWifi");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
