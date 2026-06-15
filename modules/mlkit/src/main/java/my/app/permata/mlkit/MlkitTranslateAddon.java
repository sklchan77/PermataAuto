package my.app.permata.mlkit;

import android.content.Context;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.common.MlKit;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.TranslateAddon;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.pref.PreferenceStore;

@Keep
@SuppressWarnings("unused")
public class MlkitTranslateAddon extends TranslateAddon {
	private static final AddonInfo info =
			PermataAddon.findAddonInfo(MlkitTranslateAddon.class.getName());

	static {
		try {
			MlKit.initialize(App.get());
		} catch (Exception ignored) {}
	}

	public FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		var translator = Translation.getClient(new TranslatorOptions.Builder()
				.setSourceLanguage(srcLang).setTargetLanguage(targetLang)
				.setExecutor(App.get().getExecutor()).build());
		var p = new Promise<Translator>();
		translator.downloadModelIfNeeded()
				.addOnSuccessListener(v -> p.complete(new MlkitTranslator(translator)))
				.addOnCanceledListener(p::cancel)
				.addOnFailureListener(p::completeExceptionally);
		return p;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public List<Pair<String, String>> getSupportedLanguages(@Nullable String srcLang) {
		var locale = Locale.getDefault(Locale.Category.DISPLAY);
		var langs = CollectionUtils.map(TranslateLanguage.getAllLanguages(),
				lang -> new Pair<>(lang, new Locale(lang).getDisplayLanguage(locale)));
		langs.sort((a, b) -> a.second.compareToIgnoreCase(b.second));
		return langs;
	}

	private record MlkitTranslator(com.google.mlkit.nl.translate.Translator translator)
			implements Translator {

		public FutureSupplier<String> translate(String text) {
			var p = new Promise<String>();
			translator.translate(text)
					.addOnSuccessListener(p::complete)
					.addOnCanceledListener(p::cancel)
					.addOnFailureListener(p::completeExceptionally);
			return p;
		}

		public boolean supportsBatch() {
			return true;
		}
	}
}
