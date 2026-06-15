package my.app.permata.auto;

import android.content.Context;
import android.os.SystemClock;
import android.support.car.input.CarRestrictedEditText;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import com.google.android.gms.car.input.CarEditable;
import com.google.android.gms.car.input.CarEditableListener;

import my.app.permata.ui.activity.PermataActivity;
import my.app.permata.ui.activity.MainActivityDelegate;

/**
 * @author sklchan77
 */
public class CarEditText extends CarRestrictedEditText implements CarEditable {
	private OnKeyListener keyListener;

	public CarEditText(Context context) {
		super(context, null);
	}

	@Override
	public void setCarEditableListener(final CarEditableListener listener) {
		super.setCarEditableListener(listener::onUpdateSelection);
	}

	@Override
	public void setOnKeyListener(OnKeyListener l) {
		super.setOnKeyListener(l);
		keyListener = l;
	}

	@Override
	public void onEditorAction(int actionCode) {
		if (keyListener != null) {
			switch (actionCode) {
				case EditorInfo.IME_ACTION_GO:
				case EditorInfo.IME_ACTION_SEARCH:
				case EditorInfo.IME_ACTION_SEND:
				case EditorInfo.IME_ACTION_NEXT:
				case EditorInfo.IME_ACTION_DONE:
					long eventTime = SystemClock.uptimeMillis();
					KeyEvent e = new KeyEvent(eventTime, eventTime,
							KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0,
							KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
							KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE | KeyEvent.FLAG_EDITOR_ACTION);
					keyListener.onKey(this, KeyEvent.KEYCODE_ENTER, e);
					PermataActivity a = MainActivityDelegate.get(getContext()).getAppActivity();
					if (a instanceof MainCarActivity) a.stopInput();
					return;
			}
		}

		super.onEditorAction(actionCode);
	}
}
