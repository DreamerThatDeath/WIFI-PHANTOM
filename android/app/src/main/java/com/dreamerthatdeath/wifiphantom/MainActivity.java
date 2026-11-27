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

public class MainActivity extends Activity {
    private static final int WIFI_PERMISSION_REQUEST = 41;
    private static final String WEBSITE = "https://dreamerthatdeath.github.io/WIFI-PHANTOM/";
    private WebView webView;
    private WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;
    private boolean receiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07120D);
        getWindow().setNavigationBarColor(0xFF07120D);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
                item.put("signalPercent", Math.max(0, Math.min(100, WifiManager.calculateSignalLevel(result.level, 101))));
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

    private class WifiBridge {
        @JavascriptInterface
        public void scanWifi() {
            runOnUiThread(() -> beginScan());
        }

        @JavascriptInterface
        public void openWifiSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open Wi-Fi settings.", Toast.LENGTH_SHORT).show();
                }
            });
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
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidWifi");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
