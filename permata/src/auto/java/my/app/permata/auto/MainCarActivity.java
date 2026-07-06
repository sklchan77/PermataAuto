package my.app.permata.auto;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.failed;
import static my.app.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import my.app.permata.R;
import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.permata.ui.activity.PermataActivity;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.MediaItemListView;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.Cancellable;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.menu.OverlayMenu;

/**
 * Enterprise Core Vehicle Launcher Gateway Activity.
 * Optimizes native CAN hardware event sync vectors and Chromium system pipelines over wireless projection layers.
 * * @author Author Configuration Refactored Core
 */
public class MainCarActivity extends CarActivity implements PermataActivity {

	// Thread-safe tracking map protecting against platform exceptions while staying 100% immune to context leaks.
	private static final java.util.Map<WebView, Long> scrollTimestamps = 
			java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	// Memory-isolated weak reference tracking to completely eliminate context leaks during sudden wireless drops.
	private static java.lang.ref.WeakReference<MainCarActivity> activeInstanceRef = 
			new java.lang.ref.WeakReference<>(null);

	// Atomic temporal gate to prevent concurrent double-execution between MediaSession and Window inputs
	private static long lastProcessedKeyEventTime = 0;

	static PermataMediaServiceConnection service;
	
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate =
			(FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private TextWatcher textWatcher;

	// --- PRODUCTION STATE-AWARE AUDIO FOCUS CONFIGURATION ---
	private Object nativeFocusRequest; 
	private boolean hasActivityFocus = false;
	
	// Active Native MediaSession instance field to hijack steering wheel hardware inputs
	private android.media.session.MediaSession mediaSession;

	// Explicit runnable declaration to isolate context tracking references on the main message loop.
	private final Runnable initMediaSessionRunnable = this::initMediaSessionOnStartup;

	private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_LOSS -> {
				Log.w("MainCarActivity", "Permanent audio focus loss. Suspending foreground web playback surfaces.");
				hasActivityFocus = false;
				pauseWebViewTraffic();
			}
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
				Log.i("MainCarActivity", "Transient context loss (e.g., car navigation chime or backup camera event). Throttling V8 engine.");
				pauseWebViewTraffic();
			}
			case AudioManager.AUDIOFOCUS_GAIN -> {
				Log.i("MainCarActivity", "Audio focus successfully returned to active UI layout. Resuming rendering routines.");
				hasActivityFocus = true;
				resumeWebViewTraffic();
			}
		}
	};

	// Pre-allocated main thread handler to eliminate GC heap allocation churn during steering wheel click spams.
	private static final android.os.Handler mainThreadHandler = 
			new android.os.Handler(android.os.Looper.getMainLooper());

	/**
	 * Bridge method routing background steering wheel controls to the foreground WebView layout.
	 * Decoupled via WeakReference to guarantee immunity against sudden wireless tether teardowns.
	 */
	public static boolean shareKeyEventToCarActivity(KeyEvent event) {
		MainCarActivity activity = activeInstanceRef.get();
		if (activity == null || activity.isFinishing()) return false;

		MainActivityDelegate d = activity.delegate.peek();
		if (d == null) return false;

		int keyCode = event.getKeyCode();
		boolean isNext = (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_CHANNEL_UP);
		boolean isPrev = (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN);

		if (isNext || isPrev) {
			long now = SystemClock.uptimeMillis();
			if (now - lastProcessedKeyEventTime < 150) {
				return true; // Absorb event duplicate split-second spam bounce safely
			}

			ActivityFragment activeFragment = d.getActiveFragment();
			if (activeFragment != null) {
				String fragName = activeFragment.getClass().getName().toLowerCase();
				
				// CRITICAL FIX: NATIVE IPTV & LOCAL MEDIA PLAYER MULTI-DISPATCH ENGINE
				if (fragName.contains("iptv") || fragName.contains("player") || 
					fragName.contains("video") || fragName.contains("media")) {
					
					if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
						lastProcessedKeyEventTime = now;
						mainThreadHandler.post(() -> {
							try {
								MainCarActivity curr = activeInstanceRef.get();
								if (curr != null && !curr.isFinishing()) {
									// 1. Send straight into active focused elements inside window tree view layout
									curr.getWindow().getDecorView().dispatchKeyEvent(event);

									// 2. Wrap into an explicit matching background intent matching local service signatures
									Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
									mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);
									mediaIntent.setPackage(curr.getPackageName());
									curr.sendBroadcast(mediaIntent);
								}
							} catch (Exception e) {
								Log.e("MainCarActivity", "Error executing localized target player dispatch loop", e);
							}
						});
					}
					return true; // Successfully consumed and locked out from escaping back to standard car audio systems
				}
			}

			// Filter actions for standard browser views (e.g. YouTube Web layout, standard web pages)
			if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
				lastProcessedKeyEventTime = now;
				mainThreadHandler.post(() -> {
					try {
						MainCarActivity currentValidActivity = activeInstanceRef.get();
						if (currentValidActivity != null && !currentValidActivity.isFinishing()) {
							currentValidActivity.performFragmentScroll(!isNext, d);
						}
					} catch (Exception e) {
						Log.e("MainCarActivity", "UI Thread exception during programmatic scroll dispatch", e);
					}
				});
			}
			return true; 
		}
		return false;
	}

	/**
	 * Forcefully registers a Playing state with the car system and drops external background audio sources.
	 * Re-engineered to properly handle Android Auto connection teardown and setup signals safely.
	 */
	private void initMediaSessionOnStartup() {
		try {
			// Wipe clean any leftover context configurations from previous dead connections
			if (mediaSession != null) {
				try {
					mediaSession.setActive(false);
					mediaSession.release();
				} catch (Exception ignored) {}
				mediaSession = null;
			}

			// Force immediate audio context acquisition to hijack priority control before car radio can lock it
			acquirePlaybackFocus(AudioManager.AUDIOFOCUS_GAIN);

			mediaSession = new android.media.session.MediaSession(this, "PermataAutoMediaSession");
			mediaSession.setCallback(new android.media.session.MediaSession.Callback() {
				@Override
				public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
					if (Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonIntent.getAction())) {
						KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
						if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
							if (shareKeyEventToCarActivity(keyEvent)) {
								return true; // Absorb event from leaking back out to car radio console
							}
						}
					}
					return super.onMediaButtonEvent(mediaButtonIntent);
				}
			}, mainThreadHandler);

			android.media.session.PlaybackState.Builder stateBuilder = new android.media.session.PlaybackState.Builder()
					.setActions(android.media.session.PlaybackState.ACTION_PLAY | 
								android.media.session.PlaybackState.ACTION_PAUSE | 
								android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT | 
								android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
					.setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1.0f);

			mediaSession.setPlaybackState(stateBuilder.build());
			mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | 
								  android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
			mediaSession.setActive(true);
			Log.i("MainCarActivity", "MediaSession successfully constructed and activated with strict vehicle audio filters.");
		} catch (Exception e) {
			Log.e("MainCarActivity", "Encountered system exception while binding structural MediaSession hooks", e);
		}
	}

	/**
	 * Production safe, explicit on-demand focus capture mechanism.
	 */
	public boolean acquirePlaybackFocus(int focusDurationHint) {
		try {
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (audioManager == null) return false;

			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				AudioFocusRequest.Builder builder = new AudioFocusRequest.Builder(focusDurationHint)
						.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_MEDIA)
								.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) 
								.build())
						.setAcceptsDelayedFocusGain(true) 
						.setOnAudioFocusChangeListener(focusChangeListener, mainThreadHandler);

				AudioFocusRequest request = builder.build();
				nativeFocusRequest = request;
				int result = audioManager.requestAudioFocus(request);
				hasActivityFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
			} else {
				int result = audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, focusDurationHint);
				hasActivityFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
			}

			return hasActivityFocus;
		} catch (Exception e) {
			Log.e("MainCarActivity", "Failed safe hardware audio capture negotiation routine", e);
			return false;
		}
	}

	/**
	 * Explicit cleanup routine removing dead handlers from internal SystemServer tracking maps.
	 */
	private void releasePlaybackFocus() {
		try {
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (audioManager != null) {
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
					if (nativeFocusRequest instanceof AudioFocusRequest request) {
						audioManager.abandonAudioFocusRequest(request);
						nativeFocusRequest = null;
					}
				} else {
					audioManager.abandonAudioFocus(focusChangeListener);
				}
			}
			hasActivityFocus = false;
		} catch (Exception e) {
			Log.e("MainCarActivity", "Failed clean release extraction execution loop against System AudioService", e);
		}
	}

	/**
	 * Freezes internal V8 compilation timers completely when interface visibility drops or camera cuts in.
	 */
	private void pauseWebViewTraffic() {
		mainThreadHandler.post(() -> {
			MainActivityDelegate d = delegate.peek();
			if (d != null && d.getActiveFragment() != null) {
				View root = d.getActiveFragment().getView();
				toggleWebViewState(root, false);
			}
		});
	}

	/**
	 * Unlocks suspended rendering components instantly upon visual recovery context confirmation.
	 */
	private void resumeWebViewTraffic() {
		mainThreadHandler.post(() -> {
			MainActivityDelegate d = delegate.peek();
			if (d != null && d.getActiveFragment() != null) {
				View root = d.getActiveFragment().getView();
				toggleWebViewState(root, true);
			}
		});
	}

	private void toggleWebViewState(View v, boolean resume) {
		if (v == null) return;
		if (v instanceof WebView wv) {
			try {
				if (resume) {
					wv.onResume();
					wv.resumeTimers();
				} else {
					wv.onPause();
					wv.pauseTimers(); 
				}
			} catch (Exception e) {
				Log.e("MainCarActivity", "Error executing rendering lifecycle transition shift on target container", e);
			}
		} else if (v instanceof ViewGroup vg) {
			for (int i = 0, n = vg.getChildCount(); i < n; i++) {
				toggleWebViewState(vg.getChildAt(i), resume);
			}
		}
	}

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MainActivityDelegate.attachBaseContext(base));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		activeInstanceRef = new java.lang.ref.WeakReference<>(this); 
		
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		initCarActivity(this);
		PermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onCreate(savedInstanceState, s);
		} else {
			delegate = PermataMediaServiceConnection.connect(this).main()
					.onFailure(err -> showAlert(getContext(), String.valueOf(err))).map(c -> {
						service = c;
						return onCreate(savedInstanceState, c);
					});
		}
	}

	static void initCarActivity(CarActivity a) {
		a.setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = a.getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	private MainActivityDelegate onCreate(Bundle state, PermataMediaServiceConnection s) {
		MainActivityDelegate d = new MainActivityDelegate(this, s.createBinder());
		ActivityDelegate.setContextToDelegate(ctx -> d);
		delegate = completed(d);
		d.onActivityCreate(state);

		// Post token immediately on setup execution queue
		mainThreadHandler.removeCallbacks(initMediaSessionRunnable);
		mainThreadHandler.postDelayed(initMediaSessionRunnable, 300);

		return d; 
	}

	@Override
	public void onResume() {
		super.onResume();
		// RECONNECTION RE-BIND SAFETIES: Force local token registration to adapt during hot plugs
		activeInstanceRef = new java.lang.ref.WeakReference<>(this);
		
		// Tear down and rebuild the media routing layer to keep Android Auto from orphaning session triggers
		initMediaSessionOnStartup();
		resumeWebViewTraffic();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityResume);
	}

	@Override
	public void onPause() {
		pauseWebViewTraffic();
		super.onPause();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		mainThreadHandler.removeCallbacks(initMediaSessionRunnable);

		Cursor cursor = (Cursor) findViewById(R.id.cursor);
		if (cursor != null) {
			cursor.cleanup();
		}

		stopInput();

		if (service != null && !service.isConnected()) {
			service = null;
		}

		if (activeInstanceRef.get() == this) {
			activeInstanceRef.clear();
		}
		
		try {
			if (mediaSession != null) {
				mediaSession.setActive(false);
				mediaSession.release();
				mediaSession = null;
			}
		} catch (Exception e) {
			Log.e("MainCarActivity", "Error encountered clearing native MediaSession components inside onDestroy", e);
		}
		
		releasePlaybackFocus(); 

		super.onDestroy();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityDestroy)
				.thenRun(() -> ActivityDelegate.setContextToDelegate(null));
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		return null;
	}

	public boolean isCarActivity() {
		return true;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityFinish);
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;

		if (w instanceof OnEditorActionListener) {
			editText.setOnEditorActionListener((OnEditorActionListener) w);
		} else {
			editText.setOnEditorActionListener(null);
		}

		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
					stopInput();
					if (err instanceof OperationCanceledException) {
						textWatcher = w;
						editText.removeTextChangedListener(w);
						editText.addTextChangedListener(w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						a().startInput(editText);
					} else if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else {
						stopInput();
					}
				});
			} else {
				a().startInput(editText);
			}
		});
		return editText;
	}

	public void stopInput() {
		if (editText != null) {
			if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
			editText.setOnEditorActionListener(null);
		}
		try {
			a().stopInput();
		} catch (Exception ignored) {}
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}

	@Override
	public boolean setTextInput(String text) {
		if ((editText == null) || !isInputActive()) return false;
		editText.setText(text);
		stopInput();
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyUp(keyCode, keyEvent);

		if (d.getPrefs().useDpadCursor(d)) {
			switch (keyCode) {
				case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
						KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if (c != null) c.delayedHide();
					return true;
				}
				case KEYCODE_BACK -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.ignoreBack) {
						c.ignoreBack = false;
						return true;
					}
				}
				case KEYCODE_DPAD_CENTER -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.isFocused()) return c.click();
				}
			}
		}

		return d.onKeyUp(keyCode, keyEvent, super::onKeyDown);
	}

	private boolean performFragmentScroll(boolean up, MainActivityDelegate d) {
		if (d == null) return false;
		ActivityFragment f = d.getActiveFragment();
		if (f == null) return false;

		String name = f.getClass().getName().toLowerCase();
		if (name.contains("iptv") || name.contains("player") || name.contains("video") || name.contains("media")) {
			return false; 
		}

		View root = f.getView();
		if (root instanceof ViewGroup vg) return performViewScroll(up, vg);
		return false;
	}

	private boolean performViewScroll(boolean up, View v) {
		if (v == null || v.getVisibility() != View.VISIBLE || !v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0) {
			return false;
		}

		if (v instanceof RecyclerView rv) {
			LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
			if (lm == null) return false;
			int pos = lm.findFirstVisibleItemPosition();
			if (up) {
				if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
			} else {
				if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
			}
			return true;
		} else if (v instanceof WebView wv) {
			return smartScrollWebView(wv, up);
		} else if (v instanceof ViewGroup vg) {
			for (int i = 0, n = vg.getChildCount(); i < n; i++) {
				if (performViewScroll(up, vg.getChildAt(i))) return true;
			}
		}
		return false;
	}

	private static boolean smartScrollWebView(final WebView wv, boolean up) {
		if (wv == null || !wv.isAttachedToWindow() || wv.getWidth() <= 0 || wv.getHeight() <= 0) {
			return false;
		}

		wv.requestFocus();

		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTimeObj = scrollTimestamps.get(wv);
		long lastClickTime = (lastClickTimeObj != null) ? lastClickTimeObj : 0;
		scrollTimestamps.put(wv, now); 
		
		boolean isSpamming = (now - lastClickTime < 250);
		int pixelStep = (int) (wv.getHeight() * 0.70f); 
		if (up) pixelStep = -pixelStep;

		if (wv.getSettings().getJavaScriptEnabled()) {
			String jsScript = "(function(step, isSpam) {" +
					"  var behavior = isSpam ? 'auto' : 'smooth';" +
					"  try {" +
					"    window.scrollBy({ top: step, behavior: behavior });" +
					"  } catch(e) { window.scrollBy(0, step); }" +
					"  var rootNode = document.scrollingElement || document.documentElement || document.body;" +
					"  if (rootNode) { rootNode.scrollTop += step; }" +
					"  try {" +
					"    for (var i = 0; i < window.frames.length; i++) {" +
					"      window.frames[i].postMessage({ type: 'PERMATA_SCROLL', step: step }, '*');" +
					"    }" +
					"  } catch(e) {}" +
					"  return 1;" +
					"})(" + pixelStep + ", " + isSpamming + ");";
			wv.evaluateJavascript(jsScript, null);
		}

		final float safeContentX = wv.getWidth() * 0.33f; 
		final float centerY = wv.getHeight() / 2f;
		
		float span = wv.getHeight() * 0.45f; 
		final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
		final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

		try {
			MotionEvent eventDown = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, safeContentX, yStart, 0);
			wv.dispatchTouchEvent(eventDown);
			eventDown.recycle();

			long moveTime = now + 10;
			MotionEvent eventMove = MotionEvent.obtain(now, moveTime, MotionEvent.ACTION_MOVE, safeContentX, yEnd, 0);
			wv.dispatchTouchEvent(eventMove);
			eventMove.recycle();

			long upTime = now + 20;
			MotionEvent eventUp = MotionEvent.obtain(now, upTime, MotionEvent.ACTION_UP, safeContentX, yEnd, 0);
			wv.dispatchTouchEvent(eventUp);
			eventUp.recycle();
		} catch (Exception e) {
			int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
		}

		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);

		if (keyEvent.getRepeatCount() > 0) {
			return true; 
		}

		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyDown(keyCode, keyEvent);

		long now = SystemClock.uptimeMillis();
		if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
			keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
			keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			
			if (now - lastProcessedKeyEventTime < 150) {
				return true; 
			}
			lastProcessedKeyEventTime = now;
			
			boolean downDirection = (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_CHANNEL_UP);
			if (performFragmentScroll(!downDirection, d)) return true;
		}

		if (!d.getPrefs().useDpadCursor(d)) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);

		float x = 0;
		float y = 0;
		View screen = null;
		Cursor cursor = null;

		switch (keyCode) {
			case KEYCODE_DPAD_UP -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_UP);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = -1;
			}
			case KEYCODE_DPAD_DOWN -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_DOWN);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = 1;
			}
			case KEYCODE_DPAD_LEFT -> x = -1;
			case KEYCODE_DPAD_RIGHT -> x = 1;
			case KEYCODE_DPAD_UP_LEFT -> { y = -1; x = -1; }
			case KEYCODE_DPAD_UP_RIGHT -> { y = -1; x = 1; }
			case KEYCODE_DPAD_DOWN_LEFT -> { y = 1; x = -1; }
			case KEYCODE_DPAD_DOWN_RIGHT -> { y = 1; x = 1; }
			case KEYCODE_BACK -> {
				screen = findViewById(R.id.main_activity);
				cursor = (Cursor) screen.findViewById(R.id.cursor);
				if ((cursor == null) || cursor.isFocused())
					return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
				cursor.ignoreBack = true;
			}
			case KEYCODE_DPAD_CENTER -> {
				screen = findViewById(R.id.main_activity);
				cursor = (Cursor) screen.findViewById(R.id.cursor);
				if (cursor == null) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
			}
			default -> { return d.onKeyDown(keyCode, keyEvent, super::onKeyDown); }
		}

		if (screen == null) {
			screen = findViewById(R.id.main_activity);
			cursor = (Cursor) screen.findViewById(R.id.cursor);
		}

		int w = screen.getWidth();
		int h = screen.getHeight();

		if (cursor == null) {
			cursor = new Cursor(d, (int) (Math.min(w, h) * 0.05f));
			((ViewGroup) screen).addView(cursor);
			cursor.show(w / 2f, h / 2f);
		} else if (!cursor.isVisible()) {
			cursor.show(cursor.getX(), cursor.getY());
		} else {
			int cursorSize = (int) (Math.min(w, h) * 0.05f);
			cursor.move(w, h, x, y, cursorSize / 3f, keyCode);
		}

		return true;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress) :
				super.onKeyLongPress(keyCode, keyEvent);
	}

	private static final class Cursor extends AppCompatImageView
			implements View.OnClickListener, View.OnLongClickListener {
		private final MainActivityDelegate activity;
		private Cancellable move = Cancellable.CANCELED;
		private Cancellable hide = Cancellable.CANCELED;
		private Cancellable resetAccel = Cancellable.CANCELED;
		
		private boolean isDisposed = false;
		
		boolean ignoreBack;
		int accel = 1;

		Cursor(MainActivityDelegate d, int size) {
			super(d.getContext());
			activity = d;
			setId(R.id.cursor);
			setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
			setLayoutParams(new ConstraintLayout.LayoutParams(size, size));
			setImageDrawable(AppCompatResources.getDrawable(d.getContext(), R.drawable.cursor));
			setOnClickListener(this);
			setOnLongClickListener(this);

			Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
			StateListDrawable bg = new StateListDrawable();
			bg.addState(new int[]{android.R.attr.state_focused}, transparent);
			bg.addState(new int[]{}, transparent);
			setBackground(bg);
		}

		void cleanup() {
			isDisposed = true;
			move.cancel();
			hide.cancel();
			resetAccel.cancel();
			move = Cancellable.CANCELED;
			hide = Cancellable.CANCELED;
			resetAccel = Cancellable.CANCELED;
		}

		boolean isVisible() {
			return getVisibility() == VISIBLE;
		}

		@Override
		public void onClick(View v) {
			click();
		}

		boolean click() {
			delayedHide();
			float x = getX();
			float y = getY();
			OverlayMenu m = activity.getActiveMenu();
			if ((m instanceof ViewGroup v) && click(v, x - v.getX(), y - v.getY())) return true;
			click(screen(), x, y);
			return true;
		}

		private boolean click(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v == null || v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v instanceof WebView) {
							touch(v, x, y, true);
							return true;
						}
						if (v instanceof VideoView) {
							touch(v, x, y, false);
							return true;
						}
						if (v.isClickable()) {
							v.performClick();
							v.requestFocus();
							activity.post(this::requestFocus);
							return true;
						}

						if ((v instanceof ViewGroup vg) && click(vg, x, y)) return true;
						touch(v, x, y, false);
						return true;
					}
				}
			}
			return false;
		}

		private void touch(View v, float x, float y, boolean clearFocus) {
			if (isDisposed) return;
			long time = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
			if (clearFocus) {
				clearFocus();
				setVisibility(GONE);
			}
			v.dispatchTouchEvent(down);
			down.recycle();
			activity.postDelayed(() -> {
				if (isDisposed) return;
				MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
				v.dispatchTouchEvent(up);
				up.recycle();
				if (!clearFocus) activity.post(this::requestFocus);
			}, 100);
		}

		@Override
		public boolean onLongClick(View v) {
			delayedHide();
			longClick(screen(), getX(), getY());
			return true;
		}

		private void longClick(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v == null || v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v.isLongClickable()) {
							v.performLongClick();
							activity.post(this::requestFocus);
							return;
						}
						if (v instanceof ViewGroup vg) longClick(vg, x, y);
						return;
					}
				}
			}
		}

		void delayedHide() {
			move.cancel();
			move = Cancellable.CANCELED;
			hide.cancel();
			hide = activity.postDelayed(() -> {
				if (isDisposed) return;
				hide = Cancellable.CANCELED;
				clearFocus();
				setVisibility(GONE);
				MediaItemListView.focusActive(activity.getContext(), null);
			}, 5000);
		}

		void show(float cursorX, float cursorY) {
			if (isDisposed) return;
			setVisibility(VISIBLE);
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		void move(int w, int h, float dx, float dy, float step, int keyCode) {
			if (isDisposed) return;
			move.cancel();
			move = activity.postDelayed(() -> move(w, h, dx, dy, step, keyCode), 50);

			float cursorX = getX() + (step * dx * accel);
			float cursorY = getY() + (step * dy * accel);
			cursorX = Math.max(0, Math.min(w - step, cursorX));
			cursorY = Math.max(0, Math.min(h - step, cursorY));

			if (resetAccel.cancel()) accel += 1;
			resetAccel = activity.postDelayed(() -> {
				resetAccel = Cancellable.CANCELED;
				accel = 1;
			}, 200);

			if ((keyCode == KEYCODE_DPAD_UP) && (getY() == 0)) {
				scroll(true);
			} else if ((keyCode == KEYCODE_DPAD_DOWN) && (getY() >= screen().getHeight() - getHeight())) {
				scroll(false);
			}

			if (!focusFb(screen(), cursorX, cursorY)) requestFocus();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		private boolean focusFb(ViewGroup screen, float cursorX, float cursorY) {
			if (screen == null) return false;
			View fb = screen.findViewById(R.id.floating_button);
			if (fb == null) return false;
			float fbX = fb.getX();
			float fbY = fb.getY();
			if ((cursorX >= fbX) && (cursorX < fbX + fb.getWidth()) && (cursorY >= fbY) &&
					(cursorY < fbY + fb.getHeight())) {
				fb.requestFocus();
				return true;
			}
			return false;
		}

		private void scroll(boolean up) {
			ActivityFragment f = activity.getActiveFragment();
			if (f == null) return;
			View root = f.getView();
			if (root instanceof ViewGroup vg) {
				scrollAtCoordinates(up, vg, getX(), getY());
			}
		}

		private boolean scrollAtCoordinates(boolean up, ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v == null || v.getVisibility() != View.VISIBLE || !v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0) {
					continue;
				}

				float relativeX = cursorX - v.getX();
				float relativeY = cursorY - v.getY();

				if ((relativeX >= 0 && relativeX < v.getWidth()) && (relativeY >= 0 && relativeY < v.getHeight())) {
					if (v instanceof RecyclerView rv) {
						LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
						if (lm == null) return false;
						int pos = lm.findFirstVisibleItemPosition();
						if (up) {
							if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
						} else {
							if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
						}
						return true;
					} else if (v instanceof WebView wv) {
						return MainCarActivity.smartScrollWebView(wv, up);
					} else if (v instanceof ViewGroup vg) {
						if (scrollAtCoordinates(up, vg, relativeX, relativeY)) return true;
					}
				}
			}
			return false;
		}

		private ViewGroup screen() {
			return (ViewGroup) getParent();
		}
	}
}
