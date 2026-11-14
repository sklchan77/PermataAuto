package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import my.app.permata.PermataApplication;
import my.app.permata.auto.AccessibilityEventDispatcherService;
import my.app.permata.auto.EventDispatcher;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.function.IntObjectFunction;
import my.app.utils.log.Log;
import my.app.utils.ui.fragment.ActivityFragment;

public class KeyEventHandler {

    private static final int DBL_CLICK_INTERVAL = 500;
    private static final int LONG_CLICK_INTERVAL = 1000;

    // FIX #4: Manual Steering Priority Tracker
    public static final int TARGET_AUTO = 0;
    public static final int TARGET_MIRROR = 1;
    public static final int TARGET_WEB = 2;
    public static volatile int manualTouchFocus = TARGET_AUTO;

    private static volatile Worker worker;
    private static volatile long lastGlobalActionTime = 0L;
    
    private static final AtomicBoolean isHardwareSwiping = new AtomicBoolean(false);
    
    // GROUP 1: The Bullies. These apps ignore BT keys and steal the media session.
    private static final Set<String> STUBBORN_SCROLLERS = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", // TikTok Global
            "com.ss.android.ugc.trill", // TikTok Asia
            "com.instagram.android",    // Instagram
            "com.snapchat.android"      // Snapchat
    ));

    // GROUP 2: The Good Citizens. These apps natively support BT Next/Prev keys.
    private static final Set<String> NATIVE_BT_SCROLLERS = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme",     // Douyin (CN)
            "com.facebook.katana",          // Facebook
            "app.revanced.android.youtube", // YouTube Vanced
            "com.google.android.youtube",   // YouTube
            "com.reddit.frontpage",         // Reddit
            "com.twitter.android",          // X/Twitter
            "tv.danmaku.bili"               // Bilibili
    ));
    
    public static boolean isStubbornScroller(String pkgName) {
        return pkgName != null && STUBBORN_SCROLLERS.contains(pkgName);
    }

    public static boolean isNativeBtScroller(String pkgName) {
        return pkgName != null && NATIVE_BT_SCROLLERS.contains(pkgName);
    }
    
    public static boolean isScrollerPackage(String pkgName) {
        return isStubbornScroller(pkgName) || isNativeBtScroller(pkgName);
    }
    
    private static final Map<Class<?>, Method> webViewMethodCache = new ConcurrentHashMap<>();
    private static final Map<View, Long> scrollTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

    private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
            "let res = 'Discovery [Layer 2]: JS Registry Miss (No custom formatting applied)'; " +
            "const injectGlobalWipe = function() { " +
            "  if(!document.getElementById('permata-god-mode-css')) { " +
            "    const style = document.createElement('style'); " +
            "    style.id = 'permata-god-mode-css'; " +
            "    style.innerHTML = ` " +
            "      xg-controls, .xgplayer-controls, " +
            "      .xg-right-bar, .xg-left-bar, " +
            "      .video-info-container, .right-container, " +
            "      .bottom-container, .xgplayer-bottom, " +
            "      [class*=\"sidebar\"], [class*=\"video-info\"], " +
            "      [class*=\"action-bar\"], [class*=\"author-info\"], " +
            "      [class*=\"comment\"], .account-info, .danmaku-container, " +
            "      .login-mask-enter-done, .dy-account-close, " +
            "      [class*=\"bottom-bar\"], [class*=\"Prompt\"], " +
            "      [class*=\"download-btn\"], [class*=\"app-open\"], " +
            "      .XPromoPopup, .AppBanner, [class*=\"Banner\"], " +
            "      .UnauthBox, .box_layout, [id*=\"login\"], " +
            "      .open-app-bar, .login-dialog { " +
            "          display: none !important; " +
            "          opacity: 0 !important; " +
            "          visibility: hidden !important; " +
            "          pointer-events: none !important; " +
            "          height: 0 !important; " +
            "          width: 0 !important; " +
            "      } " +
            "    `; " +
            "    document.head.appendChild(style); " +
            "    return 'Global CSS Stylesheet Injected Successfully.'; " +
            "  } " +
            "  return 'Global CSS Stylesheet Already Exists.'; " +
            "  }; " +
            "const injectSpecificWipe = function(customCss, id) { " +
            "  if(!document.getElementById(id)) { " +
            "    const style = document.createElement('style'); " +
            "    style.id = id; " +
            "    style.innerHTML = customCss; " +
            "    document.head.appendChild(style); " +
            "    return 'Custom CSS (' + id + ') Injected.'; " +
            "  } " +
            "  return 'Custom CSS (' + id + ') Already Exists.'; " +
            "}; " +
            "const registry=[" +
            "    {name:\"douyin\",match:/douyin/,execute:function(){" +
            "      return 'DOUYIN: Standard media UI wipe executed.'; " +
            "    }}," +
            "    {name:\"tiktok\",match:/tiktok/,execute:function(){" +
            "      var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [data-e2e=\"video-desc\"], [data-e2e=\"video-music\"], [data-e2e=\"sponsored-label\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivVideoInfoContainer\"], [class*=\"DivTextInfoContainer\"], [class*=\"DivVideoAuthorInfo\"], [class*=\"DivBottomContainer\"] { display: none !important; opacity: 0 !important; pointer-events: none !important; width: 0 !important; height: 0 !important; } [class*=\"DivMediaCardOverlay\"], [class*=\"DivOverlayBottomContent\"], [class*=\"DivCreatorInfoContainer\"], [class*=\"BasePlayerContainer\"]::after { pointer-events: none !important; } '; " +
            "      if (!window.__ttUISurgery) { " +
            "        window.__ttUISurgery = true; " +
            "        setInterval(function(){ " +
            "          var els = document.querySelectorAll('span, div, button'); " +
            "          els.forEach(function(e){ " +
            "            var t = e.innerText ? e.innerText.trim() : ''; " +
            "            if((t === 'Follow' || t === 'Sponsored') && e.getBoundingClientRect().left < window.innerWidth / 2) { e.style.display = 'none'; } " +
            "          }); " +
            "        }, 500); " +
            "      } " +
            "      return 'TIKTOK: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(ttCss, 'permata-tt-css'); " +
            "    }}," +
            "    {name:\"instagram\",match:/instagram/,execute:function(){" +
            "      var igCss = ' header, nav, [role=\"navigation\"] { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; } body, html { overflow: auto !important; touch-action: pan-y !important; } '; " +
            "      if (!window.__igAudioFix) { " +
            "        window.__igAudioFix = true; " +
            "        setInterval(function(){ " +
            "          var wh = window.innerHeight; " +
            "          var vids = document.querySelectorAll('video'); " +
            "          var maxH = 0; var primary = null; " +
            "          vids.forEach(function(v){ " +
            "            var r = v.getBoundingClientRect(); " +
            "            var vis = Math.max(0, Math.min(r.bottom, wh) - Math.max(r.top, 0)); " +
            "            if(vis > maxH) { maxH = vis; primary = v; } " +
            "          }); " +
            "          vids.forEach(function(v){ " +
            "            if(v !== primary && !v.paused) { v.pause(); v.muted = true; } " +
            "          }); " +
            "          if(primary && primary.muted) { primary.muted = false; primary.volume = 1.0; } " +
            "          var els = document.querySelectorAll('span, div, button, a'); " +
            "          els.forEach(function(e){ " +
            "            var t = e.innerText ? e.innerText.trim() : ''; " +
            "            if(t === 'Follow' && e.getBoundingClientRect().left < window.innerWidth / 2) { e.style.display = 'none'; } " +
            "          }); " +
            "          var imgs = document.querySelectorAll('img, image'); " +
            "          imgs.forEach(function(img){ " +
            "            var r = img.getBoundingClientRect(); " +
            "            if(r.width >= 20 && r.width <= 60 && r.left < 100 && r.top > wh * 0.2) { " +
            "              var p = img.closest('a') || img; p.style.display = 'none'; " +
            "            } " +
            "          }); " +
            "        }, 400); " +
            "      } " +
            "      return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css') + ' | Spatial Audio, Auto-Unmute & UI Surgery Armed.'; " +
            "    }}," +
            "    {name:\"facebook\",match:/facebook/,execute:function(){" +
            "      if (!window.__fbUISurgery) { " +
            "        window.__fbUISurgery = true; " +
            "        setInterval(function(){ " +
            "          var wh = window.innerHeight; var ww = window.innerWidth; " +
            "          var vids = document.querySelectorAll('video'); " +
            "          var maxH = 0; var primary = null; " +
            "          vids.forEach(function(v){ " +
            "            var r = v.getBoundingClientRect(); " +
            "            var vis = Math.max(0, Math.min(r.bottom, wh) - Math.max(r.top, 0)); " +
            "            if(vis > maxH) { maxH = vis; primary = v; } " +
            "          }); " +
            "          if(primary && primary.muted) { primary.muted = false; primary.volume = 1.0; } " +
            "          var els = document.querySelectorAll('span, div, a, button'); " +
            "          els.forEach(function(e){ " +
            "            var r = e.getBoundingClientRect(); " +
            "            if(r.width > ww * 0.3 || r.height > wh * 0.3) return; " +
            "            var t = e.innerText ? e.innerText.trim() : ''; " +
            "            if((t === 'Follow' || t.includes('Original audio') || t === 'Sponsored') && r.left < ww / 2 && r.top > wh * 0.3) { " +
            "              e.style.opacity = '0'; e.style.pointerEvents = 'none'; " +
            "            } " +
            "          }); " +
            "          var texts = document.querySelectorAll('div[dir=\"auto\"], span[dir=\"auto\"]'); " +
            "          texts.forEach(function(txt){ " +
            "            var r = txt.getBoundingClientRect(); " +
            "            if (r.width > ww * 0.3 || r.height > wh * 0.3) return; " +
            "            if(r.left >= 0 && r.left < ww * 0.6 && r.top > wh * 0.5 && r.width > 0 && r.height > 0) { " +
            "              var t = txt.innerText ? txt.innerText.trim() : ''; " +
            "              if(t.length > 0 && r.left < ww * 0.4) { txt.style.opacity = '0'; txt.style.pointerEvents = 'none'; } " +
            "            } " +
            "          }); " +
            "          var imgs = document.querySelectorAll('image, img'); " +
            "          imgs.forEach(function(img){ " +
            "            var r = img.getBoundingClientRect(); " +
            "            if(r.width >= 20 && r.width <= 60 && r.left < 100 && r.top > wh * 0.5) { " +
            "              img.style.opacity = '0'; img.style.pointerEvents = 'none'; " +
            "            } " +
            "          }); " +
            "        }, 500); " +
            "      } " +
            "      return 'FACEBOOK: Surgery precision UI wipe & Auto-Unmute armed.'; " +
            "    }}," +
            "    {name:\"youtube\",match:/(youtube|youtu)/,execute:function(){" +
            "      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)ad.click();" +
            "      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
            "      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))b.click();});" +
            "      return 'YOUTUBE: Handled standard media skip.'; " +
            "    }}," +
            "    {name:\"bilibili\",match:/bilibili/,execute:function(){" +
            "      let pl=document.querySelector('.mplayer-play');if(pl&&pl.classList.contains('play'))pl.click();" +
            "      return 'BILIBILI: ' + injectGlobalWipe(); " +
            "    }}," +
            "    {name:\"reddit\",match:/reddit/,execute:function(){" +
            "      if(document.body&&window.getComputedStyle(document.body).overflow==='hidden') document.body.style.overflow='auto';" +
            "      return 'REDDIT: ' + injectGlobalWipe(); " +
            "    }}," +
            "    {name:\"moj\",match:/(mojapp|sharechat)/,execute:function(){" +
            "      if(document.body) document.body.style.overflow='auto';" +
            "      return 'MOJ: ' + injectGlobalWipe(); " +
            "    }}" +
            "  ];" +
            "  window.__permataActive = registry.find(p=>p.match.test(window.location.hostname));" +
            "  if (window.__permataActive) { " +
            "    res = 'Discovery [Layer 2]: Match -> ' + window.__permataActive.name; " +
            "    try { res += ' || ' + window.__permataActive.execute(); } catch(e){} " +
            "  } else { " +
            "    res = 'Discovery [Layer 2]: Generic Host'; " +
            "    var commonCss = ' header, footer, nav, aside, #cookie-notice, .cookie-banner, [id*=\"cookie\"], [class*=\"cookie\"], [id*=\"popup\"], [class*=\"popup\"], .floating-action-button { display: none !important; } '; " +
            "    try { let execRes = injectSpecificWipe(commonCss, 'permata-common-css'); res += ' || COMMON: ' + execRes; } catch(e){} " +
            "  } " +
            "  return res;" +
            "})();";

    public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event,
                                             IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        return handleKeyEvent(cb, null, event, defaultHandler);
    }

    public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event,
                                             IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        return handleKeyEvent(activity.getMediaSessionCallback(), activity, event, defaultHandler);
    }

    private static boolean handleKeyEvent(MediaSessionCallback cb,
                                                @Nullable MainActivityDelegate activity, KeyEvent event,
                                                IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        
        if (event.isCanceled()) {
            worker = null;
            return defaultHandler.apply(event.getKeyCode(), event);
        }

        if (worker != null) {
            if (worker.handle(event)) return true;
            worker = null;
            return false;
        }

        var code = event.getKeyCode();

        if (code == KeyEvent.KEYCODE_BACK) {
            if (AccessibilityEventDispatcherService.isInjectingBack) {
                return defaultHandler.apply(code, event);
            }
            if (PermataApplication.get().isMirroringMode()) {
                long currentUptime = android.os.SystemClock.uptimeMillis();
                if (currentUptime - lastGlobalActionTime < 500) {
                    return true; 
                }
                lastGlobalActionTime = currentUptime;
                AccessibilityEventDispatcherService.dispatchUniversalBack();
                return true; 
            }
            return defaultHandler.apply(code, event);
        }

        MainActivityDelegate targetActivity = activity;
        if (targetActivity == null && cb != null) {
            if (cb.getAssistant() instanceof MainActivityDelegate) {
                targetActivity = (MainActivityDelegate) cb.getAssistant();
            }
        }

        final MainActivityDelegate finalTargetActivity = targetActivity;

        if (event.getAction() == ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                
                long currentUptime = android.os.SystemClock.uptimeMillis();
                if (currentUptime - lastGlobalActionTime < 250) {
                    return true;
                }

                boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
                String activePkg = AccessibilityEventDispatcherService.getLastActivePackage();
                ActivityFragment activeFragment = (finalTargetActivity != null) ? finalTargetActivity.getActiveFragment() : null;
                
                boolean isWebBrowser = activeFragment != null && 
                        activeFragment.getClass().getName().endsWith("WebBrowserFragment") && 
                        !activeFragment.getClass().getName().endsWith("YoutubeFragment");

                // FIX #4: Absolute override based on last physical touch
                boolean forceMirror = (manualTouchFocus == TARGET_MIRROR);
                boolean forceWeb = (manualTouchFocus == TARGET_WEB);

                // PRIORITY 1: MIRRORED NATIVE SCROLLER
                if (forceMirror || (PermataApplication.get().isMirroringMode() && activePkg != null && !activePkg.startsWith("my.app.permata"))) {
                    lastGlobalActionTime = currentUptime;
                    
                    my.app.permata.auto.MirrorDisplay md = my.app.permata.auto.MirrorDisplay.get();
                    if (md != null) {
                        int width = 1000;
                        int height = 1000;
                        try {
                            if (finalTargetActivity != null && finalTargetActivity.getResources() != null) {
                                android.util.DisplayMetrics metrics = finalTargetActivity.getResources().getDisplayMetrics();
                                width = metrics.widthPixels;
                                height = metrics.heightPixels;
                            }
                        } catch (Exception ignored) {}

                        float actionX = width * 0.50f;
                        float centerY = height * 0.50f;
                        float span = height * 0.30f;
                        
                        float yStart = (!isNext) ? (centerY - span / 2f) : (centerY + span / 2f);
                        float yEnd = (!isNext) ? (centerY + span / 2f) : (centerY - span / 2f);

                        long now = android.os.SystemClock.uptimeMillis();
                        md.motionEvent(now, now, MotionEvent.ACTION_DOWN, actionX, yStart);
                        md.motionEvent(now, now + 50, MotionEvent.ACTION_MOVE, actionX, centerY);
                        md.motionEvent(now, now + 100, MotionEvent.ACTION_UP, actionX, yEnd);
                    }
                    return true;
                }

                // PRIORITY 2: PERMATA WEB BROWSER
                if (forceWeb || isWebBrowser) {
                    lastGlobalActionTime = currentUptime;
                    if (finalTargetActivity != null) {
                        finalTargetActivity.post(() -> {
                            if (finalTargetActivity.getWindow() != null) {
                                Context ctx = finalTargetActivity.getWindow().getContext();
                                while (ctx instanceof ContextWrapper) {
                                    if (ctx instanceof Activity) break;
                                    ctx = ((ContextWrapper) ctx).getBaseContext();
                                }
                                if (ctx instanceof Activity) {
                                    Activity act = (Activity) ctx;
                                    if (act.isFinishing() || act.isDestroyed()) return;
                                }
                            }

                            WebView resolvedWebView = scanFragmentsForWebView(activeFragment);
                            boolean isExplicitWebFragment = (resolvedWebView != null);

                            if (resolvedWebView == null || !resolvedWebView.isShown()) {
                                resolvedWebView = findTopVisibleWebView(finalTargetActivity);
                            }

                            if (resolvedWebView != null && resolvedWebView.isShown()) {
                                boolean hasTouchFocus = resolvedWebView.hasFocus();
                                boolean isFullScreenSize = false;
                                
                                if (finalTargetActivity.getWindow() != null && finalTargetActivity.getWindow().getDecorView() != null) {
                                    int screenHeight = finalTargetActivity.getWindow().getDecorView().getHeight();
                                    if (screenHeight > 0) {
                                        isFullScreenSize = resolvedWebView.getHeight() > (screenHeight * 0.5);
                                    }
                                }

                                if (isExplicitWebFragment || hasTouchFocus || isFullScreenSize) {
                                    String currentUrl = resolvedWebView.getUrl();
                                    String host = "unknown";
                                    boolean isMediaHost = false;
                                    boolean isDouyin = false;
                                    boolean isTikTok = false;
                                    
                                    if (currentUrl != null) {
                                        try {
                                            host = Uri.parse(currentUrl).getHost();
                                            if (host != null) {
                                                if (host.startsWith("www.")) host = host.substring(4);
                                                String h = host.toLowerCase();
                                                isTikTok = h.contains("tiktok");
                                                isDouyin = h.contains("douyin");
                                                isMediaHost = isTikTok || isDouyin || h.contains("instagram") || h.contains("youtube") || h.contains("facebook") || h.contains("bilibili");
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                    View touchTargetView = isTikTok ? resolvedWebView : findTopmostTouchTarget(finalTargetActivity, resolvedWebView);
                                    smartScrollWebView(resolvedWebView, touchTargetView, !isNext, "[Host: " + host + "] ", isMediaHost, isDouyin, isTikTok);
                                }
                            }
                        });
                    }
                    return true;
                }

                // PRIORITY 3: VIRTUAL DISPLAY FALLBACK (General Mirroring Intercept)
                if (PermataApplication.get().isMirroringMode()) {
                    lastGlobalActionTime = currentUptime;
                    my.app.permata.auto.MirrorDisplay md = my.app.permata.auto.MirrorDisplay.get();
                    if (md != null) {
                        int width = 1000;
                        int height = 1000;
                        try {
                            if (finalTargetActivity != null && finalTargetActivity.getResources() != null) {
                                android.util.DisplayMetrics metrics = finalTargetActivity.getResources().getDisplayMetrics();
                                width = metrics.widthPixels;
                                height = metrics.heightPixels;
                            }
                        } catch (Exception ignored) {}

                        float actionX = width * 0.50f;
                        float centerY = height * 0.50f;
                        float span = height * 0.30f;
                        
                        float yStart = (!isNext) ? (centerY - span / 2f) : (centerY + span / 2f);
                        float yEnd = (!isNext) ? (centerY + span / 2f) : (centerY - span / 2f);

                        long now = android.os.SystemClock.uptimeMillis();
                        md.motionEvent(now, now, MotionEvent.ACTION_DOWN, actionX, yStart);
                        md.motionEvent(now, now + 50, MotionEvent.ACTION_MOVE, actionX, centerY);
                        md.motionEvent(now, now + 100, MotionEvent.ACTION_UP, actionX, yEnd);
                    }
                    return true;
                }

                if (activePkg != null && !activePkg.startsWith("my.app.permata")) {
                    if (isStubbornScroller(activePkg)) {
                        executeNativeAppSwipe(!isNext, activePkg);
                        return true; 
                    } else if (isNativeBtScroller(activePkg)) {
                        return defaultHandler.apply(code, event);
                    }
                }

                return defaultHandler.apply(code, event);
            }
        }

        var k = Key.get(code);
        if (k == null) return defaultHandler.apply(code, event);

        if (!k.isMedia() && (finalTargetActivity != null) && (finalTargetActivity.getCurrentFocus() instanceof EditText)) {
            return defaultHandler.apply(code, event);
        }

        var dblClickAction = k.getDblClickAction();
        if (dblClickAction == null) return defaultHandler.apply(code, event);

        var action = event.getAction();
        if (action == ACTION_MULTIPLE) {
            performAction(dblClickAction, cb, finalTargetActivity, uptimeMillis());
            return true;
        }
        
        if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

        var clickAction = k.getClickAction();
        if (clickAction == null) return defaultHandler.apply(code, event);
        
        var longClickAction = k.getLongClickAction();
        if (longClickAction == null) return defaultHandler.apply(code, event);

        if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
                ((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
            performAction(clickAction, cb, finalTargetActivity, uptimeMillis());
            return true;
        }

        worker = new Worker(cb, finalTargetActivity, k, clickAction, dblClickAction, longClickAction);
        return true;
    }

    private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
        try {
            Class<?> fragClass = activeFragment.getClass();
            Method getWebViewMethod = webViewMethodCache.get(fragClass);
            
            if (getWebViewMethod == null) {
                getWebViewMethod = fragClass.getMethod("getWebView");
                webViewMethodCache.put(fragClass, getWebViewMethod);
            }
            
            Object result = getWebViewMethod.invoke(activeFragment);
            if (result instanceof WebView) return (WebView) result;
        } catch (Exception e) {
            Log.e(e, "[REACTION] Discovery: Failed to scan for WebView.");
        }
        return null;
    }

    private static WebView findTopVisibleWebView(MainActivityDelegate activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decorView = activity.getWindow().getDecorView();
        return findWebViewRecursively(decorView);
    }

    private static WebView findWebViewRecursively(View view) {
        if (view instanceof WebView && view.isShown() && view.getVisibility() == View.VISIBLE && view.getWidth() > 0) {
            if (view.getAlpha() > 0.1f) {
                return (WebView) view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) { 
                WebView res = findWebViewRecursively(vg.getChildAt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    private static View findTopmostTouchTarget(MainActivityDelegate activity, WebView baseWebView) {
        if (activity == null || activity.getWindow() == null) return baseWebView;
        
        View decorView = activity.getWindow().getDecorView();
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();
        
        if (screenWidth == 0 || screenHeight == 0) return baseWebView;
        
        long totalScreenArea = (long) screenWidth * screenHeight;
        long minValidArea = (long) (totalScreenArea * 0.20f);
        int minValidHeight = (int) (screenHeight * 0.80f);
        
        View topCandidate = scanHighestVisibleChild(decorView, minValidArea, minValidHeight);
        
        if (topCandidate != null && topCandidate.isShown()) {
            return topCandidate;
        }
        return baseWebView;
    }

    private static View scanHighestVisibleChild(View parent, long minValidArea, int minValidHeight) {
        if (parent == null || !parent.isShown() || parent.getVisibility() != View.VISIBLE || parent.getAlpha() < 0.1f) {
            return null;
        }
        
        if (parent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) parent;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                View child = vg.getChildAt(i);
                View target = scanHighestVisibleChild(child, minValidArea, minValidHeight);
                
                if (target != null) {
                    return target;
                }
            }
        }
        
        long viewArea = (long) parent.getWidth() * parent.getHeight();
        if (viewArea >= minValidArea || parent.getHeight() >= minValidHeight) {
            String className = parent.getClass().getSimpleName();
            boolean isGenericWrapper = className.equals("FrameLayout") || 
                                       className.equals("LinearLayout") || 
                                       className.equals("RelativeLayout") || 
                                       className.equals("DecorView") ||
                                       className.equals("ViewGroup") ||
                                       className.equals("ViewStub");
                                       
            if (!isGenericWrapper) {
                return parent;
            }
        }
        
        return null;
    }

    private static void executeNativeAppSwipe(boolean up, String pkgName) {
        if (!isHardwareSwiping.compareAndSet(false, true)) {
            return;
        }
        try {
            boolean success = AccessibilityEventDispatcherService.dispatchGodModeSwipe(up);
            if (!success) {
                Log.w("[NATIVE_SWIPE] Accessibility Gesture failed or service unavailable.");
            }
        } finally {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                isHardwareSwiping.set(false);
            }, 150);
        }
    }

    private static void executeHardwareTouchSwipe(final View touchTarget, boolean up, final String hostTag, final String contextLog, boolean isTikTok) {
        if (touchTarget == null || !touchTarget.isAttachedToWindow()) return;
        
        if (!isHardwareSwiping.compareAndSet(false, true)) {
            return;
        }

        final android.os.Handler guaranteedHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        final float actionX = touchTarget.getWidth() * 0.50f; 
        float centerY = touchTarget.getHeight() * 0.50f;
        
        float span = touchTarget.getHeight() * 0.20f; 
        
        final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
        final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

        try {
            final long startTime = android.os.SystemClock.uptimeMillis();
            
            MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
            eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
            touchTarget.dispatchTouchEvent(eventDown);
            eventDown.recycle();

            final int stepCount = 8; 
            final long swipeDuration = up ? 90 : 70; 
            
            for (int i = 1; i <= stepCount; i++) {
                final float linearT = (float) i / stepCount;
                final float currentY = yStart + (yEnd - yStart) * linearT;
                final long moveTime = startTime + (long) (swipeDuration * linearT);
                
                guaranteedHandler.postDelayed(() -> {
                    if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
                        MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
                        eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
                        touchTarget.dispatchTouchEvent(eventMove);
                        eventMove.recycle();
                    }
                }, (long) (swipeDuration * linearT));
            }

            guaranteedHandler.postDelayed(() -> {
                try {
                    if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
                        long endTime = startTime + swipeDuration + 10;
                        MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
                        eventUp.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
                        touchTarget.dispatchTouchEvent(eventUp);
                        eventUp.recycle();
                    }
                } finally {
                    isHardwareSwiping.set(false);
                }
            }, swipeDuration + 10);

        } catch (Exception e) {
            isHardwareSwiping.set(false); 
        }
    }

    private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String hostTag, boolean isMediaHost, boolean isDouyin, boolean isTikTok) {
        if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) return;

        long now = android.os.SystemClock.uptimeMillis();
        Long lastClickTime = scrollTimestamps.get(wv);
        if (lastClickTime == null) lastClickTime = 0L;
        
        if (now - lastClickTime < 250) {
            return;
        }
        scrollTimestamps.put(wv, now); 
        touchTarget.requestFocus();
        
        if (wv.getSettings().getJavaScriptEnabled()) {

            if (isDouyin) {
                String expDouyinFsJs = "(function() {" +
                        "  try {" +
                        "    if (document.getElementById('permata-douyin-fs')) {" +
                        "      return 'TELEMETRY_CHECK: FullScreen State ALREADY_ACTIVE. Skipping injection.';" +
                        "    }" +
                        "    var style = document.createElement('style');" +
                        "    style.id = 'permata-douyin-fs';" +
                        "    style.innerHTML = `" +
                        "      [data-e2e=\"douyin-header\"], #douyin-header, .douyin-header, " +
                        "      [data-e2e=\"douyin-navigation\"], .douyin-navigation, #douyin-navigation {" +
                        "        display: none !important; width: 0 !important; height: 0 !important;" +
                        "        opacity: 0 !important; pointer-events: none !important; position: absolute !important;" +
                        "      }" +
                        "      body, html, #root, #app, [data-e2e=\"main-content\"], .douyin-right-container, [id*=\"right-container\"] {" +
                        "        margin: 0 !important; padding: 0 !important;" +
                        "        width: 100vw !important; max-width: 100vw !important;" +
                        "        min-width: 0 !important; " +
                        "        left: 0 !important; right: 0 !important;" +
                        "      }" +
                        "      .playerContainer, .xgplayer, .xg-video-container, [data-e2e=\"feed-active-video\"], .xgplayer-video-wrap {" +
                        "        height: 100vh !important; width: 100vw !important;" +
                        "        max-height: 100vh !important; max-width: 100vw !important;" +
                        "        background-color: #000000 !important;" +
                        "        pointer-events: none !important; touch-action: pan-y !important;" +
                        "        border-radius: 0 !important; margin: 0 !important;" +
                        "      }" +
                        "      .xgplayer video, video {" +
                        "        height: 100% !important; width: 100% !important;" +
                        "        object-fit: contain !important; background-color: transparent !important;" +
                        "        pointer-events: none !important;" +
                        "      }" +
                        "      .xgplayer-poster, .poster-bg {" +
                        "        display: none !important; opacity: 0 !important;" +
                        "      }" +
                        "    `;" +
                        "    document.head.appendChild(style);" +
                        "    return 'TELEMETRY_CHECK: FullScreen State ARMED_SUCCESS. Precision CSS Injected.';" +
                        "  } catch(e) { return 'TELEMETRY_ERROR: ' + e.message; }" +
                        "})();";

                wv.evaluateJavascript(expDouyinFsJs, value -> {});
            }

            wv.postDelayed(() -> {
                if (!wv.isAttachedToWindow()) return;
                wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, value -> {});
            }, 220); 

            if (!isMediaHost) {
                String generalJsScript = "(function() {" +
                        "  try {" +
                        "    var amount = " + (!up) + " ? window.innerHeight * 0.85 : -window.innerHeight * 0.85;" +
                        "    window.scrollBy({ top: amount, behavior: 'smooth' });" +
                        "    return 'Scroll: General Webpage Smooth Scroll Executed.';" +
                        "  } catch (err) { return 'Scroll Error: ' + err.message; }" +
                        "})();";
                wv.evaluateJavascript(generalJsScript, value -> {});
                
                int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
                wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
                wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
            }
        }

        wv.postDelayed(() -> {
            if (!wv.isAttachedToWindow() || touchTarget == null || !touchTarget.isAttachedToWindow()) return;
            
            if (isMediaHost) {
                executeHardwareTouchSwipe(touchTarget, up, hostTag, "Global Hardware Swipe", isTikTok);
            }
        }, 100);
    }

    private static void performAction(Action action, MediaSessionCallback cb,
                                        @Nullable MainActivityDelegate activity, long timestamp) {
        worker = null;
        action.getHandler().handle(cb, activity, timestamp);
    }

    private static final class Worker implements Runnable {
        private final MediaSessionCallback cb;
        private final java.lang.ref.WeakReference<MainActivityDelegate> activityRef;
        private final Key key;
        private final Action clickAction;
        private final Action dblClickAction;
        private final Action longClickAction;
        private final long time;
        private long longClickTime;
        private boolean up;

        Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
                     Action clickAction, Action dblClickAction, Action longClickAction) {
            this.cb = cb;
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
            this.key = key;
            this.clickAction = clickAction;
            this.dblClickAction = dblClickAction;
            this.longClickAction = longClickAction;
            time = longClickTime = uptimeMillis();
            sched(DBL_CLICK_INTERVAL);
        }

        @Override
        public void run() {
            if (worker != this) return;
            if (up) {
                handle(clickAction);
                return;
            }

            long now = uptimeMillis();
            long diff = now - longClickTime;

            if (diff < LONG_CLICK_INTERVAL) {
                sched(LONG_CLICK_INTERVAL - diff);
            } else if (diff > 15000) { 
                worker = null;
            } else {
                longClickTime = time;
                handle(longClickAction);
                worker = this;
                sched(LONG_CLICK_INTERVAL);
            }
        }

        boolean handle(KeyEvent e) {
            if (e.getKeyCode() != key.getCode()) return false;

            switch (e.getAction()) {
                case ACTION_DOWN -> {
                    if (!up) {
                        if ((longClickAction == clickAction) || (longClickAction == Action.NONE)) {
                            handle(clickAction);
                        }
                    }
                    return true;
                }
                case ACTION_UP -> {
                    long holdTime = uptimeMillis() - time;

                    if (holdTime <= DBL_CLICK_INTERVAL) {
                        if (up) {
                            handle(dblClickAction);
                        } else if (dblClickAction == clickAction) {
                            handle(clickAction);
                        } else {
                            up = true;
                        }
                    } else if (holdTime >= LONG_CLICK_INTERVAL) {
                        worker = null;
                    } else {
                        worker = null;
                        if (longClickTime == time) {
                            handle(clickAction);
                        }
                    }
                    return true;
                }
                case ACTION_MULTIPLE -> {
                    handle(dblClickAction);
                    return true;
                }
            }
            return false;
        }

        private void handle(Action action) {
            MainActivityDelegate activity = activityRef.get();
            performAction(action, cb, activity, time);
        }

        private void sched(long delay) {
            MainActivityDelegate activity = activityRef.get();
            var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
            handler.postDelayed(this, delay);
        }
    }
}