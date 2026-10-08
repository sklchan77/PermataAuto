package my.app.permata.auto;

import static android.accessibilityservice.GestureDescription.getMaxGestureDuration;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import my.app.permata.PermataApplication;
import my.app.utils.log.Log;

public class AccessibilityEventDispatcherService extends AccessibilityService {
	private static String clickOnButton;
	private static AccessibilityEventDispatcherService instance;
	private final Set<Integer> windowIds =
			(VERSION.SDK_INT < VERSION_CODES.TIRAMISU) ? Collections.emptySet() : new HashSet<>();
	private final Path path = new Path();
	private GestureDescription.Builder gb;
	private Pointer[] pointers = new Pointer[]{new Pointer()};

	static int getWindowCount() {
		var ds = instance;
		if (ds == null) return -1;
		int cnt =
				(VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) ? ds.windowIds.size() :
						ds.listWindows().size();
		Log.d("Window count: ", cnt);
		return cnt;
	}

	static void autoClickOnButton(@Nullable String text) {
		if (clickOnButton != null) Log.i("Disabled auto click on button with text: ", clickOnButton);
		if (text != null) Log.i("Enabled auto click on button with text: ", text);
		clickOnButton = text;
	}

	static boolean dispatchBack() {
		var ds = instance;
		return (ds != null) && ds.performGlobalAction(GLOBAL_ACTION_BACK);
	}

	static boolean dispatchHome() {
		var ds = instance;
		return (ds != null) && ds.performGlobalAction(GLOBAL_ACTION_HOME);
	}

	static boolean dispatchTap(float x, float y) {
		var ds = instance;
		if ((VERSION.SDK_INT < VERSION_CODES.N) || (ds == null)) return false;
		if ((x < 0f) || (y < 0f)) return true;
		ds.path.reset();
		ds.path.moveTo(x, y);
		// Increased duration to 50ms so Android OS registers valid touch input
		var gb = new GestureDescription.Builder().addStroke(new StrokeDescription(ds.path, 0L, 50L));
		return ds.dispatchGesture(gb.build(), null, null);
	}

