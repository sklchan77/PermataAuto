	@Override
	public void onPause() {
		super.onPause();
		// --- RELEASE CAR FOCUS SAFELY ON EXIT ---
		try {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(delegate -> {
				var cb = delegate.getMediaSessionCallback();
				if (cb != null) {
					try {
						for (java.lang.reflect.Field field : cb.getClass().getDeclaredFields()) {
							if (field.getType().getName().contains("media.session.MediaSession")) {
								field.setAccessible(true);
								Object session = field.get(cb);
								if (session != null) {
									Class<?> sessionClass = session.getClass();
									
									var state = new android.media.session.PlaybackState.Builder()
											.setActions(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT | 
															android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS |
															android.media.session.PlaybackState.ACTION_PLAY |
															android.media.session.PlaybackState.ACTION_PAUSE)
											.setState(android.media.session.PlaybackState.STATE_PAUSED, 0, 0.0f)
											.build();

									for (java.lang.reflect.Method method : sessionClass.getDeclaredMethods()) {
										var params = method.getParameterTypes();
										if (params.length == 1 && params[0] == android.media.session.PlaybackState.class) {
											method.setAccessible(true);
											method.invoke(session, state);
											break;
										}
									}
									break;
								}
							}
						}
					} catch (Exception e) {
						Log.e(e, "Obfuscation-proof focus release failure");
					}
				}
			});
		} catch (Exception e) {
			// Fail-safe wrapper
		}

		if (!BuildConfig.AUTO) return;
		PermataWebView v = getWebView();
		if (v == null) return;
		PermataChromeClient chrome = v.getWebChromeClient();
		if (chrome != null) {
			if (chrome.isFullScreen()) {
				chrome.exitFullScreen();
				fullScreenOnResume = true;
			} else {
				fullScreenOnResume = false;
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		// --- BLOCK MEDIA PLAYER TRACK CLICKS ---
		try {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(delegate -> {
				var cb = delegate.getMediaSessionCallback();
				if (cb != null) {
					try {
						for (java.lang.reflect.Field field : cb.getClass().getDeclaredFields()) {
							if (field.getType().getName().contains("media.session.MediaSession")) {
								field.setAccessible(true);
								Object session = field.get(cb);
								if (session != null) {
									Class<?> sessionClass = session.getClass();
									
									for (java.lang.reflect.Method method : sessionClass.getDeclaredMethods()) {
										var params = method.getParameterTypes();
										if (params.length == 1 && params[0] == boolean.class) {
											method.setAccessible(true);
											method.invoke(session, true);
											break;
										}
									}

									var state = new android.media.session.PlaybackState.Builder()
											.setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE | 
															android.media.session.PlaybackState.ACTION_STOP)
											.setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1.0f)
											.build();

									for (java.lang.reflect.Method method : sessionClass.getDeclaredMethods()) {
										var params = method.getParameterTypes();
										if (params.length == 1 && params[0] == android.media.session.PlaybackState.class) {
											method.setAccessible(true);
											method.invoke(session, state);
											break;
										}
									}
									break;
								}
							}
						}
					} catch (Exception e) {
						Log.e(e, "Obfuscation-proof focus lock failure");
					}
				}
			});
		} catch (Exception e) {
			// Fail-safe wrapper
		}

		if (!BuildConfig.AUTO || !fullScreenOnResume) return;
		PermataWebView v = getWebView();
		if (v == null) return;
		v.onResume();
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> a.post(() -> {
			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.enterFullScreen();
		}));
	}
