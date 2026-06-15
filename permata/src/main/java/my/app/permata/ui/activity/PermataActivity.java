package my.app.permata.ui.activity;

import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.Nullable;

import my.app.utils.ui.activity.AppActivity;

/**
 * @author sklchan77
 */
public interface PermataActivity extends AppActivity {


	boolean isCarActivity();

	void setRequestedOrientation(int requestedOrientation);

	@Nullable
	default EditText startInput(TextWatcher w) {
		return null;
	}

	default void stopInput() {
	}

	default boolean isInputActive() {
		return false;
	}

	default boolean setTextInput(String text) {
		return false;
	}
}