	static boolean dispatchScale(float x, float y, float diff) {
		var ds = instance;
		if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null)) return false;
		if ((x < 0f) || (y < 0f)) return true;
		var path = ds.path;
		var dur = 100L;
		if (diff > 0) {
			path.reset();
			path.moveTo(x, y);
			path.lineTo(x - diff, y);
			ds.addStroke(new StrokeDescription(path, 0L, dur, false));
			path.reset();
			path.moveTo(x + 10, y);
			path.lineTo(x + diff + 10, y);
			ds.addStroke(new StrokeDescription(path, 0L, dur, false));
		} else {
			path.reset();
			path.moveTo(x + diff, y);
			path.lineTo(x, y);
			ds.addStroke(new StrokeDescription(path, 0L, dur, false));
			path.reset();
			path.moveTo(x - diff + 10, y);
			path.lineTo(x + 10, y);
			ds.addStroke(new StrokeDescription(path, 0L, dur, false));
		}
		return ds.dispatch();
	}

	static boolean dispatchMotionEvent(MotionEvent e) {
		var ds = instance;
		if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null)) return false;
		var pointers = ds.pointers;
		var cnt = e.getPointerCount();
		var action = e.getActionMasked();
		boolean completePrev;

		if ((cnt > 1) && (action == ACTION_MOVE)) {
			long timeDiff = e.getEventTime() - ds.pointers[0].time;
			if (timeDiff < 30L) {
				if (timeDiff < 0) ds.pointers[0].time = e.getEventTime();
				completePrev = false;
			} else {
				completePrev = true;
			}
		} else {
			completePrev = false;
		}

		if (cnt > pointers.length) {
			ds.pointers = pointers = Arrays.copyOf(pointers, cnt);
			for (int i = 0; i < cnt; i++) {
				if (pointers[i] == null) pointers[i] = new Pointer();
			}
		}

		var downTime = e.getDownTime();
		var eventTime = e.getEventTime();
		int idx;

		if ((action == ACTION_POINTER_DOWN) || (action == ACTION_POINTER_UP)) {
			action = (action == ACTION_POINTER_DOWN) ? ACTION_DOWN : ACTION_UP;
			idx = e.getActionIndex();
			cnt = idx + 1;
		} else {
			idx = 0;
		}
		for (; idx < cnt; idx++) {
			var x = e.getX(idx);
			var y = e.getY(idx);
			if ((x < 0f) || (y < 0f)) continue;
			var p = pointers[idx];
			if (!ds.buildGesture(p, downTime, eventTime, action, x, y, completePrev)) return false;
		}

		return (ds.gb == null) || ds.dispatch();
	}

	static boolean dispatchMotionEvent(long downTime, long eventTime, int action, float x, float y) {
		var ds = instance;
		if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null)) return false;
		if ((x < 0f) || (y < 0f)) return true;
		var p = instance.pointers[0];
		return ds.buildGesture(p, downTime, eventTime, action, x, y, false) && ds.dispatch();
	}

	@RequiresApi(api = VERSION_CODES.O)
	private boolean buildGesture(Pointer p, long downTime, long eventTime, int action, float x,
															 float y, boolean completePrev) {
		switch (action) {
			case ACTION_DOWN -> {
				p.x = x;
				p.y = y;
				p.time = eventTime;
				path.reset();
				path.moveTo(x, y);
				var dur = Math.max(50L, Math.min(eventTime - downTime, getMaxGestureDuration()));
				p.sd = new StrokeDescription(path, 0L, dur, true);
				addStroke(p.sd);
			}
			case ACTION_UP -> {
				if (p.sd == null) return true;
				path.reset();
				path.moveTo(x, y);
				var dur = Math.max(50L, Math.min(eventTime - p.time, getMaxGestureDuration()));
				var sd = p.sd.continueStroke(path, 0L, dur, false);
				p.sd = null;
				addStroke(sd);
			}
			case ACTION_MOVE -> {
				path.reset();
				path.moveTo(p.x, p.y);
				path.lineTo(x, y);
				var dur = Math.max(50L, Math.min(eventTime - p.time, getMaxGestureDuration()));
				p.x = x;
				p.y = y;
				p.time = eventTime;
				StrokeDescription sd;
				if (p.sd == null) {
					p.sd = sd = new StrokeDescription(path, 0L, dur, true);
				} else if (completePrev) {
					sd = p.sd.continueStroke(path, 0L, dur, false);
					p.sd = null;
				} else {
					p.sd = sd = p.sd.continueStroke(path, 0L, dur, true);
				}
				addStroke(sd);
			}
			default -> {
				Log.d("Unable to dispatch event with action ", action);
				return false;
			}
		}
		return true;
	}

	@RequiresApi(api = VERSION_CODES.N)
	private void addStroke(StrokeDescription sd) {
		if (gb == null) gb = new GestureDescription.Builder();
		gb.addStroke(sd);
	}

	@RequiresApi(api = VERSION_CODES.N)
	private boolean dispatch() {
		var g = gb.build();
		gb = null;
		return dispatchGesture(g, null, null);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		if (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) return;
		windowIds.clear();
		windowIds.addAll(listWindows());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		instance = null;
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		// OPTIMIZATION 1: Ignore all events if phone is not connected to Android Auto
		if (!PermataApplication.get().isConnectedToAuto()) {
			return;
		}

		var type = event.getEventType();
		if ((VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) && (type == TYPE_WINDOWS_CHANGED)) {
			if (event.getDisplayId() != DEFAULT_DISPLAY) return;
			var change = event.getWindowChanges();
			if (change == WINDOWS_CHANGE_ADDED) windowIds.add(event.getWindowId());
			else if (change == WINDOWS_CHANGE_REMOVED) windowIds.remove(event.getWindowId());
			return;
		}

		// OPTIMIZATION 2: Only trigger on discrete window state transitions (ignore continuous content updates)
		if (type != TYPE_WINDOW_STATE_CHANGED) return;

		// OPTIMIZATION 3: Early package name check before parsing UI tree
		CharSequence pkg = event.getPackageName();
		if (pkg == null) return;
		String pkgName = pkg.toString();

		var root = getRootInActiveWindow();
		if (root == null) return;

		try {
			// 1. Process explicit button click requests if set via autoClickOnButton()
			if (clickOnButton != null) {
				Log.i("Event received: ", event);
				Log.i("Finding button by text: ", clickOnButton);
				var btn = root.findAccessibilityNodeInfosByText(clickOnButton);
				if (btn.isEmpty()) {
					Log.i("Button not found. Trying to find by id: android:id/button1.");
					btn = root.findAccessibilityNodeInfosByViewId("android:id/button1");
				}
				if (!btn.isEmpty()) {
					Log.i("Button '", btn.get(0).getText(), "' found. Performing click.");
					performClickNode(btn.get(0));
					clickOnButton = null; // OPTIMIZATION 4: Reset click request immediately after success
					return;
				}
			}

			// 2. Automatically detect and click MediaProjection consent dialog buttons across localizations
			checkAndClickMediaProjectionConsent(root, pkgName);
		} finally {
			root.recycle(); // OPTIMIZATION 5: Prevent memory leaks / GC stutters
		}
	}

	private void checkAndClickMediaProjectionConsent(AccessibilityNodeInfo root, String pkgName) {
		// Intercept system UI dialogs where the casting/recording dialog appears
		if ("com.android.systemui".equals(pkgName) || "android".equals(pkgName) || pkgName.contains("systemui")) {
			String[] targetTexts = new String[]{
					// English (US, UK, Global)
					"Start now", "Start recording", "Allow", "Start",
					
					// Chinese (Simplified - PRC / Mainland)
					"立即开始", "开始录制", "开始", "允许",
					
					// Chinese (Traditional - Taiwan, Hong Kong, Macau)
					"立即開始", "開始錄製", "開始", "允許",

					// South East Asia
					"Mulai sekarang", "Izinkan", // Bahasa Indonesia
					"Mula sekarang", "Benarkan", // Bahasa Melayu (Malaysia / Brunei)
					"เริ่มเลย", "อนุญาต", "เริ่ม", // Thai
					"Bắt đầu ngay", "Cho phép", "Bắt đầu", // Vietnamese
					"Magsimula na", "Simulan ngayon", "Payagan", // Tagalog / Filipino

					// Indian Subcontinent
					"अभी शुरू करें", "अनुमति दें", "शुरू करें", // Hindi / Hindu
					"இப்போதே தொடங்கு", "அனுமதி", // Tamil
					
					// Middle East (Arabic - UAE, Gulf, General)
					"البدء الآن", "ابدأ الآن", "السماح", "بدء",
					
					// East Asia
					"今すぐ開始", "許可", "開始", // Japanese
					"지금 시작", "허용", "시작", // Korean

					// European & Other Major Languages
					"Iniciar ahora", "Iniciar grabación", "Permitir", // Spanish
					"Commencer", "Démarrer", "Autoriser", // French
					"Jetzt starten", "Starten", "Zulassen", // German
					"Avvia ora", "Consenti", "Iniciar agora", "Permitir", // Italian & Portuguese
					"Начать", "Разрешить", "Начать запись", // Russian
					"Şimdi başlat", "İzin ver", // Turkish
					"Nu starten", "Toestaan", "Rozpocznij teraz", "Zezwól" // Dutch & Polish
			};

			for (String text : targetTexts) {
				var nodes = root.findAccessibilityNodeInfosByText(text);
				if (nodes != null && !nodes.isEmpty()) {
					for (var node : nodes) {
						if (node != null && performClickNode(node)) {
							Log.i("Auto-clicked MediaProjection consent button: ", text);
							return;
						}
					}
				}
			}

			// Fallback check for standard Android positive dialog button resource ID
			var btn1 = root.findAccessibilityNodeInfosByViewId("android:id/button1");
			if (btn1 != null && !btn1.isEmpty()) {
				for (var node : btn1) {
					if (node != null && performClickNode(node)) {
						Log.i("Auto-clicked MediaProjection consent by ID: android:id/button1");
						return;
					}
				}
			}
		}
	}

	private boolean performClickNode(AccessibilityNodeInfo node) {
		if (node == null) return false;
		if (node.isClickable()) {
			return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
		AccessibilityNodeInfo parent = node.getParent();
		while (parent != null) {
			if (parent.isClickable()) {
				return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
			parent = parent.getParent();
		}
		return false;
	}

	@Override
	public void onInterrupt() {
	}

	private List<Integer> listWindows() {
		var windows = getWindows();
		var ids = new ArrayList<Integer>(windows.size());
		for (var w : getWindows()) {
			if ((VERSION.SDK_INT >= VERSION_CODES.R) && (w.getDisplayId() != DEFAULT_DISPLAY)) continue;
			var type = w.getType();
			if ((type == TYPE_APPLICATION) || (type == TYPE_INPUT_METHOD)) ids.add(w.getId());
		}
		return ids;
	}

	private static final class Pointer {
		float x;
		float y;
		long time;
		StrokeDescription sd;
	}
}