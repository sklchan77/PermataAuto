package my.app.permata.addon.chat;

import static my.app.permata.util.Utils.openUrl;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import java.util.Locale;

import my.app.permata.PermataApplication;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.PermataFragmentAddon;
import my.app.utils.app.App;
import my.app.utils.function.IntSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.misc.ChangeableCondition;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class ChatAddon implements PermataFragmentAddon {
	private static final AddonInfo info = PermataAddon.findAddonInfo(ChatAddon.class.getName());
	private static final Pref<Supplier<String>> OPENAI_KEY = Pref.s("OPENAI_KEY", "");
	private static final String[] MODELS =
			new String[]{"gpt-4o", "chatgpt-4o-latest", "gpt-4o-mini", "o1", "o1-mini", "o1-preview"};
	private static final Pref<IntSupplier> MODEL = Pref.i("MODEL", 2);
	private static final Pref<Supplier<String>> MODEL_OTHER = Pref.s("MODEL_OTHER", "");
	private static final Pref<Supplier<String>> CHAT_LANG =
			Pref.s("CHAT_LANG", () -> Locale.getDefault().toLanguageTag());

	@Override
	public int getAddonId() {
		return my.app.permata.R.id.chat_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new ChatFragment();
	}

	public String getOpenaiKey() {
		return PermataApplication.get().getPreferenceStore().getStringPref(OPENAI_KEY);
	}

	public String getModel() {
		var ps = PermataApplication.get().getPreferenceStore();
		var other = ps.getStringPref(MODEL_OTHER).trim();
		if (!other.isEmpty()) return other;
		int i = ps.getIntPref(MODEL);
		return (i >= 0) && (i < MODELS.length) ? MODELS[i] :
				MODELS[MODEL.getDefaultValue().getAsInt()];
	}

	public String getGetChatLang() {
		return PermataApplication.get().getPreferenceStore().getStringPref(CHAT_LANG);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addStringPref(o -> {
			String keyUrl = "https://platform.openai.com/api-keys";
			String sub = App.get().getString(R.string.openai_key_sub, keyUrl);
			o.store = store;
			o.pref = OPENAI_KEY;
			o.title = R.string.openai_key;
			o.csubtitle = HtmlCompat.fromHtml(sub, HtmlCompat.FROM_HTML_MODE_COMPACT);
			o.clickListener = v -> openUrl(v.getContext(), keyUrl);
		});
		set.addListPref(o -> {
			o.store = store;
			o.pref = MODEL;
			o.title = R.string.openai_model;
			o.stringValues = MODELS;
			o.subtitle = my.app.permata.R.string.string_format;
			o.formatSubtitle = true;
		});
		set.addStringPref(o -> {
			o.store = store;
			o.pref = MODEL_OTHER;
			o.title = R.string.openai_model_other;
			o.stringHint = "gpt-4-turbo";
		});
		set.addTtsLocalePref(o -> {
			o.store = store;
			o.pref = CHAT_LANG;
			o.title = my.app.permata.R.string.lang;
			o.subtitle = my.app.permata.R.string.string_format;
			o.formatSubtitle = true;
		});
	}
}
