package my.app.permata.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
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

	private static final int[] PALETTE_COLORS = new int[]{
			0xFF000000, 0xFF121212, 0xFF1E1E2E, 0xFF263238, 0xFF37474F, 0xFF424242,
			0xFFFFFFFF, 0xFFF5F5F5, 0xFFE0E0E0, 0xFFB0BEC5, 0xFF90A4AE, 0xFF757575,
			0xFFB71C1C, 0xFFE53935, 0xFFFF5252, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
			0xFF311B92, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1, 0xFF00897B,
			0xFF1B5E20, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835, 0xFFFFB300,
			0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF4E342E, 0xFFFFD600, 0xFF00E676
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
	// Color Palette Swatch + Hex Picker UI
	// =========================================================================

	@NonNull
	public static String toHex(@ColorInt int color) {
		return String.format(Locale.US, "#%06X", (0xFFFFFF & color));
	}

	public static void showColorPickerDialog(@NonNull MainActivityDelegate delegate,
											 @NonNull CharSequence title,
											 @ColorInt int initialColor,
											 @NonNull IntConsumer onColorSelected) {
		Context ctx = delegate.getContext();
		DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		int swatchSize = UiUtils.toIntPx(ctx, 38);
		int swatchMargin = UiUtils.toIntPx(ctx, 5);
		int pad = UiUtils.toIntPx(ctx, 12);

		ScrollView scroll = new ScrollView(ctx);
		LinearLayout container = new LinearLayout(ctx);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(pad, pad, pad, pad);
		scroll.addView(container);

		LinearLayout previewRow = new LinearLayout(ctx);
		previewRow.setOrientation(LinearLayout.HORIZONTAL);
		previewRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams previewRowLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		previewRowLp.bottomMargin = pad;
		previewRow.setLayoutParams(previewRowLp);

		View previewBox = new View(ctx);
		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(swatchSize * 2, swatchSize);
		boxLp.rightMargin = pad;
		previewBox.setLayoutParams(boxLp);
		previewBox.setBackground(createSwatchDrawable(initialColor, true));
		previewRow.addView(previewBox);

		EditText hexInput = delegate.createEditText(ctx);
		hexInput.setText(toHex(initialColor));
		hexInput.setSingleLine(true);
		hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
		hexInput.setLayoutParams(new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		previewRow.addView(hexInput);
		container.addView(previewRow);

		final int[] selectedColor = new int[]{initialColor};

		GridLayout grid = new GridLayout(ctx);
		int cols = Math.max(4, Math.min(6, (dm.widthPixels / 2) / (swatchSize + swatchMargin * 2)));
		grid.setColumnCount(cols);
		grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

		List<View> swatchViews = new ArrayList<>(PALETTE_COLORS.length);
		for (int c : PALETTE_COLORS) {
			View swatch = new View(ctx);
			GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
			glp.width = swatchSize;
			glp.height = swatchSize;
			glp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
			swatch.setLayoutParams(glp);
			swatch.setFocusable(true);
			swatch.setClickable(true);
			swatch.setBackground(createSwatchDrawable(c, c == initialColor));
			swatch.setOnClickListener(v -> {
				selectedColor[0] = c;
				hexInput.setText(toHex(c));
				previewBox.setBackground(createSwatchDrawable(c, true));
				for (int idx = 0; idx < swatchViews.size(); idx++) {
					swatchViews.get(idx).setBackground(
							createSwatchDrawable(PALETTE_COLORS[idx], PALETTE_COLORS[idx] == c));
				}
			});
			swatchViews.add(swatch);
			grid.addView(swatch);
		}
		container.addView(grid);

		DialogBuilder db = delegate.createDialogBuilder(ctx);
		db.setTitle(title);
		db.setView(scroll);
		db.setPositiveButton(android.R.string.ok, (dlg, which) -> {
			String typed = hexInput.getText() != null ? hexInput.getText().toString().trim() : "";
			if (!typed.isEmpty()) {
				if (!typed.startsWith("#")) typed = "#" + typed;
				try {
					selectedColor[0] = Color.parseColor(typed);
				} catch (IllegalArgumentException ignored) {
				}
			}
			onColorSelected.accept(selectedColor[0]);
		});
		db.setNegativeButton(android.R.string.cancel, null);
		db.show();
	}

	@NonNull
	private static GradientDrawable createSwatchDrawable(@ColorInt int fillColor, boolean selected) {
		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.RECTANGLE);
		gd.setCornerRadius(12f);
		gd.setColor(fillColor);
		int strokeColor = selected
				? (ColorUtils.calculateLuminance(fillColor) > 0.5 ? Color.BLACK : Color.WHITE)
				: 0x66888888;
		gd.setStroke(selected ? 6 : 2, strokeColor);
		return gd;
	}
}