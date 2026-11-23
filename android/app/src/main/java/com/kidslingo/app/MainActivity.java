package com.kidslingo.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;

/**
 * 키즈링고 — 저장소의 웹앱(www/index.html)을 assets/www 에 그대로 담아 인터넷 없이도 열리고,
 * 영어·한국어 음성은 안드로이드 기본 엔진(android.speech.tts.TextToSpeech)으로 읽어 준다.
 *
 * 웹 화면은 window.AndroidTTS 가 있으면 이 클래스의 speak() 를 쓰고,
 * 없으면(일반 브라우저) speechSynthesis 로 대신 읽는다.
 */
public class MainActivity extends Activity {

    private static final String TAG = "KidsLingo";
    private static final String START_URL = "file:///android_asset/www/index.html";
    private static final int BG_COLOR = 0xFFF7F9FA;
    private static final float DEFAULT_RATE = 0.75f;
    private static final float SPEECH_PITCH = 1.05f;
    private static final String LANG_EN = "en-US";
    private static final String LANG_KO = "ko-KR";

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech ttsEn;
    private TextToSpeech ttsKo;
    private volatile boolean enReady = false;
    private volatile boolean koReady = false;

    /* 엔진이 막 시작했을 때 들어온 첫 발음(문제를 여는 순간의 자동 발음)은 버리지 않고 대기시킨다. */
    private String pendingText = null;
    private String pendingLang = LANG_EN;
    private float pendingRate = DEFAULT_RATE;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initTts();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG_COLOR);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(BG_COLOR);
        configureWebView(webView);
        root.addView(webView);

        setContentView(root);
        webView.loadUrl(START_URL);
    }

    /* ===================================================================
       음성 엔진
       =================================================================== */

    private void initTts() {
        try {
            ttsEn = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && ttsEn != null) {
                    configureEngine(ttsEn);
                    enReady = applyLanguage(ttsEn, Locale.US);
                } else {
                    enReady = false;
                    Log.w(TAG, "영어 TTS 초기화 실패: " + status);
                }
                flushPending();
            });
            ttsKo = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && ttsKo != null) {
                    configureEngine(ttsKo);
                    koReady = applyLanguage(ttsKo, Locale.KOREA);
                } else {
                    koReady = false;
                    Log.w(TAG, "한국어 TTS 초기화 실패: " + status);
                }
                flushPending();
            });
        } catch (Exception e) {
            Log.e(TAG, "TTS 생성 실패", e);
        }
    }

    private void configureEngine(TextToSpeech tts) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            tts.setAudioAttributes(attrs);
        }
        tts.setSpeechRate(DEFAULT_RATE);
        tts.setPitch(SPEECH_PITCH);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
            }

            @Override
            public void onError(String utteranceId) {
                Log.w(TAG, "발음 재생 오류: " + utteranceId);
            }
        });
    }

    /** 언어 데이터가 기기에 있는지 확인하고 설정한다. 없으면 false */
    private boolean applyLanguage(TextToSpeech tts, Locale locale) {
        try {
            int result = tts.setLanguage(locale);
            boolean ok = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ok) Log.w(TAG, locale + " 음성 데이터 없음 (결과 " + result + ")");
            return ok;
        } catch (Exception e) {
            Log.w(TAG, locale + " 설정 실패", e);
            return false;
        }
    }

    private boolean isReady(String langTag) {
        return LANG_KO.equals(langTag) ? koReady : enReady;
    }

    /** 웹 화면에서 부르는 실제 발음 요청 */
    private void doSpeak(final String text, final String langTag, final float rate) {
        handler.post(() -> {
            TextToSpeech engine = LANG_KO.equals(langTag) ? ttsKo : ttsEn;
            TextToSpeech other = LANG_KO.equals(langTag) ? ttsEn : ttsKo;

            if (engine == null) {
                Log.w(TAG, "TTS 엔진이 없습니다.");
                return;
            }
            // 다른 언어로 읽던 소리는 끊고, 이번 문장만 읽는다.
            if (other != null) {
                try {
                    other.stop();
                } catch (Exception ignored) {
                }
            }
            if (!isReady(langTag)) {
                pendingText = text;
                pendingLang = langTag;
                pendingRate = rate;
                return;
            }

            try {
                engine.setSpeechRate(rate);
                engine.setPitch(SPEECH_PITCH);
                int result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kidslingo");
                if (result == TextToSpeech.ERROR) {
                    Log.w(TAG, "발음 요청 실패: " + text);
                }
            } catch (Exception e) {
                Log.e(TAG, "발음 재생 예외", e);
            }
        });
    }

    /** 엔진이 준비되면 대기 중이던 첫 발음을 흘려보낸다 */
    private void flushPending() {
        handler.post(() -> {
            if (pendingText == null) return;
            if (!isReady(pendingLang)) return;
            String text = pendingText;
            String lang = pendingLang;
            float rate = pendingRate;
            pendingText = null;
            doSpeak(text, lang, rate);
        });
    }

    /** 웹(자바스크립트)에서 쓰는 음성 인터페이스 */
    private class TtsBridge {

        @JavascriptInterface
        public boolean speak(String text, String lang, double rate) {
            if (text == null) return false;
            final String value = text.trim();
            if (value.isEmpty()) return false;

            boolean korean = lang != null && lang.toLowerCase(Locale.US).startsWith("ko");
            String langTag = korean ? LANG_KO : LANG_EN;
            if ((korean ? ttsKo : ttsEn) == null) return false;

            double wanted = (rate > 0) ? rate : DEFAULT_RATE;
            float speed = (float) Math.max(0.3, Math.min(2.0, wanted));
            doSpeak(value, langTag, speed);
            return true;
        }

        @JavascriptInterface
        public void stop() {
            handler.post(() -> {
                pendingText = null;
                try {
                    if (ttsEn != null) ttsEn.stop();
                } catch (Exception ignored) {
                }
                try {
                    if (ttsKo != null) ttsKo.stop();
                } catch (Exception ignored) {
                }
            });
        }

        /** "en=1;ko=1;engine=com.google.android.tts" 형태로 알려 준다 */
        @JavascriptInterface
        public String status() {
            String engine = "";
            try {
                if (ttsEn != null) engine = String.valueOf(ttsEn.getDefaultEngine());
            } catch (Exception ignored) {
            }
            return "en=" + (enReady ? "1" : "0")
                    + ";ko=" + (koReady ? "1" : "0")
                    + ";engine=" + engine;
        }

        @JavascriptInterface
        public boolean isAvailable() {
            return enReady || koReady;
        }

        @JavascriptInterface
        public void openTtsSettings() {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        }
    }

    /* ===================================================================
       화면(WebView)
       =================================================================== */

    private void configureWebView(WebView view) {
        WebSettings s = view.getSettings();

        s.setJavaScriptEnabled(true);
        // 학습 진행도(하트·보석·클리어 횟수)를 localStorage 에 보관한다.
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // 문제를 여는 순간의 자동 발음이 사용자 터치를 기다리지 않도록 한다.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSaveFormData(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handleUrl(Uri.parse(url));
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d(TAG, sourceID + ":" + lineNumber + " " + message);
            }
        });
    }

    /** 앱 안의 페이지는 WebView 에서, 바깥 링크는 기본 브라우저에서 연다. */
    private boolean handleUrl(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null || "file".equals(scheme) || "about".equals(scheme)
                || "javascript".equals(scheme)) {
            return false;
        }
        if ("http".equals(scheme) || "https".equals(scheme)
                || "mailto".equals(scheme) || "tel".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "열 수 있는 앱이 없습니다: " + uri);
            }
            return true;
        }
        return true;
    }

    /** 뒤로가기: 모달 → 시트 → 학습 화면 → 앱 종료 순서로 웹 화면이 먼저 판단한다. */
    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        String js = "(function(){try{return window.handleAndroidBack?window.handleAndroidBack():'exit';}"
                + "catch(e){return 'exit';}})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null || value.contains("exit")) {
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        // 앱을 벗어나면 읽던 소리를 멈춘다.
        try {
            if (ttsEn != null) ttsEn.stop();
            if (ttsKo != null) ttsKo.stop();
        } catch (Exception ignored) {
        }
        pendingText = null;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidTTS");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        try {
            if (ttsEn != null) {
                ttsEn.stop();
                ttsEn.shutdown();
                ttsEn = null;
            }
            if (ttsKo != null) {
                ttsKo.stop();
                ttsKo.shutdown();
                ttsKo = null;
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
