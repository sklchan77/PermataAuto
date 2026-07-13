package my.app.permata.addon.web;

import static my.app.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import my.app.permata.addon.web.yt.YoutubeFragment;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.function.BooleanConsumer;
import my.app.utils.log.Log;

/**
 * @author sklchan77
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

		// === MEDIA SESSION JAVASCRIPT INJECTION ===
		// Hooks into Chromium's active MediaSession (created by TikTok/Instagram players)
		// Routes the steering wheel Next/Prev requests back to our Java client via a dummy URL
		if (!isYoutubeUri(Uri.parse(url))) {
			String js = "try { " +
				"  if (!window.__permataFrame) { " +
				"    window.__permataFrame = document.createElement('iframe'); " +
				"    window.__permataFrame.style.display = 'none'; " +
				"    document.body.appendChild(window.__permataFrame); " +
				"  } " +
				"  navigator.mediaSession.setActionHandler('nexttrack', () => { window.__permataFrame.src = 'permata://scroll/next'; }); " +
				"  navigator.mediaSession.setActionHandler('previoustrack', () => { window.__permataFrame.src = 'permata://scroll/prev'; }); " +
				"} catch(e) {}";
			view.evaluateJavascript(js, null);
		}
		// ==========================================
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
																					@NonNull WebResourceRequest request) {
		Uri url = request.getUrl();
		String scheme = url.getScheme();

		// === CHROMIUM SCROLL EVENT INTERCEPTION ===
		if ("permata".equals(scheme) && "scroll".equals(url.getHost())) {
			boolean isNext = url.getPath() != null && url.getPath().contains("next");
			Log.i("Steering Wheel Scroll via JS Intercepted. Next: " + isNext);

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

		// === FIX FOR ERR_UNKNOWN_URL_SCHEME CRASH ===
		// Block deep links like snssdk1128:// which halt the view
		if (scheme != null && !scheme.startsWith("http") && !scheme.equals("file") && !scheme.equals("content") && !scheme.equals("permata")) {
			Log.i("Blocked unsupported URL scheme to prevent web crash: " + url.toString());
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
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			Log.e("Web error received: " + error.getDescription());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}