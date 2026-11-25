package my.app.permata.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.BaseProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityPrefs;
import my.app.utils.function.IntConsumer;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.view.DialogBuilder;

public final class CustomThemeManager {

	public static final int SLOT_WINDOW_BG = 0;
	public static final int SLOT_PRIMARY_BARS = 1;
	public static final int SLOT_SURFACE_MENU = 2;
	public static final int SLOT_ACCENT = 3;
	public static final int SLOT_TEXT_PRIMARY = 4;
	public static final int SLOT_TEXT_SECONDARY = 5;
	public static final int SLOT_ICON_TINT = 6;

	public static final class BuiltInThemeInfo {
		public final int themeId;
		@StyleRes public final int styleRes;
		@StringRes public final int nameRes;

		BuiltInThemeInfo(int themeId, @StyleRes int styleRes, @StringRes int nameRes) {
			this.themeId = themeId;
			this.styleRes = styleRes;
			this.nameRes = nameRes;
		}
	}

	public static final List<BuiltInThemeInfo> BUILT_IN_THEMES = List.of(
			new BuiltInThemeInfo(MainActivityPrefs.THEME_CLASSIC, R.style.AppTheme_Classic, R.string.theme_classic),
			new BuiltInThemeInfo(MainActivityPrefs.THEME_DARK, R.style.AppTheme_Dark, R.string.theme_dark),
			new BuiltInThemeInfo(MainActivityPrefs.THEME_BLACK, R.style.AppTheme_Black, R.string.theme_black),
			new BuiltInThemeInfo(MainActivityPrefs.THEME_STAR_WARS, R.style.AppTheme_BlackStarWars, R.string.theme_star_wars),
			new BuiltInThemeInfo(MainActivityPrefs.THEME_PURPLE, R.style.AppTheme_Purple, R.string.theme_purple),
			new BuiltInThemeInfo(MainActivityPrefs.THEME_LIGHT, R.style.AppTheme_Light, R.string.theme_light)
	);

	private static final int[] BUILT_IN_RANDOM_POOL = new int[]{
			MainActivityPrefs.THEME_DARK,
			MainActivityPrefs.THEME_LIGHT,
			MainActivityPrefs.THEME_BLACK,
			MainActivityPrefs.THEME_STAR_WARS,
			MainActivityPrefs.THEME_PURPLE,
			MainActivityPrefs.THEME_CLASSIC
	};

	// 36 curated default useful & famous colors (including Gold, Silver, Bronze, Ruby, Teal, Navy Blue, Emerald)
	private static final int[] DEFAULT_PALETTE_COLORS = new int[]{
			// Row 1: OLED & Dark Neutrals
			0xFF000000, 0xFF121212, 0xFF1E1E2E, 0xFF263238, 0xFF424242, 0xFF757575,
			// Row 2: Whites & Precious Metals (Silver, Gold, Metallic Gold, Bronze)
			0xFFFFFFFF, 0xFFF5F5F5, 0xFFC0C0C0, 0xFFFFD700, 0xFFD4AF37, 0xFFCD7F32,
			// Row 3: Deep Ruby, Ruby, Crimson, Pink, Royal Purple, Violet
			0xFF9B111E, 0xFFE0115F, 0xFFE53935, 0xFFFF4081, 0xFF8E24AA, 0xFF5E35B1,
			// Row 4: Dark UI Navy, Navy Blue, Royal Blue, Sky Blue, Cyan, Electric Cyan
			0xFF0F1E3D, 0xFF000080, 0xFF1E88E5, 0xFF039BE5, 0xFF00BCD4, 0xFF00E5FF,
			// Row 5: Teal, Bright Teal, Deep Emerald, Emerald, Forest Green, Neon Mint
			0xFF008080, 0xFF00A896, 0xFF046307, 0xFF50C878, 0xFF43A047, 0xFF00E676,
			// Row 6: Star Wars Yellow, Amber, Orange, Coral, Deep Bronze, Espresso
			0xFFFFD600, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF8C5A2B, 0xFF4E342E
	};

	private static Integer sessionRandomThemeMain;
	private static Palette sessionRandomCustomMain;
	private static Integer sessionRandomThemeAa;
	private static Palette sessionRandomCustomAa;

	private CustomThemeManager() {
	}

	public static final class Palette {
		public String name;
		@ColorInt public int windowBg;
		@ColorInt public int primaryBars;
		@ColorInt public int surfaceMenu;
		@ColorInt public int accent;
		@ColorInt public int textPrimary;
		@ColorInt public int textSecondary;
		@ColorInt public int iconTint;

		public Palette(String name,
					   @ColorInt int windowBg,
					   @ColorInt int primaryBars,
					   @ColorInt int surfaceMenu,
					   @ColorInt int accent,
					   @ColorInt int textPrimary,
					   @ColorInt int textSecondary,
					   @ColorInt int iconTint) {
			this.name = (name == null || name.trim().isEmpty()) ? "Custom" : name.trim();
			this.windowBg = windowBg;
			this.primaryBars = primaryBars;
			this.surfaceMenu = surfaceMenu;
			this.accent = accent;
			this.textPrimary = textPrimary;
			this.textSecondary = textSecondary;
			this.iconTint = iconTint;
		}

		public static Palette createFallback() {
			return new Palette(
					"Custom",
					0xFF121212,
					0xFF1F1B24,
					0xFF25202B,
					0xFFBB86FC,
					0xFFFFFFFF,
					0xFFE0E0E0,
					0xFFFFFFFF
			);
		}

		public Palette copy() {
			return new Palette(name, windowBg, primaryBars, surfaceMenu, accent,
					textPrimary, textSecondary, iconTint);
		}

		public boolean isLightBackground() {
			return ColorUtils.calculateLuminance(windowBg) > 0.5;
		}

		@ColorInt
		public int getSlot(int slot) {
			return switch (slot) {
				case SLOT_WINDOW_BG -> windowBg;
				case SLOT_PRIMARY_BARS -> primaryBars;
				case SLOT_SURFACE_MENU -> surfaceMenu;
				case SLOT_ACCENT -> accent;
				case SLOT_TEXT_PRIMARY -> textPrimary;
				case SLOT_TEXT_SECONDARY -> textSecondary;
				case SLOT_ICON_TINT -> iconTint;
				default -> primaryBars;
			};
		}

		public void setSlot(int slot, @ColorInt int color) {
			switch (slot) {
				case SLOT_WINDOW_BG -> windowBg = color;
				case SLOT_PRIMARY_BARS -> primaryBars = color;
				case SLOT_SURFACE_MENU -> surfaceMenu = color;
				case SLOT_ACCENT -> accent = color;
				case SLOT_TEXT_PRIMARY -> textPrimary = color;
				case SLOT_TEXT_SECONDARY -> textSecondary = color;
				case SLOT_ICON_TINT -> iconTint = color;
			}
		}

