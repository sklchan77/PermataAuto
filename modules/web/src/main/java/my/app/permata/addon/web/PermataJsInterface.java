package my.app.permata.addon.web;

import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import my.app.utils.app.App;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class PermataJsInterface {
	public static final String NAME = "Permata";
	public static final String JS_EVENT = "window.Permata.event";
	public static final int JS_EDIT = 0;
	public static final int JS_ERR = 1;
	protected static final int JS_LAST = 1;
	private final PermataWebView webView;

	public PermataJsInterface(PermataWebView webView) {
		this.webView = webView;
	}

	public PermataWebView getWebView() {
		return webView;
	}

	@Keep
	@SuppressWarnings("unused")
	@JavascriptInterface
	public void event(int event, String data) {
		App.get().run(() -> handleEvent(event, data));
	}

	protected void handleEvent(int event, String data) {
		switch (event) {
			case JS_EDIT:
				Log.d("Edit text event: ", data);
				getWebView().showKeyboard(data);
				break;
			case JS_ERR:
				Log.e("JavaScript error: ", data);
				break;
		}
	}
}
