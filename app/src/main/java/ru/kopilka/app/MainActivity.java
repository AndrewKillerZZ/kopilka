package ru.kopilka.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout root;
    private Object banner;
    private Object appOpen;
    private long appOpenLoadTime;
    private Object rewarded;
    private boolean htmlConsent;
    private boolean adsStarted;
    private boolean firstResume = true;
    private SharedPreferences sp;

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
            for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
                if (k.getParameterTypes().length == args.length) return k.newInstance(args);
            }
        } catch (Throwable e) { }
        return null;
    }
    private Object adRequest() {
        Object b = construct(C("com.yandex.mobile.ads.common.AdRequest$Builder"));
        return invoke(b, "build");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("ads", 0);
        TextView splash = new TextView(this);
        splash.setText("КОПИЛКА");
        splash.setTextSize(28);
        splash.setTextColor(0xFF8A5A2B);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(0xFFF0E3C8);
        setContentView(splash);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidAds");
        web.loadUrl("file:///android_asset/www/kopilka.html");
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        htmlConsent = sp.getBoolean("consent", false);
        if (htmlConsent) startAds();
    }

    private boolean adFree() { return sp.getLong("af", 0) > System.currentTimeMillis(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void startAds() {
        if (adsStarted || !htmlConsent) return;
        adsStarted = true;
        Class<?> ma = C("com.yandex.mobile.ads.common.MobileAds");
        Class<?> initListener = C("com.yandex.mobile.ads.common.InitCompletionListener");
        boolean initialized = false;
        if (ma != null && initListener != null) {
            Object proxy = Proxy.newProxyInstance(initListener.getClassLoader(),
                new Class<?>[]{initListener}, new InvocationHandler() {
                    @Override public Object invoke(Object p, Method m, Object[] a) { return null; }
                });
            initialized = staticInvoke(ma, "initialize", this, proxy) != null || true;
        }
        if (!initialized) staticInvoke(ma, "initialize", this);
        final android.os.Handler h = new android.os.Handler(getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                setContentView(root);
                ensureBanner();
                loadAppOpen();
                loadRewarded();
                web.evaluateJavascript(
                    "toast('Реклама: баннер=" + (banner != null) + ", open=" + (appOpen != null) + ", rewarded=" + (rewarded != null) + "')", null);
                h.postDelayed(new Runnable() {
                    @Override public void run() { tryAppOpen(); }
                }, 700);
            }
        }, 1500);
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
        p.topMargin = dp(8);
        root.addView((android.view.View) banner, p);
    }
    private void removeBanner() {
        if (banner != null) {
            invoke(banner, "destroy");
            root.removeView((android.view.View) banner);
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
                        if ("onRewarded".equals(m.getName())) {
                            sp.edit().putLong("rw_last", System.currentTimeMillis()).apply();
                            grantAdFree(48);
                        }
                        if ("onAdDismissed".equals(m.getName())) loadRewarded();
                        return null;
                    }
                });
            invoke(rewarded, "setAdEventListener", proxy);
        }
        invoke(rewarded, "loadAd", adRequest());
    }
    private void showRewarded() {
        Object loaded = invoke(rewarded, "isLoaded");
        if (!Boolean.TRUE.equals(loaded)) {
            loadRewarded();
            web.evaluateJavascript("window.kopilkaAdFreeUnavailable()", null);
            return;
        }
        invoke(rewarded, "show", this);
    }
    private void grantAdFree(int hours) {
        sp.edit().putLong("af", System.currentTimeMillis() + hours * 3600_000L).apply();
        removeBanner();
        web.evaluateJavascript("window.kopilkaAdFreeGranted(" + hours + ")", null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (firstResume) firstResume = false; else tryAppOpen();
    }
    @Override
    protected void onDestroy() {
        removeBanner();
        web.destroy();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface public void onAppReady() {
            runOnUiThread(new Runnable() { @Override public void run() { ensureBanner(); } });
        }
        @JavascriptInterface public void setConsent(final boolean v) {
            htmlConsent = v;
            sp.edit().putBoolean("consent", v).apply();
            runOnUiThread(new Runnable() { @Override public void run() { startAds(); } });
        }
        @JavascriptInterface public void setAdFree(final long ms) {
            sp.edit().putLong("af", ms).apply();
            runOnUiThread(new Runnable() { @Override public void run() {
                if (ms > System.currentTimeMillis()) removeBanner(); else ensureBanner();
            } });
        }
        @JavascriptInterface public void showRewardedAdFree() {
            runOnUiThread(new Runnable() { @Override public void run() { showRewarded(); } });
        }
    }
        }
