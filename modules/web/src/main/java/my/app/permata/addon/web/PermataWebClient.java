package my.app.permata.addon.web;

import static my.app.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.graphics.Bitmap;
import android.net.Uri;
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
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
																					@NonNull WebResourceRequest request) {
		if (isYoutubeUri(request.getUrl())) {
			try {
				MainActivityDelegate a =
						MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(my.app.permata.R.id.youtube_fragment) instanceof YoutubeFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
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