		public JSONObject toJson() {
			JSONObject o = new JSONObject();
			try {
				o.put("name", name);
				o.put("windowBg", windowBg);
				o.put("primaryBars", primaryBars);
				o.put("surfaceMenu", surfaceMenu);
				o.put("accent", accent);
				o.put("textPrimary", textPrimary);
				o.put("textSecondary", textSecondary);
				o.put("iconTint", iconTint);
			} catch (Exception ex) {
				Log.e(ex, "Failed to serialize custom theme palette");
			}
			return o;
		}

		@NonNull
		public static Palette fromJson(@Nullable JSONObject o) {
			Palette def = createFallback();
			if (o == null) return def;
			return new Palette(
					o.optString("name", def.name),
					o.optInt("windowBg", def.windowBg),
					o.optInt("primaryBars", def.primaryBars),
					o.optInt("surfaceMenu", def.surfaceMenu),
					o.optInt("accent", def.accent),
					o.optInt("textPrimary", def.textPrimary),
					o.optInt("textSecondary", def.textSecondary),
					o.optInt("iconTint", def.iconTint)
			);
		}
	}

	// =========================================================================
	// Customizable 36-Swatch Storage & Reset
	// =========================================================================

	@NonNull
	public static int[] getCustomSwatches() {
		int[] defaults = Arrays.copyOf(DEFAULT_PALETTE_COLORS, DEFAULT_PALETTE_COLORS.length);
		try {
			String raw = MainActivityPrefs.get().getStringPref(MainActivityPrefs.CUSTOM_COLOR_SWATCHES);
			if (raw == null || raw.isEmpty()) return defaults;
			JSONArray arr = new JSONArray(raw);
			int len = Math.min(arr.length(), defaults.length);
			for (int i = 0; i < len; i++) {
				defaults[i] = arr.optInt(i, defaults[i]);
			}
		} catch (Exception ex) {
			Log.w(ex, "Failed to load custom color swatches, using defaults");
		}
		return defaults;
	}

	public static void saveCustomSwatches(@NonNull int[] swatches) {
		JSONArray arr = new JSONArray();
		for (int c : swatches) {
			arr.put(c);
		}
		MainActivityPrefs.get().applyStringPref(MainActivityPrefs.CUSTOM_COLOR_SWATCHES, arr.toString());
	}

	@NonNull
	public static int[] resetCustomSwatches() {
		int[] defaults = Arrays.copyOf(DEFAULT_PALETTE_COLORS, DEFAULT_PALETTE_COLORS.length);
		saveCustomSwatches(defaults);
		return defaults;
	}

	// =========================================================================
	// Built-In XML Theme Extractor (Option A)
	// =========================================================================

	@NonNull
	public static Palette extractBuiltInPalette(@NonNull Context ctx, int themeId) {
		BuiltInThemeInfo target = BUILT_IN_THEMES.get(0);
		for (BuiltInThemeInfo info : BUILT_IN_THEMES) {
			if (info.themeId == themeId) {
				target = info;
				break;
			}
		}
		return extractFromStyle(ctx, target.styleRes, ctx.getString(target.nameRes));
	}

	@NonNull
	public static Palette extractFromStyle(@NonNull Context ctx, @StyleRes int styleRes, @NonNull String label) {
		try {
			Context themed = new ContextThemeWrapper(ctx, styleRes);
			int windowBg = resolveAttrColor(themed, android.R.attr.colorBackground, 0xFF121212);
			TypedArray wta = themed.getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowBackground});
			Drawable wd = wta.getDrawable(0);
			wta.recycle();
			if (wd instanceof ColorDrawable cd) {
				windowBg = cd.getColor();
			}

			int primaryBars = resolveAttrColor(themed, androidx.appcompat.R.attr.colorPrimary, 0xFF1F1B24);
			int surfaceMenu = resolveAttrColor(themed, com.google.android.material.R.attr.colorSurface, primaryBars);
			int accent = resolveAttrColor(themed, androidx.appcompat.R.attr.colorAccent, 0xFFBB86FC);
			int textPrimary = resolveAttrColor(themed, android.R.attr.textColorPrimary, Color.WHITE);
			int textSecondary = resolveAttrColor(themed, android.R.attr.textColorSecondary, 0xFFE0E0E0);
			int iconTint = resolveAttrColor(themed, com.google.android.material.R.attr.colorOnPrimary, textPrimary);

