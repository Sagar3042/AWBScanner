package com.example.awbscanner;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int OVERLAY_PERMISSION_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Full screen WebView toiri kora
        webView = new WebView(this);
        setContentView(webView);

        // WebView configuration setup kora
        setupWebView();

        // App open holei prothome Camera permission check kora
        checkCameraPermission();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        // Camera jate user er click charai auto start hote pare
        webSettings.setMediaPlaybackRequiresUserGesture(false); 

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // HTML5 theke asha camera permission request auto-grant kora
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });

        // ==========================================
        // JS THEKE ANDROID E JOGAJOG ER BRIDGE
        // ==========================================
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidInterface");
        
        // Assets folder theke index.html load kora
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    // ==========================================
    // JAVASCRIPT INTERFACE CLASS
    // HTML er JS file theke ei method gulo call hobe
    // ==========================================
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) { 
            mContext = c; 
        }

        @JavascriptInterface
        public void startFloatingService() {
            // PIP mode e jawar age check kora je 'Draw over other apps' permission ache kina
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(mContext)) {
                // Permission na thakle settings page e niye jabe
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
                
                // UI thread e Toast dekhano
                runOnUiThread(() -> Toast.makeText(mContext, "Doya kore 'Allow display over other apps' on korun", Toast.LENGTH_LONG).show());
                return;
            }

            // Permission thakle Service start kore ei main activity bondho kore dewa
            Intent serviceIntent = new Intent(MainActivity.this, FloatingScannerService.class);
            startService(serviceIntent);
            finish(); // Activity close hobe, kintu floating service cholte thakbe
        }

        @JavascriptInterface
        public void vibratePhone() {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(100);
                }
            }
        }

        @JavascriptInterface
        public void playBeepSound() {
            // Scan success hole native beep sound play kora
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150); 
        }

        @JavascriptInterface
        public void copyToClipboardNative(String text) {
            // Native Android Clipboard Manager bebohar kore text copy kora
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("AWB Scan", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
            
            // Toast msg dekhano jate user bujhte pare copy hoyeche
            runOnUiThread(() -> Toast.makeText(mContext, "Copied: " + text, Toast.LENGTH_SHORT).show());
        }
        
        @JavascriptInterface
        public void stopFloatingService() {
            // Ekhane kichu korar dorkar nei, karon ei interface ta Main Activity te cholche (standard mode)
        }
    }
}
