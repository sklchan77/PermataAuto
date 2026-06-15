package my.app.permata.addon.web.yt;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import my.app.permata.addon.web.PermataWebClient;
import my.app.permata.addon.web.WebBrowserFragment;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class YoutubeWebClient extends PermataWebClient {

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		if (!isYoutubeUri(request.getUrl())) {
			MainActivityDelegate a = MainActivityDelegate.get(view.getContext());

			try {
				if (!(a.showFragment(
						my.app.permata.R.id.web_browser_fragment) instanceof WebBrowserFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}
}
