package com.example.awbscanner;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingScannerService extends Service {

    private WindowManager windowManager;
    private WebView floatingWebView;
    private WindowManager.LayoutParams params;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingWebView = new WebView(this);

        // PIP Window er size (160dp x 160dp)
        int sizePx = (int) (160 * getResources().getDisplayMetrics().density);
        
        // Android version onujayi Overlay type set kora
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // FLAG_NOT_FOCUSABLE khub e important, noile pichoner app e touch ba keyboard kaj korbe na
        params = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        // Screen er bottom-right e thakbe prothome
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = 20; // margin
        params.y = 100;

        setupWebView();
        
        // Screen e WebView ti add kora
        windowManager.addView(floatingWebView, params);
        
        // Drag korar jonno touch listener
        setupDragListener();
    }

    private void setupWebView() {
        WebSettings webSettings = floatingWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        floatingWebView.setWebViewClient(new WebViewClient());
        floatingWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });

        // Floating Interface inject kora
        floatingWebView.addJavascriptInterface(new FloatingWebAppInterface(), "AndroidInterface");
        
        // URL e '?mode=floating' jure dewa jate HTML bujhte pare je ekhon PIP mode e ache
        floatingWebView.loadUrl("file:///android_asset/index.html?mode=floating");
    }

    private void setupDragListener() {
        floatingWebView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false; // False return korle WebView er vitorer click (jemon katar button) kaj korbe
                        
                    case MotionEvent.ACTION_MOVE:
                        // Gravity END(right) tai x er math ektu onnorokom
                        params.x = initialX - (int) (event.getRawX() - initialTouchX); 
                        params.y = initialY - (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingWebView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Service bondho hole screen theke view remove kora
        if (floatingWebView != null) {
            windowManager.removeView(floatingWebView);
            floatingWebView.destroy();
        }
    }

    // ==========================================
    // FLOATING MODE ER JAVASCRIPT BRIDGE
    // ==========================================
    public class FloatingWebAppInterface {
        
        @JavascriptInterface
        public void stopFloatingService() {
            // Background theke abar Main Activity (Full screen) open kora
            Intent intent = new Intent(FloatingScannerService.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            // Nijeke bondho kore dewa
            stopSelf(); 
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
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
        }

        @JavascriptInterface
        public void copyToClipboardNative(String text) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("AWB Scan Background", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
            Toast.makeText(FloatingScannerService.this, "Copied in Background: " + text, Toast.LENGTH_SHORT).show();
        }
        
        @JavascriptInterface
        public void startFloatingService() {
            // Already floating mode e achi, tai ekhane kichu korar dorkar nei
        }
    }
}
