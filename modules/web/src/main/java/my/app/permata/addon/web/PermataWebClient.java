package my.app.permata.addon.web;

import static my.app.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;

import my.app.permata.addon.web.yt.YoutubeFragment;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.function.BooleanConsumer;
import my.app.utils.log.Log;

/**
 * Production-hardened WebClient.
 * Features: JS debounce, God-Mode DOM Surgery (Decoder Release), 
 * Clean 3.0s Cold-Start Grace Period, and Per-Media Unmute Enforcer.
 */
public class PermataWebClient extends WebViewClientCompat {
    BooleanConsumer loading;

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (loading != null) {
            loading.accept(true);
        } else {
            MainActivityDelegate.getActivityDelegate(view.getContext())
                    .onSuccess(a -> a.setContentLoading(new Promise<>()));
        }
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        PermataWebView v = (PermataWebView) view;
        FutureSupplier<MainActivityDelegate> f =
                MainActivityDelegate.getActivityDelegate(v.getContext());
        f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

        if (loading != null) {
            loading.accept(false);
            loading = null;
        }

        super.onPageFinished(view, url);
        ((PermataWebView) view).hideKeyboard();
        v.pageLoaded(url);
        f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));

        if (!isYoutubeUri(Uri.parse(url))) {
            // 1. Hardware MediaSession mapping for steering wheel controls
            String js = "try { " +
                "  if (!window.__permataFrame) { " +
                "    window.__permataFrame = document.createElement('iframe'); " +
                "    window.__permataFrame.style.display = 'none'; " +
                "    document.body.appendChild(window.__permataFrame); " +
                "  } " +
                "  if (navigator.mediaSession) {" +
                "    navigator.mediaSession.setActionHandler('nexttrack', () => { window.__permataFrame.src = 'permata://scroll/next'; }); " +
                "    navigator.mediaSession.setActionHandler('previoustrack', () => { window.__permataFrame.src = 'permata://scroll/prev'; }); " +
                "  }" +
                "} catch(e) {}";
            view.evaluateJavascript(js, null);

            // 2. HARDENED OBSERVER WITH 3.0s GRACE PERIOD
            String mediaObserverJs = "(function() { " +
                "  if (window.__permataMediaObserverAttached) return; " +
                "  window.__permataMediaObserverAttached = true; " +
                "  var activeVideo = null; " +
                "  var playDebounce = null; " +
                "  var pauseDebounce = null; " +
                "  var isColdStartGrace = true; " +
                "  var isDouyinHost = window.location.hostname.indexOf('douyin.com') !== -1; " +
                "  " +
                "  /* --- CLEAN COLD START GRACE PERIOD --- */ " +
                "  /* Immunity window (3.0s) strictly for initialization and settlement */ " +
                "  setTimeout(function() { isColdStartGrace = false; }, 3000); " +
                "  " +
                "  function onPlayHandler() { " +
                "      if (playDebounce) clearTimeout(playDebounce); " +
                "      playDebounce = setTimeout(function() { " +
                "          if(window.AndroidMediaBridge) window.AndroidMediaBridge.onMediaPlay(); " +
                "      }, 150); " +
                "  } " +
                "  function onPauseHandler() { " +
                "      if (isColdStartGrace) return; /* Suppress accidental pauses during initialization */ " +
                "      if (pauseDebounce) clearTimeout(pauseDebounce); " +
                "      pauseDebounce = setTimeout(function() { " +
                "          if (this === activeVideo && window.AndroidMediaBridge) { " +
                "              window.AndroidMediaBridge.onMediaPause(); " +
                "          } " +
                "      }, 150); " +
                "  } " +
                "  " +
                "  var intObserver = new IntersectionObserver((entries) => { " +
                "    entries.forEach(entry => { " +
                "      var v = entry.target; " +
                "      /* Skip threshold ratio check for Douyin during cold start grace period */ " +
                "      var isValidIntersect = (isDouyinHost && isColdStartGrace) ? entry.isIntersecting : (entry.isIntersecting && entry.intersectionRatio > 0.5); " +
                "      if (isValidIntersect) { " +
                "        var isNewMedia = (activeVideo !== v); " +
                "        activeVideo = v; " +
                "        " +
                "        /* Wake up decoder if previously suspended */ " +
                "        if (v.__permataSuspended) { " +
                "            v.setAttribute('preload', 'auto'); " +
                "            v.__permataSuspended = false; " +
                "        } " +
                "        " +
                "        /* --- PER-MEDIA CHANGE UNMUTE ENFORCER (FB/IG Defense) --- */ " +
                "        if (isNewMedia) { " +
                "            v.muted = false; " +
                "            v.volume = 1.0; " +
                "            /* Backup micro-timers to override SPA React render cycles */ " +
                "            setTimeout(function() { if (activeVideo === v) { v.muted = false; v.volume = 1.0; } }, 100); " +
                "            setTimeout(function() { if (activeVideo === v) { v.muted = false; v.volume = 1.0; } }, 400); " +
                "        } " +
                "        " +
                "        v.addEventListener('play', onPlayHandler); " +
                "        v.addEventListener('playing', onPlayHandler); " +
                "        v.addEventListener('pause', onPauseHandler); " +
                "        " +
                "        /* Decoder Surgery: Steal hardware decoders from background videos */ " +
                "        var allVids = document.querySelectorAll('video'); " +
                "        for (var i = 0; i < allVids.length; i++) { " +
                "          if (allVids[i] !== v) { " +
                "            var ov = allVids[i]; " +
                "            if (!ov.paused && !isColdStartGrace) ov.pause(); " +
                "            ov.muted = true; " +
                "            " +
                "            if (!ov.__permataSuspended && !isColdStartGrace) { " +
                "                ov.__permataSuspended = true; " +
                "                ov.setAttribute('preload', 'none'); " +
                "                ov.load(); " + 
                "            } " +
                "          } " +
                "        } " +
                "      } else if (!isColdStartGrace) { " +
                "        if (activeVideo === v) activeVideo = null; " +
                "        v.removeEventListener('play', onPlayHandler); " +
                "        v.removeEventListener('playing', onPlayHandler); " +
                "        v.removeEventListener('pause', onPauseHandler); " +
                "      } " +
                "    }); " +
                "  }, { threshold: [0.0, 0.5] }); " +
                "  " +
                "  function scanAndBind(node) { " +
                "    if (node.tagName && node.tagName.toLowerCase() === 'video' && !node.__permataObserved) { " +
                "      node.__permataObserved = true; " +
                "      intObserver.observe(node); " +
                "    } " +
                "    if (node.querySelectorAll) { " +
                "      var vids = node.querySelectorAll('video'); " +
                "      for (var i = 0; i < vids.length; i++) { " +
                "        if (!vids[i].__permataObserved) { " +
                "          vids[i].__permataObserved = true; " +
                "          intObserver.observe(vids[i]); " +
                "        } " +
                "      } " +
                "    } " +
                "  } " +
                "  scanAndBind(document); " +
                "  var mutObserver = new MutationObserver(function(mutations) { " +
                "    mutations.forEach(function(mut) { " +
                "      if (mut.addedNodes) { " +
                "        for (var j = 0; j < mut.addedNodes.length; j++) scanAndBind(mut.addedNodes[j]); " +
                "      } " +
                "    }); " +
                "  }); " +
                "  if (document.body) mutObserver.observe(document.body, { childList: true, subtree: true }); " +
                "})();";
            view.evaluateJavascript(mediaObserverJs, null);
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                            @NonNull WebResourceRequest request) {
        Uri url = request.getUrl();
        String scheme = url.getScheme();

        if ("permata".equals(scheme) && "scroll".equals(url.getHost())) {
            boolean isNext = url.getPath() != null && url.getPath().contains("next");

            float centerX = view.getWidth() / 2f;
            float startY = isNext ? (view.getHeight() * 0.8f) : (view.getHeight() * 0.2f);
            float endY = isNext ? (view.getHeight() * 0.2f) : (view.getHeight() * 0.8f);

            long downTime = SystemClock.uptimeMillis();
            
            MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, startY, 0);
            view.dispatchTouchEvent(downEvent);
            downEvent.recycle();

            long moveTime = downTime + 30;
            MotionEvent moveEvent = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, centerX, endY, 0);
            view.dispatchTouchEvent(moveEvent);
            moveEvent.recycle();

            long upTime = moveTime + 30;
            MotionEvent upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, centerX, endY, 0);
            view.dispatchTouchEvent(upEvent);
            upEvent.recycle();

            return true; 
        }

        if (scheme != null && !scheme.startsWith("http") && !scheme.equals("file") && !scheme.equals("content") && !scheme.equals("permata")) {
            return true; 
        }

        if (isYoutubeUri(url)) {
            try {
                MainActivityDelegate a =
                        MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
                if (a == null) return false;
                if (!(a.showFragment(my.app.permata.R.id.youtube_fragment) instanceof YoutubeFragment f))
                    return false;
                f.loadUrl(url.toString());
                return true;
            } catch (IllegalArgumentException ex) {
                Log.d(ex);
            }
        }

        return false;
    }

    public static boolean isYoutubeUri(Uri uri) {
        String host = uri.getHost();
        return ((host != null) && ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com")) ||
                host.equals("youtu.be")));
    }

    @Override
    public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
                                @NonNull WebResourceErrorCompat error) {
        super.onReceivedError(view, request, error);
    }
}