			return new Palette(label, windowBg, primaryBars, surfaceMenu, accent,
					textPrimary, textSecondary, iconTint);
		} catch (Exception ex) {
			Log.w(ex, "Failed extracting built-in theme style, using fallback");
			return Palette.createFallback();
		}
	}

	@ColorInt
	private static int resolveAttrColor(@NonNull Context themedCtx, int attr, @ColorInt int fallback) {
		TypedArray ta = themedCtx.getTheme().obtainStyledAttributes(new int[]{attr});
		try {
			ColorStateList csl = ta.getColorStateList(0);
			if (csl != null) return csl.getDefaultColor();
			return ta.getColor(0, fallback);
		} finally {
			ta.recycle();
		}
	}

	// =========================================================================
	// Storage & Presets
	// =========================================================================

	@NonNull
	public static Palette getActiveCustomPalette(boolean auto) {
		MainActivityPrefs prefs = MainActivityPrefs.get();
		PreferenceStore.Pref<my.app.utils.function.Supplier<String>> key =
				(auto && MainActivityPrefs.CUSTOM_THEME_ACTIVE_AA != null)
						? MainActivityPrefs.CUSTOM_THEME_ACTIVE_AA
						: MainActivityPrefs.CUSTOM_THEME_ACTIVE;
		String raw = prefs.getStringPref(key);
		if (raw == null || raw.isEmpty()) {
			Context appCtx = PermataApplication.get();
			int seedTheme = auto ? MainActivityPrefs.THEME_DARK : MainActivityPrefs.THEME_CLASSIC;
			return (appCtx != null) ? extractBuiltInPalette(appCtx, seedTheme) : Palette.createFallback();
		}
		try {
			return Palette.fromJson(new JSONObject(raw));
		} catch (Exception ex) {
			Log.w(ex, "Invalid active custom theme JSON, falling back");
			return Palette.createFallback();
		}
	}

	public static void saveActiveCustomPalette(boolean auto, @NonNull Palette palette) {
		MainActivityPrefs prefs = MainActivityPrefs.get();
		PreferenceStore.Pref<my.app.utils.function.Supplier<String>> key =
				(auto && MainActivityPrefs.CUSTOM_THEME_ACTIVE_AA != null)
						? MainActivityPrefs.CUSTOM_THEME_ACTIVE_AA
						: MainActivityPrefs.CUSTOM_THEME_ACTIVE;
		prefs.applyStringPref(key, palette.toJson().toString());
	}

	@NonNull
	public static List<Palette> getSavedPresets() {
		MainActivityPrefs prefs = MainActivityPrefs.get();
		String raw = prefs.getStringPref(MainActivityPrefs.CUSTOM_THEME_PRESETS);
		if (raw == null || raw.isEmpty()) return Collections.emptyList();
		List<Palette> list = new ArrayList<>();
		try {
			JSONArray arr = new JSONArray(raw);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject item = arr.optJSONObject(i);
				if (item != null) list.add(Palette.fromJson(item));
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to parse saved custom theme presets");
		}
		return list;
	}

	public static void savePreset(@NonNull Palette preset) {
		List<Palette> existing = new ArrayList<>(getSavedPresets());
		boolean replaced = false;
		for (int i = 0; i < existing.size(); i++) {
			if (existing.get(i).name.equalsIgnoreCase(preset.name)) {
				existing.set(i, preset.copy());
				replaced = true;
				break;
			}
		}
		if (!replaced) existing.add(preset.copy());
		persistPresetList(existing);
	}

	public static void deletePreset(@NonNull String presetName) {
		List<Palette> existing = new ArrayList<>(getSavedPresets());
		existing.removeIf(p -> p.name.equalsIgnoreCase(presetName));
		persistPresetList(existing);
	}

	private static void persistPresetList(@NonNull List<Palette> presets) {
		JSONArray arr = new JSONArray();
		for (Palette p : presets) {
			arr.put(p.toJson());
		}
		MainActivityPrefs.get().applyStringPref(MainActivityPrefs.CUSTOM_THEME_PRESETS, arr.toString());
	}

	// =========================================================================
	// Randomizer & Theme Resolution
	// =========================================================================

	public static synchronized void ensureSessionRandomSelected(boolean auto) {
		if (auto) {
			if (sessionRandomThemeAa != null) return;
		} else {
			if (sessionRandomThemeMain != null) return;
		}

		List<Palette> presets = getSavedPresets();
		int totalChoices = BUILT_IN_RANDOM_POOL.length + presets.size();
		int pick = new Random().nextInt(totalChoices);

		int chosenThemeId;
		Palette chosenPalette = null;

		if (pick < BUILT_IN_RANDOM_POOL.length) {
			chosenThemeId = BUILT_IN_RANDOM_POOL[pick];
		} else {
			chosenThemeId = MainActivityPrefs.THEME_CUSTOM;
			chosenPalette = presets.get(pick - BUILT_IN_RANDOM_POOL.length);
		}

		if (auto) {
			sessionRandomThemeAa = chosenThemeId;
			sessionRandomCustomAa = chosenPalette;
			Log.i("[CAR][THEME] Startup Random Theme Selected | id=" + chosenThemeId
					+ (chosenPalette != null ? " (" + chosenPalette.name + ")" : ""));
		} else {
			sessionRandomThemeMain = chosenThemeId;
			sessionRandomCustomMain = chosenPalette;
			Log.i("[PHONE][THEME] Startup Random Theme Selected | id=" + chosenThemeId
					+ (chosenPalette != null ? " (" + chosenPalette.name + ")" : ""));
		}
	}

	public static synchronized void rerollRandomTheme(boolean auto) {
		if (auto) {
			sessionRandomThemeAa = null;
			sessionRandomCustomAa = null;
		} else {
			sessionRandomThemeMain = null;
			sessionRandomCustomMain = null;
		}
		ensureSessionRandomSelected(auto);
	}

	public static int resolveEffectiveThemeId(boolean auto) {
		int prefTheme = MainActivityPrefs.get().getThemePref(auto);
		if (prefTheme == MainActivityPrefs.THEME_RANDOM) {
			ensureSessionRandomSelected(auto);
			return auto ? sessionRandomThemeAa : sessionRandomThemeMain;
		}
		return prefTheme;
	}

	@Nullable
	public static Palette resolveEffectiveCustomPalette(boolean auto) {
		int prefTheme = MainActivityPrefs.get().getThemePref(auto);
		if (prefTheme == MainActivityPrefs.THEME_CUSTOM) {
			return getActiveCustomPalette(auto);
		}
		if (prefTheme == MainActivityPrefs.THEME_RANDOM) {
			ensureSessionRandomSelected(auto);
			int effective = auto ? sessionRandomThemeAa : sessionRandomThemeMain;
			if (effective == MainActivityPrefs.THEME_CUSTOM) {
				Palette p = auto ? sessionRandomCustomAa : sessionRandomCustomMain;
				return (p != null) ? p : getActiveCustomPalette(auto);
			}
		}
		return null;
	}

	@StyleRes
	public static int getBaseStyleForCustom(@NonNull Palette p) {
		return p.isLightBackground() ? R.style.AppTheme_Light : R.style.AppTheme_Dark;
	}

	// =========================================================================
	// Runtime View & Window Styler
	// =========================================================================

	public static void applyIfCustom(@NonNull MainActivityDelegate delegate) {
		boolean auto = delegate.isCarActivity();
		Palette p = resolveEffectiveCustomPalette(auto);
		if (p == null) return;

		try {
			Window w = delegate.getWindow();
			if (w != null) {
				w.setBackgroundDrawable(new ColorDrawable(p.windowBg));
				if (!auto) {
					w.setStatusBarColor(p.primaryBars);
					w.setNavigationBarColor(p.primaryBars);
				}
			}

			View root = delegate.findViewById(R.id.main_activity);
			if (root != null) root.setBackgroundColor(p.windowBg);

			View body = delegate.getBody();
			if (body != null) body.setBackgroundColor(Color.TRANSPARENT);

			styleBarContainer(delegate.getToolBar(), p.primaryBars, p.textPrimary, p.iconTint, p.accent);
			styleBarContainer(delegate.getNavBar(), p.primaryBars, p.textPrimary, p.iconTint, p.accent);
			styleBarContainer(delegate.getControlPanel(), p.primaryBars, p.textPrimary, p.iconTint, p.accent);

			View contextMenu = delegate.findViewById(R.id.context_menu);
			if (contextMenu != null) {
				styleMenuOverlay(contextMenu, p);
			}
			View toolMenu = delegate.findViewById(R.id.tool_menu);
			if (toolMenu != null) {
				styleMenuOverlay(toolMenu, p);
			}

			View fb = delegate.getFloatingButton();
			if (fb != null) {
				fb.setBackgroundTintList(ColorStateList.valueOf(p.primaryBars));
				if (fb instanceof FloatingActionButton fab) {
					fab.setSupportImageTintList(ColorStateList.valueOf(p.iconTint));
					fab.setRippleColor(ColorStateList.valueOf(p.accent));
				} else if (fb instanceof ImageView iv) {
					iv.setImageTintList(ColorStateList.valueOf(p.iconTint));
				}
			}

			View frame = delegate.findViewById(R.id.frame_layout);
			if (frame != null) {
				styleContentHierarchy(frame, p);
				frame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
					Palette active = resolveEffectiveCustomPalette(delegate.isCarActivity());
					if (active != null) {
						styleContentHierarchy(frame, active);
						if (contextMenu != null && contextMenu.getVisibility() == View.VISIBLE) {
							styleMenuOverlay(contextMenu, active);
						}
						if (toolMenu != null && toolMenu.getVisibility() == View.VISIBLE) {
							styleMenuOverlay(toolMenu, active);
						}
					}
				});
			}
		} catch (Exception ex) {
			Log.w(ex, "CustomThemeManager: Failed applying custom palette to views");
		}
	}

	private static void styleBarContainer(@Nullable View bar,
										  @ColorInt int bg,
										  @ColorInt int textColor,
										  @ColorInt int iconTint,
										  @ColorInt int accent) {
		if (bar == null) return;
		bar.setBackgroundColor(bg);
		tintChildrenRecursive(bar, textColor, iconTint, accent, false);
	}

	private static void styleMenuOverlay(@NonNull View menuView, @NonNull Palette p) {
		if (menuView instanceof ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				View child = vg.getChildAt(i);
				if (child.getBackground() != null) {
					child.setBackgroundTintList(ColorStateList.valueOf(p.surfaceMenu));
				}
				tintChildrenRecursive(child, p.textSecondary, p.iconTint, p.accent, true);
			}
		}
	}

	private static void styleContentHierarchy(@NonNull View root, @NonNull Palette p) {
		tintChildrenRecursive(root, p.textSecondary, p.iconTint, p.accent, true);
	}

	private static void tintChildrenRecursive(@NonNull View v,
											  @ColorInt int textColor,
											  @ColorInt int iconTint,
											  @ColorInt int accent,
											  boolean isContentArea) {
		// Never overwrite custom colors inside our Color Picker Sample Preview or Swatches
		if ("keep_own_color".equals(v.getTag())) return;

		if (v instanceof SeekBar sb) {
			sb.setThumbTintList(ColorStateList.valueOf(accent));
			sb.setProgressTintList(ColorStateList.valueOf(accent));
			sb.setSecondaryProgressTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 120)));
		} else if (v instanceof BaseProgressIndicator<?> bpi) {
			bpi.setIndicatorColor(accent);
		} else if (v instanceof ProgressBar pb) {
			pb.setIndeterminateTintList(ColorStateList.valueOf(accent));
			pb.setProgressTintList(ColorStateList.valueOf(accent));
		} else if (v instanceof EditText et) {
			et.setTextColor(textColor);
			et.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150));
		} else if (v instanceof TextView tv) {
			tv.setTextColor(textColor);
		} else if (v instanceof ImageView iv) {
			if (!isContentArea || iv.getImageTintList() != null) {
				iv.setImageTintList(ColorStateList.valueOf(iconTint));
			}
		}

		if (v instanceof RecyclerView rv) {
			for (int i = 0; i < rv.getChildCount(); i++) {
				tintChildrenRecursive(rv.getChildAt(i), textColor, iconTint, accent, isContentArea);
			}
			return;
		}

		if (v instanceof ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				tintChildrenRecursive(vg.getChildAt(i), textColor, iconTint, accent, isContentArea);
			}
		}
	}

	// =========================================================================
	// Color Picker Dialog: Live 7-Element Sample Template + 36+1 Swatches + Full Color Wheel
	// =========================================================================

	@NonNull
	public static String toHex(@ColorInt int color) {
		return String.format(Locale.US, "#%06X", (0xFFFFFF & color));
	}

	public static void showColorPickerDialog(@NonNull MainActivityDelegate delegate,
											 @NonNull CharSequence title,
											 @ColorInt int initialColor,
											 @NonNull IntConsumer onColorSelected) {
		Palette base = getActiveCustomPalette(delegate.isCarActivity());
		showColorPickerDialog(delegate, title, base, SLOT_ACCENT, onColorSelected);
	}

	public static void showColorPickerDialog(@NonNull MainActivityDelegate delegate,
											 @NonNull CharSequence title,
											 @NonNull Palette currentPalette,
											 int editingSlot,
											 @NonNull IntConsumer onColorSelected) {
		Context ctx = delegate.getContext();
		DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		int swatchSize = UiUtils.toIntPx(ctx, 36);
		int swatchMargin = UiUtils.toIntPx(ctx, 4);
		int pad = UiUtils.toIntPx(ctx, 10);

		Palette previewPalette = currentPalette.copy();
		int initialColor = previewPalette.getSlot(editingSlot);
		final int[] selectedColor = new int[]{initialColor};
		final int[] swatches = getCustomSwatches();
		final int[] editingSwatchIdx = new int[]{-1}; // -1 = big preview (+1), 0..35 = grid swatch
		final boolean[] updatingHex = new boolean[]{false};

		ScrollView scroll = new ScrollView(ctx);
		LinearLayout container = new LinearLayout(ctx);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(pad, pad, pad, pad);
		scroll.addView(container);

		// -----------------------------------------------------------------
		// 1. LIVE SAMPLE TEMPLATE (Always visible above both Swatches & Wheel)
		// -----------------------------------------------------------------
		TextView editingCaption = new TextView(ctx);
		editingCaption.setText(ctx.getString(R.string.custom_theme_preview_editing, title));
		editingCaption.setTextSize(12f);
		editingCaption.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		capLp.bottomMargin = UiUtils.toIntPx(ctx, 6);
		editingCaption.setLayoutParams(capLp);
		container.addView(editingCaption);

		SampleTemplateView sampleTemplate = new SampleTemplateView(ctx, previewPalette, editingSlot);
		LinearLayout.LayoutParams tplLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tplLp.bottomMargin = pad;
		sampleTemplate.setLayoutParams(tplLp);
		container.addView(sampleTemplate);

		// -----------------------------------------------------------------
		// 2. PALETTE PANEL (Big Selected +1 Swatch, Hex Input, Reset, 36 Swatches)
		// -----------------------------------------------------------------
		LinearLayout palettePanel = new LinearLayout(ctx);
		palettePanel.setOrientation(LinearLayout.VERTICAL);
		container.addView(palettePanel);

		// Big "+1" Selected Preview Box + Hex Input
		LinearLayout previewRow = new LinearLayout(ctx);
		previewRow.setOrientation(LinearLayout.HORIZONTAL);
		previewRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams previewRowLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		previewRowLp.bottomMargin = pad;
		previewRow.setLayoutParams(previewRowLp);

		View bigPreviewBox = new View(ctx);
		bigPreviewBox.setTag("keep_own_color");
		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(swatchSize * 2, swatchSize);
		boxLp.rightMargin = pad;
		bigPreviewBox.setLayoutParams(boxLp);
		bigPreviewBox.setFocusable(true);
		bigPreviewBox.setClickable(true);
		bigPreviewBox.setBackground(createSwatchDrawable(initialColor, true));
		previewRow.addView(bigPreviewBox);

		EditText hexInput = delegate.createEditText(ctx);
		hexInput.setText(toHex(initialColor));
		hexInput.setSingleLine(true);
		hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
		hexInput.setLayoutParams(new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		previewRow.addView(hexInput);
		palettePanel.addView(previewRow);

		// Swatches Header Row + Reset 36 Defaults Button
		LinearLayout swatchHeaderRow = new LinearLayout(ctx);
		swatchHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
		swatchHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		shLp.bottomMargin = UiUtils.toIntPx(ctx, 6);
		swatchHeaderRow.setLayoutParams(shLp);

		TextView swatchHint = new TextView(ctx);
		swatchHint.setText(R.string.custom_theme_swatches_hint);
		swatchHint.setTextSize(11f);
		swatchHint.setLayoutParams(new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		swatchHeaderRow.addView(swatchHint);

		TextView resetBtn = createPillButton(ctx, ctx.getString(R.string.custom_theme_reset_swatches), 0xFF455A64);
		swatchHeaderRow.addView(resetBtn);
		palettePanel.addView(swatchHeaderRow);

		// 36-Swatch Grid
		GridLayout grid = new GridLayout(ctx);
		int cols = Math.max(4, Math.min(6, (dm.widthPixels / 2) / (swatchSize + swatchMargin * 2)));
		grid.setColumnCount(cols);
		grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
		palettePanel.addView(grid);

		// -----------------------------------------------------------------
		// 3. IN-PLACE FULL SPECTRUM COLOR WHEEL PANEL (Hidden until swatch/preview tapped)
		// -----------------------------------------------------------------
		LinearLayout wheelPanel = new LinearLayout(ctx);
		wheelPanel.setOrientation(LinearLayout.VERTICAL);
		wheelPanel.setGravity(Gravity.CENTER_HORIZONTAL);
		wheelPanel.setVisibility(View.GONE);
		container.addView(wheelPanel);

		TextView wheelHeader = new TextView(ctx);
		wheelHeader.setTextSize(13f);
		wheelHeader.setTypeface(null, Typeface.BOLD);
		wheelHeader.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams whLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		whLp.bottomMargin = UiUtils.toIntPx(ctx, 6);
		wheelHeader.setLayoutParams(whLp);
		wheelPanel.addView(wheelHeader);

		int wheelSize = Math.min(UiUtils.toIntPx(ctx, 210),
				Math.max(UiUtils.toIntPx(ctx, 140), (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.42f)));
		ColorWheelView colorWheel = new ColorWheelView(ctx);
		colorWheel.setTag("keep_own_color");
		LinearLayout.LayoutParams cwLp = new LinearLayout.LayoutParams(wheelSize, wheelSize);
		cwLp.gravity = Gravity.CENTER_HORIZONTAL;
		cwLp.bottomMargin = UiUtils.toIntPx(ctx, 8);
		colorWheel.setLayoutParams(cwLp);
		wheelPanel.addView(colorWheel);

		// Brightness (Value) slider row
		LinearLayout brightRow = new LinearLayout(ctx);
		brightRow.setOrientation(LinearLayout.HORIZONTAL);
		brightRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		brLp.bottomMargin = UiUtils.toIntPx(ctx, 8);
		brightRow.setLayoutParams(brLp);

		TextView darkLabel = new TextView(ctx);
		darkLabel.setText("Dark");
		darkLabel.setTextSize(11f);
		brightRow.addView(darkLabel);

		SeekBar brightnessBar = new SeekBar(ctx);
		brightnessBar.setTag("keep_own_color");
		brightnessBar.setMax(100);
		brightnessBar.setProgress(100);
		LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		sbLp.leftMargin = UiUtils.toIntPx(ctx, 6);
		sbLp.rightMargin = UiUtils.toIntPx(ctx, 6);
		brightnessBar.setLayoutParams(sbLp);
		brightRow.addView(brightnessBar);

		TextView lightLabel = new TextView(ctx);
		lightLabel.setText("Bright");
		lightLabel.setTextSize(11f);
		brightRow.addView(lightLabel);
		wheelPanel.addView(brightRow);

		// Live Wheel Color Preview + Hex
		LinearLayout wheelPreviewRow = new LinearLayout(ctx);
		wheelPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
		wheelPreviewRow.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams wpLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		wpLp.bottomMargin = UiUtils.toIntPx(ctx, 10);
		wheelPreviewRow.setLayoutParams(wpLp);

		View wheelSwatchPreview = new View(ctx);
		wheelSwatchPreview.setTag("keep_own_color");
		LinearLayout.LayoutParams wspLp = new LinearLayout.LayoutParams(swatchSize * 2, UiUtils.toIntPx(ctx, 30));
		wspLp.rightMargin = pad;
		wheelSwatchPreview.setLayoutParams(wspLp);
		wheelPreviewRow.addView(wheelSwatchPreview);

		TextView wheelHexText = new TextView(ctx);
		wheelHexText.setTextSize(14f);
		wheelHexText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		wheelPreviewRow.addView(wheelHexText);
		wheelPanel.addView(wheelPreviewRow);

		// Wheel Action Buttons (Back to Swatches / Apply Color)
		LinearLayout wheelBtnRow = new LinearLayout(ctx);
		wheelBtnRow.setOrientation(LinearLayout.HORIZONTAL);
		wheelBtnRow.setGravity(Gravity.CENTER);
		wheelPanel.addView(wheelBtnRow);

		TextView wheelBackBtn = createPillButton(ctx, ctx.getString(R.string.custom_theme_wheel_back), 0xFF455A64);
		LinearLayout.LayoutParams wbLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		wbLp.rightMargin = pad;
		wheelBackBtn.setLayoutParams(wbLp);
		wheelBtnRow.addView(wheelBackBtn);

		TextView wheelApplyBtn = createPillButton(ctx, ctx.getString(R.string.custom_theme_wheel_apply), 0xFF1E88E5);
		wheelBtnRow.addView(wheelApplyBtn);

		// -----------------------------------------------------------------
		// 4. INTERACTION WIRING
		// -----------------------------------------------------------------
		List<View> swatchViews = new ArrayList<>(swatches.length);

		Runnable refreshSwatchGrid = () -> {
			for (int idx = 0; idx < swatchViews.size(); idx++) {
				swatchViews.get(idx).setBackground(
						createSwatchDrawable(swatches[idx], swatches[idx] == selectedColor[0]));
			}
		};

		IntConsumer applyPreviewColor = c -> {
			selectedColor[0] = c;
			previewPalette.setSlot(editingSlot, c);
			sampleTemplate.update(previewPalette);
			bigPreviewBox.setBackground(createSwatchDrawable(c, true));
			updatingHex[0] = true;
			hexInput.setText(toHex(c));
			updatingHex[0] = false;
			refreshSwatchGrid.run();
		};

		final int[] wheelWorkingColor = new int[]{initialColor};
		final int[] colorBeforeWheel = new int[]{initialColor};

		Runnable syncWheelReadout = () -> {
			int c = wheelWorkingColor[0];
			wheelSwatchPreview.setBackground(createSwatchDrawable(c, true));
			wheelHexText.setText(toHex(c));
			// Live-update the Sample Template at the top as the user drags the Color Wheel!
			previewPalette.setSlot(editingSlot, c);
			sampleTemplate.update(previewPalette);
		};

		colorWheel.setOnColorChangedListener(c -> {
			wheelWorkingColor[0] = c;
			syncWheelReadout.run();
		});

		brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					colorWheel.setBrightness(progress / 100f);
					wheelWorkingColor[0] = colorWheel.getColor();
					syncWheelReadout.run();
				}
			}
			@Override public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		IntConsumer openWheelForTarget = targetIndex -> {
			editingSwatchIdx[0] = targetIndex;
			colorBeforeWheel[0] = selectedColor[0];
			int startColor = (targetIndex >= 0 && targetIndex < swatches.length)
					? swatches[targetIndex]
					: selectedColor[0];
			wheelWorkingColor[0] = startColor;
			colorWheel.setColor(startColor);
			brightnessBar.setProgress(Math.round(colorWheel.getBrightness() * 100f));
			wheelHeader.setText(targetIndex >= 0
					? String.format(Locale.US, "Color Wheel  •  Swatch #%d", targetIndex + 1)
					: "Color Wheel  •  Selected Color");
			syncWheelReadout.run();
			palettePanel.setVisibility(View.GONE);
			wheelPanel.setVisibility(View.VISIBLE);
		};

		// Populate 36 customizable swatches
		for (int i = 0; i < swatches.length; i++) {
			final int idx = i;
			View swatch = new View(ctx);
			swatch.setTag("keep_own_color");
			GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
			glp.width = swatchSize;
			glp.height = swatchSize;
			glp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
			swatch.setLayoutParams(glp);
			swatch.setFocusable(true);
			swatch.setClickable(true);
			swatch.setBackground(createSwatchDrawable(swatches[idx], swatches[idx] == initialColor));
			swatch.setOnClickListener(v -> {
				applyPreviewColor.accept(swatches[idx]);
				openWheelForTarget.accept(idx);
			});
			swatchViews.add(swatch);
			grid.addView(swatch);
		}

		// Big "+1" Preview Box also opens Full Spectrum Color Wheel
		bigPreviewBox.setOnClickListener(v -> openWheelForTarget.accept(-1));

		// Reset 36 Swatches to Default Famous/Useful Palette
		resetBtn.setOnClickListener(v -> {
			int[] restored = resetCustomSwatches();
			System.arraycopy(restored, 0, swatches, 0, Math.min(restored.length, swatches.length));
			refreshSwatchGrid.run();
		});

		// Wheel Back (cancel wheel edit & restore preview)
		wheelBackBtn.setOnClickListener(v -> {
			wheelPanel.setVisibility(View.GONE);
			palettePanel.setVisibility(View.VISIBLE);
			applyPreviewColor.accept(colorBeforeWheel[0]);
		});

		// Wheel Apply (persist customized swatch if editing 0..35, and apply color)
		wheelApplyBtn.setOnClickListener(v -> {
			int chosen = wheelWorkingColor[0];
			int idx = editingSwatchIdx[0];
			if (idx >= 0 && idx < swatches.length) {
				swatches[idx] = chosen;
				saveCustomSwatches(swatches);
			}
			wheelPanel.setVisibility(View.GONE);
			palettePanel.setVisibility(View.VISIBLE);
			applyPreviewColor.accept(chosen);
		});

		// Live Hex Typing
		hexInput.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override
			public void afterTextChanged(Editable s) {
				if (updatingHex[0]) return;
				String typed = s != null ? s.toString().trim() : "";
				if (typed.length() == 6 && !typed.startsWith("#")) typed = "#" + typed;
				if (typed.length() == 7 || typed.length() == 9) {
					try {
						int parsed = Color.parseColor(typed);
						selectedColor[0] = parsed;
						previewPalette.setSlot(editingSlot, parsed);
						sampleTemplate.update(previewPalette);
						bigPreviewBox.setBackground(createSwatchDrawable(parsed, true));
						refreshSwatchGrid.run();
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		});

		DialogBuilder db = delegate.createDialogBuilder(ctx);
		db.setTitle(title);
		db.setView(scroll);
		db.setPositiveButton(android.R.string.ok, (dlg, which) -> {
			if (wheelPanel.getVisibility() == View.VISIBLE) {
				int chosen = wheelWorkingColor[0];
				int idx = editingSwatchIdx[0];
				if (idx >= 0 && idx < swatches.length) {
					swatches[idx] = chosen;
					saveCustomSwatches(swatches);
				}
				selectedColor[0] = chosen;
			} else {
				String typed = hexInput.getText() != null ? hexInput.getText().toString().trim() : "";
				if (!typed.isEmpty()) {
					if (!typed.startsWith("#")) typed = "#" + typed;
					try {
						selectedColor[0] = Color.parseColor(typed);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			onColorSelected.accept(selectedColor[0]);
		});
		db.setNegativeButton(android.R.string.cancel, null);
		db.show();
	}

	@NonNull
	private static TextView createPillButton(@NonNull Context ctx, @NonNull String label, @ColorInt int bgColor) {
		TextView btn = new TextView(ctx);
		btn.setTag("keep_own_color");
		btn.setText(label);
		btn.setTextSize(12f);
		btn.setTypeface(null, Typeface.BOLD);
		btn.setTextColor(Color.WHITE);
		int hPad = UiUtils.toIntPx(ctx, 10);
		int vPad = UiUtils.toIntPx(ctx, 5);
		btn.setPadding(hPad, vPad, hPad, vPad);
		btn.setFocusable(true);
		btn.setClickable(true);
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(UiUtils.toIntPx(ctx, 14));
		bg.setColor(bgColor);
		bg.setStroke(2, 0x88FFFFFF);
		btn.setBackground(bg);
		return btn;
	}

	@NonNull
	private static GradientDrawable createSwatchDrawable(@ColorInt int fillColor, boolean selected) {
		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.RECTANGLE);
		gd.setCornerRadius(10f);
		gd.setColor(fillColor);
		int strokeColor = selected
				? (ColorUtils.calculateLuminance(fillColor) > 0.5 ? Color.BLACK : Color.WHITE)
				: 0x66888888;
		gd.setStroke(selected ? 6 : 2, strokeColor);
		return gd;
	}

	// =========================================================================
	// Live Mini-App Sample Template Showing All 7 Theme Elements
	// =========================================================================

	private static final class SampleTemplateView extends LinearLayout {
		private final int highlightSlot;
		private final LinearLayout screenBox;
		private final LinearLayout topBar;
		private final ImageView topIcon;
		private final TextView topTitle;
		private final ImageView topMenuIcon;
		private final LinearLayout listTrackRow;
		private final ImageView listIcon;
		private final TextView listText;
		private final LinearLayout progressTrack;
		private final View progressFill;
		private final LinearLayout menuCard;
		private final ImageView menuIcon;
		private final TextView menuText;
		private final LinearLayout bottomBar;
		private final ImageView playIcon;
		private final TextView nowPlayingText;
		private final View bottomAccentBar;

		SampleTemplateView(@NonNull Context ctx, @NonNull Palette palette, int highlightSlot) {
			super(ctx);
			this.highlightSlot = highlightSlot;
			setTag("keep_own_color");
			setOrientation(VERTICAL);

			int dp2 = UiUtils.toIntPx(ctx, 2);
			int dp4 = UiUtils.toIntPx(ctx, 4);
			int dp6 = UiUtils.toIntPx(ctx, 6);
			int iconPx = UiUtils.toIntPx(ctx, 16);

			// Outer Mini App Screen (Window Background)
			screenBox = new LinearLayout(ctx);
			screenBox.setTag("keep_own_color");
			screenBox.setOrientation(VERTICAL);
			screenBox.setPadding(dp2, dp2, dp2, dp2);
			addView(screenBox, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			// --- Top ToolBar (Bars & Control Panel + Primary Text + Icon Tint) ---
			topBar = new LinearLayout(ctx);
			topBar.setTag("keep_own_color");
			topBar.setOrientation(HORIZONTAL);
			topBar.setGravity(Gravity.CENTER_VERTICAL);
			topBar.setPadding(dp6, dp4, dp6, dp4);

			topIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_manage, iconPx);
			topBar.addView(topIcon);

			topTitle = new TextView(ctx);
			topTitle.setTag("keep_own_color");
			topTitle.setText("PermataAuto (Bar Text)");
			topTitle.setTextSize(11f);
			topTitle.setTypeface(null, Typeface.BOLD);
			topTitle.setPadding(dp4, dp2, dp4, dp2);
			LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			ttLp.leftMargin = dp4;
			topBar.addView(topTitle, ttLp);

			topMenuIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_more, iconPx);
			topBar.addView(topMenuIcon);
			screenBox.addView(topBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			// --- Middle Content Area (Window Background + Secondary Text + Accent + Menus & Dialogs) ---
			LinearLayout contentRow = new LinearLayout(ctx);
			contentRow.setTag("keep_own_color");
			contentRow.setOrientation(HORIZONTAL);
			contentRow.setGravity(Gravity.CENTER_VERTICAL);
			contentRow.setPadding(dp6, dp6, dp6, dp6);

			// Left: Track list item + Accent SeekBar
			LinearLayout leftCol = new LinearLayout(ctx);
			leftCol.setTag("keep_own_color");
			leftCol.setOrientation(VERTICAL);
			LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f);
			leftLp.rightMargin = dp6;

			listTrackRow = new LinearLayout(ctx);
			listTrackRow.setTag("keep_own_color");
			listTrackRow.setOrientation(HORIZONTAL);
			listTrackRow.setGravity(Gravity.CENTER_VERTICAL);

			listIcon = makeMiniIcon(ctx, android.R.drawable.ic_media_next, iconPx);
			listTrackRow.addView(listIcon);

			listText = new TextView(ctx);
			listText.setTag("keep_own_color");
			listText.setText("Track List (Secondary)");
			listText.setTextSize(10f);
			listText.setPadding(dp4, dp2, dp4, dp2);
			listTrackRow.addView(listText);
			leftCol.addView(listTrackRow);

			// Accent progress bar mockup
			progressTrack = new LinearLayout(ctx);
			progressTrack.setTag("keep_own_color");
			progressTrack.setOrientation(HORIZONTAL);
			progressTrack.setPadding(dp2, dp2, dp2, dp2);
			LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.toIntPx(ctx, 10));
			ptLp.topMargin = dp4;

			progressFill = new View(ctx);
			progressFill.setTag("keep_own_color");
			progressTrack.addView(progressFill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.65f));
			View progressRest = new View(ctx);
			progressRest.setTag("keep_own_color");
			progressRest.setBackgroundColor(0x44888888);
			progressTrack.addView(progressRest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.35f));
			leftCol.addView(progressTrack, ptLp);

			contentRow.addView(leftCol, leftLp);

			// Right: Floating Menu / Dialog Card
			menuCard = new LinearLayout(ctx);
			menuCard.setTag("keep_own_color");
			menuCard.setOrientation(HORIZONTAL);
			menuCard.setGravity(Gravity.CENTER_VERTICAL);
			menuCard.setPadding(dp6, dp6, dp6, dp6);

			menuIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_info_details, iconPx);
			menuCard.addView(menuIcon);

			menuText = new TextView(ctx);
			menuText.setTag("keep_own_color");
			menuText.setText("Menu / Dialog");
			menuText.setTextSize(10f);
			menuText.setPadding(dp4, dp2, dp4, dp2);
			menuCard.addView(menuText);

			contentRow.addView(menuCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f));
			screenBox.addView(contentRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			// --- Bottom Control Panel (Bars & Control Panel + Accent + Primary Text + Icon Tint) ---
			bottomAccentBar = new View(ctx);
			bottomAccentBar.setTag("keep_own_color");
			screenBox.addView(bottomAccentBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.toIntPx(ctx, 3)));

			bottomBar = new LinearLayout(ctx);
			bottomBar.setTag("keep_own_color");
			bottomBar.setOrientation(HORIZONTAL);
			bottomBar.setGravity(Gravity.CENTER_VERTICAL);
			bottomBar.setPadding(dp6, dp4, dp6, dp4);

			playIcon = makeMiniIcon(ctx, android.R.drawable.ic_media_play, iconPx);
			bottomBar.addView(playIcon);

			nowPlayingText = new TextView(ctx);
			nowPlayingText.setTag("keep_own_color");
			nowPlayingText.setText("Control Panel (Primary Text)");
			nowPlayingText.setTextSize(10f);
			nowPlayingText.setPadding(dp4, dp2, dp4, dp2);
			LinearLayout.LayoutParams npLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			npLp.leftMargin = dp4;
			bottomBar.addView(nowPlayingText, npLp);
			screenBox.addView(bottomBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			update(palette);
		}

		private static ImageView makeMiniIcon(@NonNull Context ctx, int drawableRes, int sizePx) {
			ImageView iv = new ImageView(ctx);
			iv.setTag("keep_own_color");
			iv.setImageResource(drawableRes);
			iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
			int pad = UiUtils.toIntPx(ctx, 2);
			iv.setPadding(pad, pad, pad, pad);
			iv.setLayoutParams(new LinearLayout.LayoutParams(sizePx + pad * 2, sizePx + pad * 2));
			return iv;
		}

		void update(@NonNull Palette p) {
			int hiColor = 0xFF00E5FF; // vivid cyan highlight ring
			int hiAlt = 0xFFFF1744;   // fallback vivid red if element itself is cyan-ish

			// 1. Window Background
			screenBox.setBackground(makeBox(p.windowBg, 10f,
					highlightSlot == SLOT_WINDOW_BG ? pickHighlight(p.windowBg, hiColor, hiAlt) : 0x88888888,
					highlightSlot == SLOT_WINDOW_BG ? 6 : 2));

			// 2. Bars & Control Panel
			int barBorder = highlightSlot == SLOT_PRIMARY_BARS ? pickHighlight(p.primaryBars, hiColor, hiAlt) : Color.TRANSPARENT;
			int barStroke = highlightSlot == SLOT_PRIMARY_BARS ? 5 : 0;
			topBar.setBackground(makeBox(p.primaryBars, 6f, barBorder, barStroke));
			bottomBar.setBackground(makeBox(p.primaryBars, 6f, barBorder, barStroke));

			// 3. Menus & Dialogs
			menuCard.setBackground(makeBox(p.surfaceMenu, 8f,
					highlightSlot == SLOT_SURFACE_MENU ? pickHighlight(p.surfaceMenu, hiColor, hiAlt) : 0x55888888,
					highlightSlot == SLOT_SURFACE_MENU ? 5 : 2));

			// 4. Accent & Progress
			progressFill.setBackgroundColor(p.accent);
			bottomAccentBar.setBackgroundColor(p.accent);
			progressTrack.setBackground(makeBox(Color.TRANSPARENT, 4f,
					highlightSlot == SLOT_ACCENT ? pickHighlight(p.windowBg, hiColor, hiAlt) : Color.TRANSPARENT,
					highlightSlot == SLOT_ACCENT ? 4 : 0));

			// 5. Primary Text (Bars)
			topTitle.setTextColor(p.textPrimary);
			nowPlayingText.setTextColor(p.textPrimary);
			Drawable primaryTextHi = highlightSlot == SLOT_TEXT_PRIMARY
					? makeBox(Color.TRANSPARENT, 4f, pickHighlight(p.primaryBars, hiColor, hiAlt), 3)
					: null;
			topTitle.setBackground(primaryTextHi);
			nowPlayingText.setBackground(primaryTextHi != null ? primaryTextHi.getConstantState().newDrawable() : null);

			// 6. Secondary Text (Lists & Menus)
			listText.setTextColor(p.textSecondary);
			menuText.setTextColor(p.textSecondary);
			listText.setBackground(highlightSlot == SLOT_TEXT_SECONDARY
					? makeBox(Color.TRANSPARENT, 4f, pickHighlight(p.windowBg, hiColor, hiAlt), 3)
					: null);
			menuText.setBackground(highlightSlot == SLOT_TEXT_SECONDARY
					? makeBox(Color.TRANSPARENT, 4f, pickHighlight(p.surfaceMenu, hiColor, hiAlt), 3)
					: null);

			// 7. Icon Tint
			ColorStateList iconCsl = ColorStateList.valueOf(p.iconTint);
			ImageView[] icons = new ImageView[]{topIcon, topMenuIcon, listIcon, menuIcon, playIcon};
			for (ImageView iv : icons) {
				iv.setImageTintList(iconCsl);
				iv.setBackground(highlightSlot == SLOT_ICON_TINT
						? makeBox(Color.TRANSPARENT, 6f, pickHighlight(p.primaryBars, hiColor, hiAlt), 3)
						: null);
			}
		}

		@ColorInt
		private static int pickHighlight(@ColorInt int bg, @ColorInt int primaryHi, @ColorInt int altHi) {
			return ColorUtils.calculateContrast(primaryHi, bg) >= 2.5 ? primaryHi : altHi;
		}

		@NonNull
		private static GradientDrawable makeBox(@ColorInt int fill, float radius, @ColorInt int strokeColor, int strokeWidth) {
			GradientDrawable gd = new GradientDrawable();
			gd.setShape(GradientDrawable.RECTANGLE);
			gd.setCornerRadius(radius);
			gd.setColor(fill);
			if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
				gd.setStroke(strokeWidth, strokeColor);
			}
			return gd;
		}
	}

	// =========================================================================
	// Full Spectrum HSV Color Wheel (Pure Android Canvas — Zero Dependencies)
	// =========================================================================

	private static final class ColorWheelView extends View {
		private static final int[] HUE_COLORS = new int[]{
				0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
				0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
		};

		private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint satPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint valOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint selectorOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint selectorInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		private final float[] hsv = new float[]{0f, 1f, 1f};
		private float cx, cy, radius;
		@Nullable private IntConsumer listener;

		ColorWheelView(@NonNull Context ctx) {
			super(ctx);
			borderPaint.setStyle(Paint.Style.STROKE);
			borderPaint.setStrokeWidth(3f);
			borderPaint.setColor(0x88FFFFFF);

			selectorOuterPaint.setStyle(Paint.Style.STROKE);
			selectorOuterPaint.setStrokeWidth(5f);
			selectorOuterPaint.setColor(Color.WHITE);

			selectorInnerPaint.setStyle(Paint.Style.STROKE);
			selectorInnerPaint.setStrokeWidth(2.5f);
			selectorInnerPaint.setColor(Color.BLACK);
		}

		void setOnColorChangedListener(@Nullable IntConsumer listener) {
			this.listener = listener;
		}

		void setColor(@ColorInt int color) {
			Color.colorToHSV(color, hsv);
			invalidate();
		}

		@ColorInt
		int getColor() {
			return Color.HSVToColor(hsv);
		}

		void setBrightness(float value) {
			hsv[2] = Math.max(0f, Math.min(1f, value));
			invalidate();
		}

		float getBrightness() {
			return hsv[2];
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
			cx = w / 2f;
			cy = h / 2f;
			radius = Math.max(1f, Math.min(w, h) / 2f - 10f);
			huePaint.setShader(new SweepGradient(cx, cy, HUE_COLORS, null));
			satPaint.setShader(new RadialGradient(cx, cy, radius,
					0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
		}

		@Override
		protected void onDraw(@NonNull Canvas canvas) {
			super.onDraw(canvas);
			// 1. Hue sweep
			canvas.drawCircle(cx, cy, radius, huePaint);
			// 2. Saturation radial white center fade
			canvas.drawCircle(cx, cy, radius, satPaint);
			// 3. Value (brightness) darkening overlay
			int darkAlpha = Math.round((1f - hsv[2]) * 255f);
			if (darkAlpha > 0) {
				valOverlayPaint.setColor(Color.argb(darkAlpha, 0, 0, 0));
				canvas.drawCircle(cx, cy, radius, valOverlayPaint);
			}
			// 4. Outer crisp border
			canvas.drawCircle(cx, cy, radius, borderPaint);

			// 5. Selector crosshair ring
			double rad = Math.toRadians(hsv[0]);
			float px = cx + (float) (Math.cos(rad) * hsv[1] * radius);
			float py = cy + (float) (Math.sin(rad) * hsv[1] * radius);
			canvas.drawCircle(px, py, 14f, selectorOuterPaint);
			canvas.drawCircle(px, py, 11f, selectorInnerPaint);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> {
					if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
					updateFromTouch(event.getX(), event.getY());
					return true;
				}
				case MotionEvent.ACTION_MOVE -> {
					updateFromTouch(event.getX(), event.getY());
					return true;
				}
				case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
					return true;
				}
			}
			return super.onTouchEvent(event);
		}

		private void updateFromTouch(float x, float y) {
			float dx = x - cx;
			float dy = y - cy;
			float dist = (float) Math.hypot(dx, dy);
			float sat = Math.min(1f, dist / radius);
			float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
			if (angle < 0f) angle += 360f;

			hsv[0] = angle;
			hsv[1] = sat;
			invalidate();
			if (listener != null) {
				listener.accept(Color.HSVToColor(hsv));
			}
		}
	}
}