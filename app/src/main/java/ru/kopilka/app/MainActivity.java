package ru.kopilka.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout adHolder;
    private FrameLayout splash;
    private Object banner, appOpen, rewarded;
    private long appOpenLoadTime;
    private boolean htmlConsent, adsStarted, firstResume = true, pageReady;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());

    static final String BANNER_ID = "demo-banner-yandex";
    static final String APP_OPEN_ID = "demo-appopenad-yandex";
    static final String REWARDED_ID = "demo-rewarded-yandex";

    private static Class<?> C(String n) {
        try { return Class.forName(n); } catch (Throwable t) { return null; }
    }
    private static Object invoke(Object t, String name, Object... args) {
        if (t == null) return null;
        for (Method m : t.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                try { return m.invoke(t, args); } catch (Throwable e) { return null; }
            }
        }
        return null;
    }
    private static Object staticInvoke(Class<?> c, String name, Object... args) {
        if (c == null) return null;
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                try { return m.invoke(null, args); } catch (Throwable e) { return null; }
            }
        }
        return null;
    }
    private static Object construct(Class<?> c, Object... args) {
        if (c == null) return null;
        try {
            for (Constructor<?> k : c.getConstructors()) {
                if (k.getParameterTypes().length == args.length) return k.newInstance(args);
            }
        } catch (Throwable e) { }
        return null;
    }
    private Object adRequest() {
        return invoke(construct(C("com.yandex.mobile.ads.common.AdRequest$Builder")), "build");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("ads", 0);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xFF1B1E6B);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF1B1E6B);
        web.addJavascriptInterface(new Bridge(), "AndroidAds");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                pageReady = true;
                h.postDelayed(new Runnable() { @Override public void run() { hideSplash(); } }, 450);
            }
        });
        column.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        adHolder = new LinearLayout(this);
        adHolder.setOrientation(LinearLayout.VERTICAL);
        column.addView(adHolder, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout frame = new FrameLayout(this);
        frame.addView(column, new FrameLayout.LayoutParams(-1, -1));
        splash = buildSplash();
        frame.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        web.loadUrl("file:///android_asset/www/kopilka.html");
        h.postDelayed(new Runnable() { @Override public void run() { hideSplash(); } }, 3000);

        htmlConsent = sp.getBoolean("consent", false);
        if (htmlConsent) startAds();
    }

    private FrameLayout buildSplash() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFFF0E3C8);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView pig = new TextView(this);
        pig.setText("🐷");
        pig.setTextSize(52);
        pig.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText("Копилка-Хрюндель");
        name.setTextSize(22);
        name.setTextColor(0xFF8A5A2B);
        name.setGravity(Gravity.CENTER);
        box.addView(pig);
        box.addView(name);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        f.addView(box, lp);
        return f;
    }
    private void hideSplash() {
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
            @Override public void run() { splash.setVisibility(View.GONE); }
        }).start();
    }

    private boolean adFree() { return sp.getLong("af", 0) > System.currentTimeMillis(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void js(String s) { if (pageReady) web.evaluateJavascript(s, null); }

    private void startAds() {
        if (adsStarted || !htmlConsent) return;
        adsStarted = true;
        Class<?> ma = C("com.yandex.mobile.ads.common.MobileAds");
        Class<?> li = C("com.yandex.mobile.ads.common.InitCompletionListener");
        if (ma != null && li != null) {
            Object proxy = Proxy.newProxyInstance(li.getClassLoader(), new Class<?>[]{li},
                new InvocationHandler() {
                    @Override public Object invoke(Object p, Method m, Object[] a) { return null; }
                });
            staticInvoke(ma, "initialize", this, proxy);
        } else {
            staticInvoke(ma, "initialize", this);
        }
        h.postDelayed(new Runnable() {
            @Override public void run() {
                ensureBanner();
                loadAppOpen();
                loadRewarded();
                js("toast('Реклама: баннер=" + (banner != null) + ", open=" + (appOpen != null) + ", rewarded=" + (rewarded != null) + "')");
                h.postDelayed(new Runnable() { @Override public void run() { tryAppOpen(); } }, 900);
            }
        }, 1400);
    }

    private void ensureBanner() {
        if (!adsStarted || adFree() || banner != null) return;
        banner = construct(C("com.yandex.mobile.ads.banner.BannerAdView"), this);
        if (banner == null) return;
        invoke(banner, "setAdUnitId", BANNER_ID);
        Object size = staticInvoke(C("com.yandex.mobile.ads.banner.BannerAdSize"), "stickyWidth", this);
        if (size != null) invoke(banner, "setAdSize", size);
        invoke(banner, "loadAd", adRequest());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(6);
        adHolder.addView((View) banner, p);
    }
    private void removeBanner() {
        if (banner != null) {
            invoke(banner, "destroy");
            adHolder.removeView((View) banner);
            banner = null;
        }
    }

    private void loadAppOpen() {
        appOpen = construct(C("com.yandex.mobile.ads.appopenad.AppOpenAd"), this);
        if (appOpen == null) return;
        invoke(appOpen, "setAdUnitId", APP_OPEN_ID);
        invoke(appOpen, "loadAd", adRequest());
        appOpenLoadTime = System.currentTimeMillis();
    }
    private void tryAppOpen() {
        if (!adsStarted || adFree() || appOpen == null) return;
        long now = System.currentTimeMillis();
        if (now - appOpenLoadTime > 4 * 3600_000L) { appOpen = null; loadAppOpen(); return; }
        if (now - sp.getLong("ao_last", 0) < 4 * 3600_000L) return;
        if (now - sp.getLong("rw_last", 0) < 10 * 60_000L) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(sp.getString("ao_day", "")))
            sp.edit().putString("ao_day", today).putInt("ao_n", 0).apply();
        if (sp.getInt("ao_n", 0) >= 3) return;
        sp.edit().putLong("ao_last", now).putInt("ao_n", sp.getInt("ao_n", 0) + 1).apply();
        Object ad = appOpen; appOpen = null;
        invoke(ad, "show", this);
        loadAppOpen();
    }

    private void loadRewarded() {
        rewarded = construct(C("com.yandex.mobile.ads.rewarded.RewardedAd"), this);
        if (rewarded == null) return;
        invoke(rewarded, "setAdUnitId", REWARDED_ID);
        Class<?> li = C("com.yandex.mobile.ads.rewarded.RewardedAdEventListener");
        if (li != null) {
            Object proxy = Proxy.newProxyInstance(li.getClassLoader(), new Class<?>[]{li},
                new InvocationHandler() {
                    @Override public Object invoke(Object p, Method m, Object[] a) {
                        String n = m.getName();
                        if ("onRewarded".equals(n)) {
                            sp.edit().putLong("rw_last", System.currentTimeMillis()).apply();
                            grantAdFree(48);
                        } else if ("onAdDismissed".equals(n)) {
                            loadRewarded();
                        }
                        return null;
                    }
                });
            invoke(rewarded, "setAdEventListener", proxy);
        }
        invoke(rewarded, "loadAd", adRequest());
    }
    private void showRewarded() {
        if (!Boolean.TRUE.equals(invoke(rewarded, "isLoaded"))) {
            loadRewarded();
            js("window.kopilkaAdFreeUnavailable()");
            return;
        }
        invoke(rewarded, "show", this);
    }
    private void grantAdFree(int hours) {
        sp.edit().putLong("af", System.currentTimeMillis() + hours * 3600_000L).apply();
        removeBanner();
        js("window.kopilkaAdFreeGranted(" + hours + ")");
    }

    @Override protected void onStart() {
        super.onStart();
        if (firstResume) firstResume = false; else tryAppOpen();
    }
    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        removeBanner();
        web.destroy();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface public void onAppReady() {
            h.post(new Runnable() { @Override public void run() { ensureBanner(); } });
        }
        @JavascriptInterface public void setConsent(final boolean v) {
            htmlConsent = v;
            sp.edit().putBoolean("consent", v).apply();
            h.post(new Runnable() { @Override public void run() { startAds(); } });
        }
        @JavascriptInterface public void setAdFree(final long ms) {
            sp.edit().putLong("af", ms).apply();
            h.post(new Runnable() { @Override public void run() {
                if (ms > System.currentTimeMillis()) removeBanner(); else ensureBanner();
            } });
        }
        @JavascriptInterface public void showRewardedAdFree() {
            h.post(new Runnable() { @Override public void run() { showRewarded(); } });
        }
    }
}
