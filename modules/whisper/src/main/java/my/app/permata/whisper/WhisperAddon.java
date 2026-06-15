package my.app.permata.whisper;

import android.content.Context;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import my.app.permata.PermataApplication;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.SubGenAddon;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.BooleanSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.misc.ChangeableCondition;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;

@Keep
@SuppressWarnings("unused")
public class WhisperAddon extends SubGenAddon {
	public static final PreferenceStore.Pref<BooleanSupplier>
			USE_GPU = PreferenceStore.Pref.b("SG_WHISPER_USE_GPU", false);
	public static final PreferenceStore.Pref<Supplier<String>> MODEL =
			PreferenceStore.Pref.s("SG_WHISPER_MODEL", "tiny-q5_1");
	private static final AddonInfo info = PermataAddon.findAddonInfo(WhisperAddon.class.getName());

	@Override
	public int getAddonId() {
		return my.app.permata.R.id.whisper_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public FutureSupplier<Transcriptor> getTranscriptor(PreferenceStore ps) {
		return Whisper.create(ps).cast();
	}

	@Override
	protected Object createCacheKey(PreferenceStore ps) {
		return ps.getStringPref(MODEL) + "|" + ps.getBooleanPref(USE_GPU);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																 ChangeableCondition visibility, boolean isGlobalSettings) {
		super.contributeSettings(ctx, ps, set, visibility, isGlobalSettings);
		set.addListPref(o -> {
			o.setStringValues(ps, MODEL, Whisper.getModelNames());
			o.title = my.app.permata.R.string.sub_gen_model;
			o.subtitle = my.app.permata.R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = visibility.copy();
		});
		set.addBooleanPref(o -> {
			o.store = ps;
			o.pref = USE_GPU;
			o.title = my.app.permata.R.string.sub_gen_use_gpu;
			o.visibility = visibility.copy();
		});
	}

	@Override
	protected List<Pair<String, String>> getSupportedLanguages() {
		var supported = new String[]{
				"af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo", "br", "bs", "ca", "cs", "cy",
				"da", "de", "el", "en", "es", "et", "eu", "fa", "fi", "fo", "fr", "gl", "gu", "ha", "haw",
				"he", "hi", "hr", "ht", "hu", "hy", "id", "is", "it", "ja", "jw", "ka", "kk", "km", "kn",
				"ko", "la", "lb", "ln", "lo", "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt",
				"my", "ne", "nl", "nn", "no", "oc", "pa", "pl", "ps", "pt", "ro", "ru", "sa", "sd", "si",
				"sk", "sl", "sn", "so", "sq", "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl",
				"tr", "tt", "uk", "ur", "uz", "vi", "yi", "yo", "zh", "yue"
		};
		var locale = Locale.getDefault(Locale.Category.DISPLAY);
		var values = new ArrayList<Pair<String, String>>(supported.length + 1);
		values.add(
				new Pair<>("auto", PermataApplication.get().getString(my.app.permata.R.string.auto)));
		for (String lang : supported) {
			values.add(new Pair<>(lang, new Locale(lang).getDisplayLanguage(locale)));
		}
		values.subList(1, values.size()).sort((a, b) -> a.second.compareToIgnoreCase(b.second));
		return values;
	}

	@Override
	public Collection<String> doCleanUp(Context ctx, PreferenceStore ps) {
		return Whisper.cleanUp(ctx, ps);
	}
}
