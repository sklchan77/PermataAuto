package my.app.permata.ui.view;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static java.util.Objects.requireNonNull;
import static my.app.permata.media.pref.MediaPrefs.AE_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.EQ_PRESET;
import static my.app.utils.function.CheckedRunnable.runWithRetry;

import android.content.Context;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import my.app.permata.R;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.function.BooleanSupplier;
import my.app.utils.function.IntSupplier;
import my.app.utils.function.ShortConsumer;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.pref.BasicPreferenceStore;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.pref.PreferenceView;
import my.app.utils.pref.PreferenceView.BooleanOpts;
import my.app.utils.pref.PreferenceView.ListOpts;
import my.app.utils.ui.UiUtils;

public class AudioEffectsView extends ScrollView implements PreferenceStore.Listener {
	private static final String TAG = "AudioEffectsView";

	private final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
	private final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);
	
	@Nullable private PreferenceStore store;
	@Nullable private PreferenceStore ctrlPrefs;
	@Nullable private AudioEffects effects;
	@Nullable private PlayableItem activeItem;

	public AudioEffectsView(@NonNull Context context) {
		this(context, null);
		setLayoutParams(new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
	}

	public AudioEffectsView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		setBackgroundColor(Color.TRANSPARENT);
	}

	@Nullable
	public AudioEffects getEffects() {
		return effects;
	}
	public void init(@NonNull MediaSessionCallback cb, @NonNull AudioEffects effects, @NonNull PlayableItem pi) {
		this.effects = effects;
		this.activeItem = pi;
		this.store = new BasicPreferenceStore();
		this.store.addBroadcastListener(this);
		
		removeAllViews();
		inflate(getContext(), R.layout.audio_effects, this);

		Equalizer eq = effects.getEqualizer();
		Virtualizer virt = effects.getVirtualizer();
		BassBoost bass = effects.getBassBoost();
		LoudnessEnhancer le = effects.getLoudnessEnhancer();

		if (eq != null) {
			ctrlPrefs = cb.getPlaybackControlPrefs();
			short numPresets = eq.getNumberOfPresets();
			String[] userPresets = ctrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
			String[] presetNames = new String[numPresets + userPresets.length + 1];
			int preset = getEqPreset(pi, ctrlPrefs);
			if (preset < 0) preset = -preset + numPresets;

			configureSwitch(findViewById(R.id.equalizer_switch), 
				() -> eq.getEnabled(), 
				checked -> effects.setEqualizerEnabled(getContext().getApplicationContext(), checked));
				
			findViewById(R.id.equalizer_preset_save).setOnClickListener(this::savePreset);
			findViewById(R.id.equalizer_preset_delete).setOnClickListener(this::deletePreset);
			createBands(eq);

			presetNames[0] = getResources().getString(R.string.eq_manual);
			for (short i = 0; i < numPresets; i++) {
				presetNames[i + 1] = presetName(eq.getPresetName(i));
			}
			for (int i = 0; i < userPresets.length; i++) {
				presetNames[i + numPresets + 1] = MediaPrefs.getUserPresetName(userPresets[i]);
			}

			PreferenceView pref = findViewById(R.id.equalizer_preset);
			pref.setPreference(null, () -> {
				ListOpts o = new ListOpts();
				o.store = store;
				o.pref = MediaPrefs.EQ_PRESET;
				o.title = R.string.string_format;
				o.formatTitle = true;
				o.stringValues = presetNames;
				return o;
			});

			store.applyIntPref(MediaPrefs.EQ_PRESET, preset);
		} else {
			hide(R.id.equalizer, R.id.equalizer_title, R.id.equalizer_switch);
		}

		if (virt != null) {
			configureSwitch(findViewById(R.id.virtualizer_switch), 
				() -> virt.getEnabled(), 
				checked -> effects.setVirtualizerEnabled(getContext().getApplicationContext(), checked));
				
			configureSeek(findViewById(R.id.virtualizer_seek), 
				virt::getRoundedStrength, 
				strength -> effects.setVirtualizerStrength(getContext().getApplicationContext(), strength));

			PreferenceView pref = findViewById(R.id.virtualizer_mode);
			pref.setPreference(null, () -> {
				ListOpts o = new ListOpts();
				o.store = store;
				o.pref = MediaPrefs.VIRT_MODE;
				o.title = R.string.string_format;
				o.formatTitle = true;
				o.values = new int[]{R.string.auto, R.string.binaural, R.string.transaural};
				o.valuesMap = new int[]{VIRTUALIZATION_MODE_AUTO, VIRTUALIZATION_MODE_BINAURAL, VIRTUALIZATION_MODE_TRANSAURAL};
				return o;
			});
		} else {
			hide(R.id.virtualizer, R.id.virtualizer_title, R.id.virtualizer_switch);
		}

		if (bass != null) {
			configureSwitch(findViewById(R.id.bass_switch), 
				() -> bass.getEnabled(), 
				checked -> effects.setBassBoostEnabled(getContext().getApplicationContext(), checked));
				
			configureSeek(findViewById(R.id.bass_seek), 
				bass::getRoundedStrength, 
				strength -> effects.setBassBoostStrength(getContext().getApplicationContext(), strength));
		} else {
			hide(R.id.bass, R.id.bass_title, R.id.bass_switch);
		}

		if (le != null) {
			configureSwitch(findViewById(R.id.vol_boost_switch), 
				() -> le.getEnabled(), 
				checked -> effects.setLoudnessEnhancerEnabled(getContext().getApplicationContext(), checked));
				
			configureSeek(findViewById(R.id.vol_boost_seek), 
				() -> (int) (le.getTargetGain() / 10),
				g -> effects.setLoudnessEnhancementGain(getContext().getApplicationContext(), g * 10));
		} else {
			hide(R.id.vol_boost, R.id.vol_boost_title, R.id.vol_boost_switch);
		}

		if (pi.getPrefs().getBooleanPref(AE_ENABLED)) {
			store.applyBooleanPref(TRACK, true);
		} else if (pi.getParent().getPrefs().getBooleanPref(AE_ENABLED)) {
			store.applyBooleanPref(FOLDER, true);
		}

		PreferenceView pref = findViewById(R.id.track);
		pref.setPreference(null, () -> {
			BooleanOpts o = new BooleanOpts();
			o.store = store;
			o.pref = TRACK;
			o.title = R.string.current_track;
			return o;
		});

		pref = findViewById(R.id.folder);
		pref.setPreference(null, () -> {
			BooleanOpts o = new BooleanOpts();
			o.store = store;
			o.pref = FOLDER;
			o.title = R.string.current_folder;
			return o;
		});
	}

	public void cleanup() {
		if (store != null) {
			store.removeBroadcastListener(this);
		}
		removeAllViews();
		store = null;
		ctrlPrefs = null;
		effects = null;
		activeItem = null;
	}
	public void apply(@NonNull MediaSessionCallback cb) {
		if (store == null) return;

		MediaEngine eng = cb.getEngine();
		if (eng != null) {
			PlayableItem pi = eng.getSource();
			if (pi != null) {
				if (store.getBooleanPref(TRACK)) {
					apply(pi.getPrefs());
					return;
				} else if (store.getBooleanPref(FOLDER)) {
					apply(pi.getParent().getPrefs());
					clearPrefs(pi.getPrefs());
					return;
				} else {
					clearPrefs(pi.getPrefs());
					clearPrefs(pi.getParent().getPrefs());
				}
			}
		}
		apply(cb.getPlaybackControlPrefs());
	}

	private void apply(@NonNull PreferenceStore ps) {
		runWithRetry(() -> applyPrefs(ps));
	}

	private void applyPrefs(@NonNull PreferenceStore ps) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			PreferenceStore currentStore = requireNonNull(this.store);
			AudioEffects engineEffects = requireNonNull(this.effects);
			
			Equalizer eq = engineEffects.getEqualizer();
			Virtualizer virt = engineEffects.getVirtualizer();
			BassBoost bass = engineEffects.getBassBoost();
			LoudnessEnhancer le = engineEffects.getLoudnessEnhancer();
			boolean enabled = false;

			if (eq != null && eq.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.EQ_ENABLED, true);
				int preset = currentStore.getIntPref(MediaPrefs.EQ_PRESET);
				short numPresets = eq.getNumberOfPresets();

				if (preset == 0) {
					int[] bands = getBandValues(eq);
					e.setIntPref(MediaPrefs.EQ_PRESET, 0);
					e.setIntArrayPref(MediaPrefs.EQ_BANDS, bands);
				} else if (preset <= numPresets) {
					e.setIntPref(MediaPrefs.EQ_PRESET, (short) preset);
				} else {
					e.setIntPref(MediaPrefs.EQ_PRESET, (short) (numPresets - preset));
				}
			} else {
				e.removePref(MediaPrefs.EQ_ENABLED);
				e.removePref(MediaPrefs.EQ_PRESET);
				e.removePref(MediaPrefs.EQ_BANDS);
			}

			if (virt != null && virt.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.VIRT_ENABLED, true);
				e.setIntPref(MediaPrefs.VIRT_MODE, currentStore.getIntPref(MediaPrefs.VIRT_MODE));
				e.setIntPref(MediaPrefs.VIRT_STRENGTH, virt.getRoundedStrength());
			} else {
				e.removePref(MediaPrefs.VIRT_ENABLED);
				e.removePref(MediaPrefs.VIRT_MODE);
				e.removePref(MediaPrefs.VIRT_STRENGTH);
			}

			if (bass != null && bass.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.BASS_ENABLED, true);
				e.setIntPref(MediaPrefs.BASS_STRENGTH, bass.getRoundedStrength());
			} else {
				e.removePref(MediaPrefs.BASS_ENABLED);
				e.removePref(MediaPrefs.BASS_STRENGTH);
			}

			if (le != null && le.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.VOL_BOOST_ENABLED, true);
				e.setIntPref(MediaPrefs.VOL_BOOST_STRENGTH, (int) (le.getTargetGain() / 10));
			} else {
				e.removePref(MediaPrefs.VOL_BOOST_ENABLED);
				e.removePref(MediaPrefs.VOL_BOOST_STRENGTH);
			}

			if (enabled) e.setBooleanPref(AE_ENABLED, true);
			else e.removePref(AE_ENABLED);
		}
	}

	private void clearPrefs(@NonNull PreferenceStore ps) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.removePref(MediaPrefs.AE_ENABLED);
			e.removePref(MediaPrefs.EQ_ENABLED);
			e.removePref(MediaPrefs.EQ_PRESET);
			e.removePref(MediaPrefs.EQ_BANDS);
			e.removePref(MediaPrefs.VIRT_ENABLED);
			e.removePref(MediaPrefs.VIRT_MODE);
			e.removePref(MediaPrefs.VIRT_STRENGTH);
			e.removePref(MediaPrefs.BASS_ENABLED);
			e.removePref(MediaPrefs.BASS_STRENGTH);
			e.removePref(MediaPrefs.VOL_BOOST_ENABLED);
			e.removePref(MediaPrefs.VOL_BOOST_STRENGTH);
		}
	}

	private void createBands(@NonNull Equalizer eq) {
		short[] range = eq.getBandLevelRange();
		int sbMax = range[1] - range[0];
		String minText = String.valueOf(range[0] / 100);
		String maxText = String.valueOf(range[1] / 100);
		ViewGroup bands = findViewById(R.id.equalizer_bands);
		LayoutInflater inflater = LayoutInflater.from(getContext());

		for (short n = eq.getNumberOfBands(), i = 0; i < n; i++) {
			inflater.inflate(R.layout.equalizer_band, bands);
			ViewGroup bandView = (ViewGroup) bands.getChildAt(i);
			TextView label = bandView.findViewById(R.id.eq_band_title);
			AppCompatSeekBar sb = bandView.findViewById(R.id.eq_band_seek);
			TextView min = bandView.findViewById(R.id.eq_band_min);
			TextView max = bandView.findViewById(R.id.eq_band_max);
			float freq = (float) eq.getCenterFreq(i) / 1000;
			short band = i;
			
			sb.setMax(sbMax);
			sb.setProgress(eq.getBandLevel(i) - range[0]);
			min.setText(minText);
			max.setText(maxText);
			sb.setOnSeekBarChangeListener(new SeekBarListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					runWithRetry(() -> eqBandChanged(eq, band, progress, fromUser));
				}
			});

			if (freq >= 1000) {
				freq /= 1000;
				String strFreq = String.format((freq == Math.floor(freq)) ? "%.0f" : "%.1f", freq);
				label.setText(getResources().getString(R.string.eq_khz, strFreq));
			} else {
				label.setText(getResources().getString(R.string.eq_hz, String.valueOf((int) freq)));
			}
		}
	}
	private void setBandValues(@NonNull Equalizer eq) {
		short[] range = eq.getBandLevelRange();
		ViewGroup bandsView = findViewById(R.id.equalizer_bands);

		for (short n = eq.getNumberOfBands(), i = 0; i < n; i++) {
			ViewGroup band = (ViewGroup) bandsView.getChildAt(i);
			AppCompatSeekBar sb = band.findViewById(R.id.eq_band_seek);
			sb.setProgress(eq.getBandLevel(i) - range[0]);
		}
	}

	private void setUserBandValues(@NonNull Equalizer eq, @NonNull String[] presets, int preset) {
		short[] range = eq.getBandLevelRange();
		int[] bands = MediaPrefs.getUserPresetBands(presets[preset]);
		ViewGroup bandsView = findViewById(R.id.equalizer_bands);

		for (short n = eq.getNumberOfBands(), i = 0; (i < n) && (i < bands.length); i++) {
			ViewGroup band = (ViewGroup) bandsView.getChildAt(i);
			AppCompatSeekBar sb = band.findViewById(R.id.eq_band_seek);
			
			if (effects != null) {
				effects.setEqualizerBandGain(getContext().getApplicationContext(), i, (short) bands[i]);
			}
			sb.setProgress(eq.getBandLevel(i) - range[0]);
		}
	}

	private void configureSwitch(@NonNull CompoundButton sw, @NonNull BooleanSupplier checkSupplier, @NonNull my.app.utils.function.Consumer<Boolean> changeAction) {
		sw.setOnCheckedChangeListener(null);
		sw.setChecked(checkSupplier.getAsBoolean());
		sw.setOnCheckedChangeListener((b, checked) -> runWithRetry(() -> changeAction.accept(checked)));
	}

	private void configureSeek(@NonNull SeekBar sb, @NonNull IntSupplier get, @NonNull ShortConsumer set) {
		sb.setMax(1000);
		sb.setProgress(get.getAsInt());
		sb.setOnSeekBarChangeListener(new SeekBarListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					runWithRetry(() -> set.accept((short) progress));
				}
			}
		});
	}

	private void eqBandChanged(@NonNull Equalizer eq, short band, int progress, boolean fromUser) {
		if (!fromUser || effects == null) return;

		PreferenceStore currentStore = requireNonNull(this.store);
		short[] range = eq.getBandLevelRange();
		short calculatedGain = (short) (progress + range[0]);
		
		effects.setEqualizerBandGain(getContext().getApplicationContext(), band, calculatedGain);

		int p = currentStore.getIntPref(MediaPrefs.EQ_PRESET);
		if ((p != 0) && (p <= eq.getNumberOfPresets())) {
			currentStore.applyIntPref(EQ_PRESET, 0);
		}
	}

	private void savePreset(@NonNull View v) {
		PreferenceView pref = findViewById(R.id.equalizer_preset);
		ListOpts opts = (ListOpts) pref.getOpts();
		if (opts == null) return;
		
		int p = opts.store.getIntPref(MediaPrefs.EQ_PRESET);
		AudioEffects engineEffects = requireNonNull(effects);
		Equalizer eq = requireNonNull(engineEffects.getEqualizer());
		short numPresets = eq.getNumberOfPresets();
		PreferenceStore currentCtrlPrefs = requireNonNull(this.ctrlPrefs);

		if (p > numPresets) {
			int[] bands = getBandValues(eq);
			String[] userPresets = currentCtrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
			userPresets[p - numPresets - 1] = MediaPrefs.toUserPreset(opts.stringValues[p], bands);
			currentCtrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
		} else {
			UiUtils.queryText(getContext(), R.string.preset_name, R.drawable.equalizer).onSuccess(name -> {
				if (name == null) return;

				int[] bands = getBandValues(eq);
				String[] userPresets = currentCtrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
				userPresets = Arrays.copyOf(userPresets, userPresets.length + 1);
				userPresets[userPresets.length - 1] = MediaPrefs.toUserPreset(name, bands);
				opts.stringValues = Arrays.copyOf(opts.stringValues, opts.stringValues.length + 1);
				opts.stringValues[opts.stringValues.length - 1] = name;
				currentCtrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
				opts.store.applyIntPref(MediaPrefs.EQ_PRESET, opts.stringValues.length - 1);
			});
		}
	}

	private void deletePreset(@NonNull View v) {
		PreferenceView pref = findViewById(R.id.equalizer_preset);
		ListOpts opts = (ListOpts) pref.getOpts();
		if (opts == null) return;
		
		int p = opts.store.getIntPref(MediaPrefs.EQ_PRESET);
		AudioEffects engineEffects = requireNonNull(effects);
		Equalizer eq = requireNonNull(engineEffects.getEqualizer());
		short numPresets = eq.getNumberOfPresets();
		PreferenceStore currentCtrlPrefs = requireNonNull(this.ctrlPrefs);
		
		String[] userPresets = currentCtrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
		userPresets = CollectionUtils.remove(userPresets, (p - numPresets - 1));
		opts.stringValues = CollectionUtils.remove(opts.stringValues, p);
		currentCtrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
		opts.store.applyIntPref(MediaPrefs.EQ_PRESET, 0);
	}

	@NonNull
	private int[] getBandValues(@NonNull Equalizer eq) {
		short n = eq.getNumberOfBands();
		int[] bands = new int[n];
		for (short i = 0; i < n; i++) {
			bands[i] = eq.getBandLevel(i);
		}
		return bands;
	}

	private void hide(@IdRes int... ids) {
		for (int id : ids) {
			View view = findViewById(id);
			if (view != null) view.setVisibility(GONE);
		}
	}

	private void show(@IdRes int id) {
		View view = findViewById(id);
		if (view != null) view.setVisibility(VISIBLE);
	}

	@Override
	public void onPreferenceChanged(@NonNull PreferenceStore store, @NonNull List<Pref<?>> prefs) {
		runWithRetry(() -> preferenceChanged(store, prefs));
	}

	private void preferenceChanged(@NonNull PreferenceStore store, @NonNull List<Pref<?>> prefs) {
		if (prefs.contains(MediaPrefs.EQ_PRESET)) {
			int p = store.getIntPref(MediaPrefs.EQ_PRESET);

			if (p == 0) {
				show(R.id.equalizer_preset_save);
				hide(R.id.equalizer_preset_delete);
				return;
			}

			AudioEffects engineEffects = requireNonNull(this.effects);
			Equalizer eq = requireNonNull(engineEffects.getEqualizer());
			short numPresets = eq.getNumberOfPresets();

			if (p > numPresets) {
				p -= numPresets + 1;
				String[] presets = requireNonNull(ctrlPrefs).getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
				show(R.id.equalizer_preset_save);
				show(R.id.equalizer_preset_delete);
				if (p < presets.length) setUserBandValues(eq, presets, p);
				return;
			}

			hide(R.id.equalizer_preset_save, R.id.equalizer_preset_delete);
			eq.usePreset((short) (p - 1));
			
			if (effects != null) {
				short totalBands = eq.getNumberOfBands();
				for (short i = 0; i < totalBands; i++) {
					effects.setEqualizerBandGain(getContext().getApplicationContext(), i, eq.getBandLevel(i));
				}
			}
			setBandValues(eq);
			
		} else if (prefs.contains(MediaPrefs.VIRT_MODE)) {
			AudioEffects engineEffects = requireNonNull(this.effects);
			Virtualizer virt = requireNonNull(engineEffects.getVirtualizer());
			virt.forceVirtualizationMode(store.getIntPref(MediaPrefs.VIRT_MODE));
			
		} else if (prefs.contains(TRACK) && store.getBooleanPref(TRACK)) {
			store.applyBooleanPref(FOLDER, false);
		} else if (prefs.contains(FOLDER) && store.getBooleanPref(FOLDER)) {
			store.applyBooleanPref(TRACK, false);
		}
	}

	@Override
	@Nullable
	public View focusSearch(@NonNull View focused, int direction) {
		if ((direction == FOCUS_UP) && (focused.getId() == R.id.equalizer_switch)) {
			MainActivityDelegate delegate = MainActivityDelegate.get(getContext());
			if (delegate != null && delegate.getToolBar() != null) {
				View v = delegate.getToolBar().findViewById(my.app.utils.R.id.tool_bar_back_button);
				if (v != null && v.getVisibility() == VISIBLE) return v;
			}
		}
		return super.focusSearch(focused, direction);
	}

	@NonNull
	private String presetName(@NonNull String name) {
		switch (name) {
			case "Manual": return getResources().getString(R.string.eq_manual);
			case "Normal": return getResources().getString(R.string.eq_normal);
			case "Classical": return getResources().getString(R.string.eq_classical);
			case "Dance": return getResources().getString(R.string.eq_dance);
			case "Flat": return getResources().getString(R.string.eq_flat);
			case "Folk": return getResources().getString(R.string.eq_folk);
			case "Heavy Metal": return getResources().getString(R.string.eq_heavy_metal);
			case "Hip Hop": return getResources().getString(R.string.eq_hip_hop);
			case "Jazz": return getResources().getString(R.string.eq_jazz);
			case "Pop": return getResources().getString(R.string.eq_pop);
			case "Rock": return getResources().getString(R.string.eq_rock);
			default: return name;
		}
	}

	private static int getEqPreset(@NonNull PlayableItem pi, @NonNull PreferenceStore ctrlPrefs) {
		MediaPrefs prefs = pi.getPrefs();
		if (prefs.getBooleanPref(AE_ENABLED)) {
			return prefs.getIntPref(EQ_PRESET);
		} else {
			prefs = pi.getParent().getPrefs();
			return prefs.getBooleanPref(AE_ENABLED) ? prefs.getIntPref(EQ_PRESET) : ctrlPrefs.getIntPref(EQ_PRESET);
		}
	}

	private static abstract class SeekBarListener implements SeekBar.OnSeekBarChangeListener {
		@Override public void onStartTrackingTouch(SeekBar seekBar) {}
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}
	}
}
