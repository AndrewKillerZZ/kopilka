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
import com.yandex.mobile.ads.appopenad.AppOpenAd;
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.InitCompletionListener;
import com.yandex.mobile.ads.common.MobileAds;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout root;
    private BannerAdView banner;
    private AppOpenAd appOpen;
    private long appOpenLoadTime;
    private RewardedAd rewarded;
    private boolean htmlConsent;
    private boolean adsStarted;
    private boolean firstResume = true;
    private SharedPreferences sp;

    static final String BANNER_ID = "demo-banner-yandex";
    static final String APP_OPEN_ID = "demo-appopenad-yandex";
    static final String REWARDED_ID = "demo-rewarded-yandex";

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

    private boolean adFree() {
        return sp.getLong("af", 0) > System.currentTimeMillis();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void startAds() {
        if (adsStarted || !htmlConsent) return;
        adsStarted = true;
        MobileAds.initialize(this, new InitCompletionListener() {
            @Override
            public void onInitializationCompleted() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setContentView(root);
                        ensureBanner();
                        loadAppOpen();
                        loadRewarded();
                        web.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                tryAppOpen();
                            }
                        }, 700);
                    }
                });
            }
        });
    }

    private void ensureBanner() {
        if (!adsStarted || adFree() || banner != null) return;
        banner = new BannerAdView(this);
        banner.setAdUnitId(BANNER_ID);
        banner.setAdSize(BannerAdSize.stickyWidth(this));
        banner.loadAd(new AdRequest.Builder().build());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        root.addView(banner, p);
    }

    private void removeBanner() {
        if (banner != null) {
            banner.destroy();
            root.removeView(banner);
            banner = null;
        }
    }

    private void loadAppOpen() {
        appOpen = new AppOpenAd(this);
        appOpen.setAdUnitId(APP_OPEN_ID);
        appOpen.setAdEventListener(new AppOpenAdEventListener() {
            @Override public void onAdShown() {}
            @Override public void onAdFailedToLoad(AdError e) {}
            @Override public void onAdDismissed() { loadAppOpen(); }
            @Override public void onAdClicked() {}
            @Override public void onAdImpression() {}
        });
        appOpen.loadAd(new AdRequest.Builder().build());
        appOpenLoadTime = System.currentTimeMillis();
    }

    private void tryAppOpen() {
        if (!adsStarted || adFree() || appOpen == null) return;
        long now = System.currentTimeMillis();
        if (now - appOpenLoadTime > 4 * 3600_000L) {
            appOpen = null;
            loadAppOpen();
            return;
        }
        if (now - sp.getLong("ao_last", 0) < 4 * 3600_000L) return;
        if (now - sp.getLong("rw_last", 0) < 10 * 60_000L) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(sp.getString("ao_day", ""))) {
            sp.edit().putString("ao_day", today).putInt("ao_n", 0).apply();
        }
        if (sp.getInt("ao_n", 0) >= 3) return;
        sp.edit().putLong("ao_last", now).putInt("ao_n", sp.getInt("ao_n", 0) + 1).apply();
        AppOpenAd ad = appOpen;
        appOpen = null;
        ad.show(this);
    }

    private void loadRewarded() {
        rewarded = new RewardedAd(this);
        rewarded.setAdUnitId(REWARDED_ID);
        rewarded.setAdEventListener(new RewardedAdEventListener() {
            @Override
            public void onRewarded(Reward r) {
                sp.edit().putLong("rw_last", System.currentTimeMillis()).apply();
                grantAdFree(48);
            }
            @Override public void onAdFailedToLoad(AdError e) {}
            @Override public void onAdDismissed() { loadRewarded(); }
            @Override public void onAdShown() {}
            @Override public void onAdClicked() {}
            @Override public void onAdImpression() {}
        });
        rewarded.loadAd(new AdRequest.Builder().build());
    }

    private void showRewarded() {
        if (rewarded == null || !rewarded.isLoaded()) {
            loadRewarded();
            web.evaluateJavascript("window.kopilkaAdFreeUnavailable()", null);
            return;
        }
        rewarded.show(this);
    }

    private void grantAdFree(int hours) {
        sp.edit().putLong("af", System.currentTimeMillis() + hours * 3600_000L).apply();
        removeBanner();
        web.evaluateJavascript("window.kopilkaAdFreeGranted(" + hours + ")", null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (firstResume) {
            firstResume = false;
        } else {
            tryAppOpen();
        }
    }

    @Override
    protected void onDestroy() {
        if (banner != null) banner.destroy();
        web.destroy();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface
        public void onAppReady() {
            runOnUiThread(new Runnable() {
                @Override public void run() { ensureBanner(); }
            });
        }

        @JavascriptInterface
        public void setConsent(final boolean v) {
            htmlConsent = v;
            sp.edit().putBoolean("consent", v).apply();
            runOnUiThread(new Runnable() {
                @Override public void run() { startAds(); }
            });
        }

        @JavascriptInterface
        public void setAdFree(final long ms) {
            sp.edit().putLong("af", ms).apply();
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (ms > System.currentTimeMillis()) removeBanner();
                    else ensureBanner();
                }
            });
        }

        @JavascriptInterface
        public void showRewardedAdFree() {
            runOnUiThread(new Runnable() {
                @Override public void run() { showRewarded(); }
            });
        }
    }
}
