package my.app.permata.ui.theme;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.VectorDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.BaseProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityPrefs;
import my.app.permata.ui.view.VideoView;
import my.app.utils.function.IntConsumer;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.view.DialogBuilder;

public final class CustomThemeManager {

	// Zone 1: Main Screen (Window)
	public static final int SLOT_WINDOW_BG = 0;
	public static final int SLOT_LIST_TEXT = 1;
	public static final int SLOT_LIST_SUBTITLE = 2;
	public static final int SLOT_LIST_ICONS = 3;

	// Zone 2: Top & Navigation Bars
	public static final int SLOT_BARS_BG = 4;
	public static final int SLOT_BARS_TEXT = 5;
	public static final int SLOT_BARS_ICONS = 6;

	// Zone 3: Playback Control Panel
	public static final int SLOT_CONTROL_BG = 7;
	public static final int SLOT_CONTROL_TEXT = 8;
	public static final int SLOT_CONTROL_ICONS = 9;

	// Zone 4: Menus & Dialogs
	public static final int SLOT_MENU_BG = 10;
	public static final int SLOT_MENU_TEXT = 11;
	public static final int SLOT_MENU_ICONS = 12;

	// Zone 5: Interactive Accent
	public static final int SLOT_ACCENT = 13;

	private static final int GRID_COLS = 10;
	private static final int GRID_ROWS = 10;

	private static final String THEME_META_PREFS = "permata_custom_theme_meta";
	private static final String KEY_CURATED_V1_SEEDED = "curated_presets_v1_seeded";
	private static volatile boolean curatedSeedChecked = false;

	private enum Zone {
		BARS,
		CONTROL_PANEL,
		CONTENT,
		MENU
	}

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

	// 10 globally renowned, automotive-tuned 14-slot theme presets
	private static final List<Palette> CURATED_PRESETS = List.of(
			// 1. Nord
			new Palette(
					"Nordic Aurora",
					0xFF242933, 0xFFECEFF4, 0xFF9BA8BF, 0xFF88C0D0,
					0xFF2E3440, 0xFFECEFF4, 0xFF88C0D0,
					0xFF3B4252, 0xFFECEFF4, 0xFF8FBCBB,
					0xFF333A48, 0xFFE5E9F0, 0xFF81A1C1,
					0xFF88C0D0
			),
			// 2. Tokyo Night
			new Palette(
					"Tokyo Midnight",
					0xFF16161E, 0xFFC0CAF5, 0xFF7882A8, 0xFF7AA2F7,
					0xFF1F2335, 0xFFE0E6FF, 0xFF7DCFFF,
					0xFF24283B, 0xFFE0E6FF, 0xFFBB9AF7,
					0xFF292E42, 0xFFC0CAF5, 0xFF7DCFFF,
					0xFF7AA2F7
			),
			// 3. Catppuccin Mocha
			new Palette(
					"Velvet Mocha",
					0xFF181825, 0xFFCDD6F4, 0xFF9399B2, 0xFFCBA6F7,
					0xFF1E1E2E, 0xFFCDD6F4, 0xFF89B4FA,
					0xFF262637, 0xFFF5E0DC, 0xFFF5C2E7,
					0xFF313244, 0xFFCDD6F4, 0xFF94E2D5,
					0xFFCBA6F7
			),
			// 4. Kanagawa
			new Palette(
					"Kanagawa Wave",
					0xFF16161D, 0xFFDCD7BA, 0xFF9A9682, 0xFF7E9CD8,
					0xFF1F1F28, 0xFFDCD7BA, 0xFFE6C384,
					0xFF252535, 0xFFDCD7BA, 0xFF7FB4CA,
					0xFF2A2A37, 0xFFDCD7BA, 0xFFE6C384,
					0xFFE6C384
			),
			// 5. Dracula
			new Palette(
					"Dracula Nocturne",
					0xFF1E1F29, 0xFFF8F8F2, 0xFF95A2C9, 0xFFBD93F9,
					0xFF282A36, 0xFFF8F8F2, 0xFF8BE9FD,
					0xFF343746, 0xFFF8F8F2, 0xFFFF79C6,
					0xFF383A4B, 0xFFF8F8F2, 0xFF50FA7B,
					0xFFBD93F9
			),
			// 6. Rosé Pine
			new Palette(
					"Rosé Pine Dusk",
					0xFF191724, 0xFFE0DEF4, 0xFF908CAA, 0xFFEBBCBA,
					0xFF1F1D2E, 0xFFE0DEF4, 0xFFF6C177,
					0xFF26233A, 0xFFE0DEF4, 0xFFC4A7E7,
					0xFF2A273F, 0xFFE0DEF4, 0xFF9CCFD8,
					0xFFEBBCBA
			),
			// 7. Gruvbox Dark
			new Palette(
					"Gruvbox Amber",
					0xFF1D2021, 0xFFEBDBB2, 0xFFA89984, 0xFFFABD2F,
					0xFF282828, 0xFFFBF1C7, 0xFFFE8019,
					0xFF32302F, 0xFFFBF1C7, 0xFFFABD2F,
					0xFF3C3836, 0xFFEBDBB2, 0xFF8EC07C,
					0xFFFE8019
			),
			// 8. Solarized Dark
			new Palette(
					"Solarized Abyss",
					0xFF00212B, 0xFFEEE8D5, 0xFF839496, 0xFF2AA198,
					0xFF002B36, 0xFFFDF6E3, 0xFF268BD2,
					0xFF073642, 0xFFFDF6E3, 0xFFB58900,
					0xFF06323D, 0xFFEEE8D5, 0xFF2AA198,
					0xFF2AA198
			),
			// 9. Monokai Pro
			new Palette(
					"Monokai Carbon",
					0xFF221F22, 0xFFFCFCFA, 0xFF939293, 0xFF78DCE8,
					0xFF2D2A2E, 0xFFFCFCFA, 0xFFFFD866,
					0xFF38353A, 0xFFFCFCFA, 0xFFFF6188,
					0xFF403E41, 0xFFFCFCFA, 0xFFA9DC76,
					0xFFFFD866
			),
			// 10. Everforest Dark
			new Palette(
					"Everforest Pine",
					0xFF232A2E, 0xFFD3C6AA, 0xFF9DA9A0, 0xFFA7C080,
					0xFF2D353B, 0xFFE5DCC5, 0xFF83C092,
					0xFF343F44, 0xFFE5DCC5, 0xFFDBBC7F,
					0xFF3D484D, 0xFFD3C6AA, 0xFFA7C080,
					0xFFA7C080
			)
	);

	// 10x10 (100) curated greatest, latest & iconic colors
	private static final int[] DEFAULT_PALETTE_COLORS = new int[]{
			// Row 1: OLED, Dark UI & Modern Neutrals
			0xFF000000, 0xFF0B1215, 0xFF121212, 0xFF1C1C1E, 0xFF1E1E2E,
			0xFF282A36, 0xFF2E3440, 0xFF37474F, 0xFF616161, 0xFF878681,

			// Row 2: Whites, Pantone 2026 Cloud Dancer & Precious Metals (Silver, Gold, Bronze, Rose Gold)
			0xFFFFFFFF, 0xFFF5F5F5, 0xFFF0EEE9, 0xFFF7E7CE, 0xFFC0C0C0,
			0xFFA8A9AD, 0xFFFFD700, 0xFFD4AF37, 0xFFCD7F32, 0xFFB76E79,

			// Row 3: Earth, Luxury Leather & Pantone 2025 Mocha Mousse
			0xFF2C1A12, 0xFF4E342E, 0xFF6D4C41, 0xFF8C5A2B, 0xFFB87333,
			0xFFA47864, 0xFFC19A6B, 0xFFD2B48C, 0xFFE4C7B8, 0xFFFFBE98,

			// Row 4: Rubies, Ferrari Red & Crimsons
			0xFF4A0404, 0xFF7F0000, 0xFF9B111E, 0xFFB71C1C, 0xFFD40000,
			0xFFE53935, 0xFFE0115F, 0xFFFF1744, 0xFFFF5252, 0xFFFF8A80,

			// Row 5: Oranges, Amber, Star Wars & Ferrari Yellow
			0xFFBF360C, 0xFFE65100, 0xFFF4511E, 0xFFFB8C00, 0xFFFF9800,
			0xFFFFB300, 0xFFFFC107, 0xFFFFD600, 0xFFFFF200, 0xFFFFF59D,

			// Row 6: British Racing Green, Emeralds, Jade & Neon Mint
			0xFF004225, 0xFF046307, 0xFF1B5E20, 0xFF2E7D32, 0xFF00A86B,
			0xFF43A047, 0xFF50C878, 0xFF7CB342, 0xFF00E676, 0xFF76FF03,

			// Row 7: Teals, Tiffany Blue & Electric Cyans
			0xFF003333, 0xFF004D40, 0xFF00695C, 0xFF008080, 0xFF00A896,
			0xFF0ABAB5, 0xFF00BCD4, 0xFF40E0D0, 0xFF00E5FF, 0xFF84FFFF,

			// Row 8: Navy, Sapphires & Iconic Automotive Blues
			0xFF0A192F, 0xFF0F1E3D, 0xFF000080, 0xFF1A237E, 0xFF0F52BA,
			0xFF1565C0, 0xFF1E88E5, 0xFF009FDA, 0xFF03A9F4, 0xFF81D4FA,

			// Row 9: Midnight Purple, Amethyst & Cyberpunk Violets
			0xFF1A0033, 0xFF311B92, 0xFF4A148C, 0xFF5E35B1, 0xFF6A1B9A,
			0xFF8E24AA, 0xFF9966CC, 0xFFBB86FC, 0xFFB388FF, 0xFFE1BEE7,

			// Row 10: Magentas, Cyberpunk Rose & Sakura Pinks
			0xFF4A0E2E, 0xFF880E4F, 0xFFAD1457, 0xFFC2185B, 0xFFD81B60,
			0xFFFF007F, 0xFFFF4081, 0xFFEC407A, 0xFFF48FB1, 0xFFFCE4EC
	};

	private static final Set<View> HOOKED_ROOTS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<RecyclerView> HOOKED_RECYCLERS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<View> HOOKED_MENUS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<View, Integer> STYLED_SURFACE_HASH = new WeakHashMap<>();

	private static Integer sessionRandomThemeMain;
	private static Palette sessionRandomCustomMain;
	private static Integer sessionRandomThemeAa;
	private static Palette sessionRandomCustomAa;

	private CustomThemeManager() {
	}

	public static final class Palette {
		public String name;
		// Zone 1: Window
		@ColorInt public int windowBg;
		@ColorInt public int listText;
		@ColorInt public int listSubtitle;
		@ColorInt public int listIcons;
		// Zone 2: Top & Nav Bars
		@ColorInt public int primaryBars;
		@ColorInt public int barsText;
		@ColorInt public int barsIcons;
		// Zone 3: Playback Control Panel
		@ColorInt public int controlPanelBg;
		@ColorInt public int controlPanelText;
		@ColorInt public int controlPanelIcons;
		// Zone 4: Menus & Dialogs
		@ColorInt public int surfaceMenu;
		@ColorInt public int menuText;
		@ColorInt public int menuIcons;
		// Zone 5: Interactive Accent
		@ColorInt public int accent;

		public Palette(String name,
					   @ColorInt int windowBg,
					   @ColorInt int listText,
					   @ColorInt int listSubtitle,
					   @ColorInt int listIcons,
					   @ColorInt int primaryBars,
					   @ColorInt int barsText,
					   @ColorInt int barsIcons,
					   @ColorInt int controlPanelBg,
					   @ColorInt int controlPanelText,
					   @ColorInt int controlPanelIcons,
					   @ColorInt int surfaceMenu,
					   @ColorInt int menuText,
					   @ColorInt int menuIcons,
					   @ColorInt int accent) {
			this.name = (name == null || name.trim().isEmpty()) ? "Custom" : name.trim();
			this.windowBg = windowBg;
			this.listText = listText;
			this.listSubtitle = listSubtitle;
			this.listIcons = listIcons;
			this.primaryBars = primaryBars;
			this.barsText = barsText;
			this.barsIcons = barsIcons;
			this.controlPanelBg = controlPanelBg;
			this.controlPanelText = controlPanelText;
			this.controlPanelIcons = controlPanelIcons;
			this.surfaceMenu = surfaceMenu;
			this.menuText = menuText;
			this.menuIcons = menuIcons;
			this.accent = accent;
		}

		public static Palette createFallback() {
			return new Palette(
					"Custom",
					0xFF121212, 0xFFE0E0E0, 0xFFA0A0A0, 0xFFE0E0E0,
					0xFF1F1B24, 0xFFFFFFFF, 0xFFFFFFFF,
					0xFF1F1B24, 0xFFFFFFFF, 0xFFFFFFFF,
					0xFF25202B, 0xFFE0E0E0, 0xFFE0E0E0,
					0xFFBB86FC
			);
		}

		public Palette copy() {
			return new Palette(
					name,
					windowBg, listText, listSubtitle, listIcons,
					primaryBars, barsText, barsIcons,
					controlPanelBg, controlPanelText, controlPanelIcons,
					surfaceMenu, menuText, menuIcons,
					accent
			);
		}

		public boolean isLightBackground() {
			return ColorUtils.calculateLuminance(windowBg) > 0.5;
		}

		@NonNull
		public int[] getPreviewStripColors() {
			return new int[]{windowBg, primaryBars, controlPanelBg, surfaceMenu, accent};
		}

		@ColorInt
		public int getSlot(int slot) {
			return switch (slot) {
				case SLOT_WINDOW_BG -> windowBg;
				case SLOT_LIST_TEXT -> listText;
				case SLOT_LIST_SUBTITLE -> listSubtitle;
				case SLOT_LIST_ICONS -> listIcons;
				case SLOT_BARS_BG -> primaryBars;
				case SLOT_BARS_TEXT -> barsText;
				case SLOT_BARS_ICONS -> barsIcons;
				case SLOT_CONTROL_BG -> controlPanelBg;
				case SLOT_CONTROL_TEXT -> controlPanelText;
				case SLOT_CONTROL_ICONS -> controlPanelIcons;
				case SLOT_MENU_BG -> surfaceMenu;
				case SLOT_MENU_TEXT -> menuText;
				case SLOT_MENU_ICONS -> menuIcons;
				case SLOT_ACCENT -> accent;
				default -> primaryBars;
			};
		}

		public void setSlot(int slot, @ColorInt int color) {
			switch (slot) {
				case SLOT_WINDOW_BG -> windowBg = color;
				case SLOT_LIST_TEXT -> listText = color;
				case SLOT_LIST_SUBTITLE -> listSubtitle = color;
				case SLOT_LIST_ICONS -> listIcons = color;
				case SLOT_BARS_BG -> primaryBars = color;
				case SLOT_BARS_TEXT -> barsText = color;
				case SLOT_BARS_ICONS -> barsIcons = color;
				case SLOT_CONTROL_BG -> controlPanelBg = color;
				case SLOT_CONTROL_TEXT -> controlPanelText = color;
				case SLOT_CONTROL_ICONS -> controlPanelIcons = color;
				case SLOT_MENU_BG -> surfaceMenu = color;
				case SLOT_MENU_TEXT -> menuText = color;
				case SLOT_MENU_ICONS -> menuIcons = color;
				case SLOT_ACCENT -> accent = color;
			}
		}

		public JSONObject toJson() {
			JSONObject o = new JSONObject();
			try {
				o.put("name", name);
				o.put("windowBg", windowBg);
				o.put("listText", listText);
				o.put("listSubtitle", listSubtitle);
				o.put("listIcons", listIcons);
				o.put("primaryBars", primaryBars);
				o.put("barsText", barsText);
				o.put("barsIcons", barsIcons);
				o.put("controlPanelBg", controlPanelBg);
				o.put("controlPanelText", controlPanelText);
				o.put("controlPanelIcons", controlPanelIcons);
				o.put("surfaceMenu", surfaceMenu);
				o.put("menuText", menuText);
				o.put("menuIcons", menuIcons);
				o.put("accent", accent);
			} catch (Exception ex) {
				Log.e(ex, "Failed to serialize custom theme palette");
			}
			return o;
		}

		@NonNull
		public static Palette fromJson(@Nullable JSONObject o) {
			Palette def = createFallback();
			if (o == null) return def;

			int winBg = o.optInt("windowBg", def.windowBg);
			int barsBg = o.optInt("primaryBars", def.primaryBars);
			int menuBg = o.optInt("surfaceMenu", def.surfaceMenu);
			int acc = o.optInt("accent", def.accent);

			// Seamless auto-migration from legacy 7-slot keys
			int bText = o.has("barsText") ? o.optInt("barsText", def.barsText) : o.optInt("textPrimary", def.barsText);
			int bIcons = o.has("barsIcons") ? o.optInt("barsIcons", def.barsIcons) : o.optInt("iconTint", def.barsIcons);
			int lText = o.has("listText") ? o.optInt("listText", def.listText) : o.optInt("textSecondary", def.listText);
			int lSub = o.has("listSubtitle")
					? o.optInt("listSubtitle", def.listSubtitle)
					: ColorUtils.blendARGB(winBg, lText, 0.70f);
			int lIcons = o.has("listIcons") ? o.optInt("listIcons", def.listIcons) : o.optInt("iconTint", bIcons);

			int ctrlBg = o.optInt("controlPanelBg", barsBg);
			int ctrlText = o.optInt("controlPanelText", bText);
			int ctrlIcons = o.optInt("controlPanelIcons", bIcons);

			int mText = o.optInt("menuText", lText);
			int mIcons = o.optInt("menuIcons", lIcons);

			return new Palette(
					o.optString("name", def.name),
					winBg, lText, lSub, lIcons,
					barsBg, bText, bIcons,
					ctrlBg, ctrlText, ctrlIcons,
					menuBg, mText, mIcons,
					acc
			);
		}
	}

	// =========================================================================
	// Customizable 100-Swatch Storage & Reset
	// =========================================================================

	@NonNull
	public static int[] getCustomSwatches() {
		int[] defaults = Arrays.copyOf(DEFAULT_PALETTE_COLORS, DEFAULT_PALETTE_COLORS.length);
		try {
			String raw = MainActivityPrefs.get().getStringPref(MainActivityPrefs.CUSTOM_COLOR_SWATCHES);
			if (raw == null || raw.isEmpty()) return defaults;
			JSONArray arr = new JSONArray(raw);
			if (arr.length() != defaults.length) {
				return defaults;
			}
			for (int i = 0; i < defaults.length; i++) {
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
			int barsText = resolveAttrColor(themed, android.R.attr.textColorPrimary, Color.WHITE);
			int barsIcons = resolveAttrColor(themed, com.google.android.material.R.attr.colorOnPrimary, barsText);
			int listText = resolveAttrColor(themed, android.R.attr.textColorSecondary, 0xFFE0E0E0);
			int listSubtitle = ColorUtils.blendARGB(windowBg, listText, 0.70f);
			int onSurface = resolveAttrColor(themed, com.google.android.material.R.attr.colorOnSurface, listText);

			return new Palette(
					label,
					windowBg, listText, listSubtitle, onSurface,
					primaryBars, barsText, barsIcons,
					primaryBars, barsText, barsIcons,
					surfaceMenu, onSurface, onSurface,
					accent
			);
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
	// Storage, Curated Presets & User Presets
	// =========================================================================

	@NonNull
	public static List<Palette> getCuratedPresets() {
		List<Palette> copies = new ArrayList<>(CURATED_PRESETS.size());
		for (Palette p : CURATED_PRESETS) {
			copies.add(p.copy());
		}
		return copies;
	}

	public static boolean isCuratedPresetName(@Nullable String name) {
		if (name == null) return false;
		for (Palette p : CURATED_PRESETS) {
			if (p.name.equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	private static synchronized void ensureCuratedPresetsSeeded() {
		if (curatedSeedChecked) return;
		Context appCtx = PermataApplication.get();
		if (appCtx == null) return;

		try {
			SharedPreferences meta = appCtx.getSharedPreferences(THEME_META_PREFS, Context.MODE_PRIVATE);
			if (!meta.getBoolean(KEY_CURATED_V1_SEEDED, false)) {
				List<Palette> merged = new ArrayList<>(readRawSavedPresets());
				boolean changed = false;
				for (Palette curated : CURATED_PRESETS) {
					boolean exists = false;
					for (Palette existing : merged) {
						if (existing.name.equalsIgnoreCase(curated.name)) {
							exists = true;
							break;
						}
					}
					if (!exists) {
						merged.add(curated.copy());
						changed = true;
					}
				}
				if (changed) {
					persistPresetList(merged);
				}
				meta.edit().putBoolean(KEY_CURATED_V1_SEEDED, true).apply();
			}
			curatedSeedChecked = true;
		} catch (Exception ex) {
			Log.w(ex, "Failed seeding curated custom theme presets");
		}
	}

	public static synchronized void restoreCuratedPresets() {
		List<Palette> existing = new ArrayList<>(readRawSavedPresets());
		for (Palette curated : CURATED_PRESETS) {
			boolean replaced = false;
			for (int i = 0; i < existing.size(); i++) {
				if (existing.get(i).name.equalsIgnoreCase(curated.name)) {
					existing.set(i, curated.copy());
					replaced = true;
					break;
				}
			}
			if (!replaced) {
				existing.add(curated.copy());
			}
		}
		persistPresetList(existing);

		Context appCtx = PermataApplication.get();
		if (appCtx != null) {
			appCtx.getSharedPreferences(THEME_META_PREFS, Context.MODE_PRIVATE)
					.edit()
					.putBoolean(KEY_CURATED_V1_SEEDED, true)
					.apply();
		}
		curatedSeedChecked = true;
	}

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
		ensureCuratedPresetsSeeded();
		return readRawSavedPresets();
	}

	@NonNull
	private static List<Palette> readRawSavedPresets() {
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
	// Runtime View, Window, Overlay, NavBar Extension & RecyclerView Zone Styler
	// =========================================================================

	public static void applyIfCustom(@NonNull MainActivityDelegate delegate) {
		boolean auto = delegate.isCarActivity();
		Palette p = resolveEffectiveCustomPalette(auto);
		if (p == null) return;

		applyPalettePass(delegate, p, auto);

		View root = delegate.findViewById(R.id.main_activity);
		if (root != null && HOOKED_ROOTS.add(root)) {
			final boolean[] pending = new boolean[]{false};
			root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
				if (pending[0]) return;
				Palette active = resolveEffectiveCustomPalette(delegate.isCarActivity());
				if (active == null) return;
				pending[0] = true;
				root.post(() -> {
					pending[0] = false;
					Palette latest = resolveEffectiveCustomPalette(delegate.isCarActivity());
					if (latest != null) {
						applyDynamicLayers(delegate, latest);
					}
				});
			});
		}
	}

	private static void applyPalettePass(@NonNull MainActivityDelegate delegate,
										 @NonNull Palette p,
										 boolean auto) {
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
			if (body != null) body.setBackgroundColor(p.windowBg);

			View frame = delegate.findViewById(R.id.frame_layout);
			if (frame != null) frame.setBackgroundColor(p.windowBg);

			View loadingBar = delegate.findViewById(R.id.content_loading_progress);
			if (loadingBar != null) {
				tintZoneRecursive(delegate, loadingBar, p, Zone.CONTENT);
			}

			View fb = delegate.getFloatingButton();
			if (fb != null) {
				fb.setBackgroundTintList(ColorStateList.valueOf(p.primaryBars));
				if (fb instanceof FloatingActionButton fab) {
					fab.setSupportImageTintList(ColorStateList.valueOf(p.barsIcons));
					fab.setRippleColor(ColorStateList.valueOf(p.accent));
				} else if (fb instanceof ImageView iv) {
					ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(p.barsIcons));
				}
			}

			applyDynamicLayers(delegate, p);
		} catch (Exception ex) {
			Log.w(ex, "CustomThemeManager: Failed applying custom palette to views");
		}
	}

	private static void applyDynamicLayers(@NonNull MainActivityDelegate delegate, @NonNull Palette p) {
		try {
			styleBarsZone(delegate, delegate.getToolBar(), p);
			styleBarsZone(delegate, delegate.getNavBar(), p);
			styleControlPanelZone(delegate, delegate.getControlPanel(), p);

			View frame = delegate.findViewById(R.id.frame_layout);
			if (frame != null) {
				frame.setBackgroundColor(p.windowBg);
				styleContentZone(delegate, frame, p);
			}

			// Discover & style ALL OverlayMenu instances (context_menu, tool_menu, nav_menu, addon menus)
			View scanRoot = null;
			Window w = delegate.getWindow();
			if (w != null) scanRoot = w.getDecorView();
			if (scanRoot == null) scanRoot = delegate.findViewById(R.id.main_activity);
			if (scanRoot != null) {
				discoverAndStyleOverlayMenus(delegate, scanRoot, p);
			}
		} catch (Exception ex) {
			Log.w(ex, "CustomThemeManager: Failed applying dynamic layers");
		}
	}

	private static void discoverAndStyleOverlayMenus(@NonNull MainActivityDelegate delegate,
													 @NonNull View v,
													 @NonNull Palette p) {
		if (isProtectedView(v)) return;
		if (v instanceof OverlayMenu) {
			hookOverlayMenu(delegate, v);
			if (v.getVisibility() == View.VISIBLE) {
				styleMenuZone(delegate, v, p);
			}
			return;
		}
		if (v instanceof ViewGroup vg && !(v instanceof RecyclerView)) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				discoverAndStyleOverlayMenus(delegate, vg.getChildAt(i), p);
			}
		}
	}

	private static void hookOverlayMenu(@NonNull MainActivityDelegate delegate, @NonNull View menuView) {
		if (!HOOKED_MENUS.add(menuView)) return;
		menuView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			if (v.getVisibility() != View.VISIBLE) return;
			Palette active = resolveEffectiveCustomPalette(delegate.isCarActivity());
			if (active != null) {
				styleMenuZone(delegate, v, active);
				v.post(() -> {
					Palette latest = resolveEffectiveCustomPalette(delegate.isCarActivity());
					if (latest != null && v.getVisibility() == View.VISIBLE) {
						styleMenuZone(delegate, v, latest);
					}
				});
			}
		});
	}

	private static void styleBarsZone(@NonNull MainActivityDelegate delegate,
									  @Nullable View bar,
									  @NonNull Palette p) {
		if (bar == null) return;
		bar.setBackgroundColor(p.primaryBars);
		styleBarChildSurfaces(delegate, bar, p, p.primaryBars, p.barsText);
		tintZoneRecursive(delegate, bar, p, Zone.BARS);
	}

	private static void styleControlPanelZone(@NonNull MainActivityDelegate delegate,
											  @Nullable View panel,
											  @NonNull Palette p) {
		if (panel == null) return;
		panel.setBackgroundColor(p.controlPanelBg);
		styleBarChildSurfaces(delegate, panel, p, p.controlPanelBg, p.controlPanelText);
		tintZoneRecursive(delegate, panel, p, Zone.CONTROL_PANEL);
	}

	private static void styleBarChildSurfaces(@NonNull MainActivityDelegate delegate,
											  @NonNull View root,
											  @NonNull Palette p,
											  @ColorInt int barBg,
											  @ColorInt int barText) {
		if (!(root instanceof ViewGroup vg)) return;
		for (int i = 0; i < vg.getChildCount(); i++) {
			styleBarSurfaceRecursive(delegate, vg.getChildAt(i), p, barBg, barText);
		}
	}

	private static void styleBarSurfaceRecursive(@NonNull MainActivityDelegate delegate,
												 @NonNull View v,
												 @NonNull Palette p,
												 @ColorInt int barBg,
												 @ColorInt int barText) {
		if (isProtectedView(v)) return;
		if (v instanceof OverlayMenu) {
			hookOverlayMenu(delegate, v);
			if (v.getVisibility() == View.VISIBLE) {
				styleMenuZone(delegate, v, p);
			}
			return;
		}
		if (v instanceof CardView cv) {
			cv.setCardBackgroundColor(barBg);
		} else if (v instanceof ViewGroup vg) {
			boolean isExtPanel = isNavExtensionContainer(vg);
			Drawable bg = vg.getBackground();
			if (isExtPanel) {
				int border = ColorUtils.setAlphaComponent(barText, 75);
				applyCachedSurfaceDrawable(vg, Objects.hash(101, barBg, border),
						() -> makeRoundedBox(barBg, UiUtils.toIntPx(vg.getContext(), 10), border,
								Math.max(1, UiUtils.toIntPx(vg.getContext(), 1))));
			} else if (bg instanceof ColorDrawable cd && Color.alpha(cd.getColor()) >= 200) {
				vg.setBackgroundColor(barBg);
			} else if (bg != null
					&& !(bg instanceof RippleDrawable)
					&& !(bg instanceof StateListDrawable)
					&& !vg.isSelected() && !vg.isActivated()
					&& vg.getChildCount() >= 2) {
				// Covers extension/panel containers with custom shape/layer XML backgrounds
				int border = ColorUtils.setAlphaComponent(barText, 65);
				applyCachedSurfaceDrawable(vg, Objects.hash(102, barBg, border),
						() -> makeRoundedBox(barBg, UiUtils.toIntPx(vg.getContext(), 8), border,
								Math.max(1, UiUtils.toIntPx(vg.getContext(), 1))));
			}
		}

		if (v instanceof ViewGroup vg && !(v instanceof RecyclerView)) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				styleBarSurfaceRecursive(delegate, vg.getChildAt(i), p, barBg, barText);
			}
		}
	}

	private static boolean isNavExtensionContainer(@NonNull ViewGroup vg) {
		if (vg instanceof RecyclerView) return true;
		int id = vg.getId();
		if (id != View.NO_ID) {
			try {
				String name = vg.getResources().getResourceEntryName(id).toLowerCase(Locale.US);
				if (name.contains("ext") || name.contains("overflow") || name.contains("popup")
						|| name.contains("menu") || name.contains("more")) {
					return true;
				}
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	private static void styleMenuZone(@NonNull MainActivityDelegate delegate,
									  @NonNull View menuView,
									  @NonNull Palette p) {
		styleMenuSurfacesRecursive(menuView, p, true);
		tintZoneRecursive(delegate, menuView, p, Zone.MENU);
	}

	private static void styleMenuSurfacesRecursive(@NonNull View v,
												   @NonNull Palette p,
												   boolean isMenuRoot) {
		if (isProtectedView(v)) return;

		if (v instanceof CardView cv) {
			cv.setCardBackgroundColor(p.surfaceMenu);
		} else if (v instanceof ViewGroup vg) {
			Drawable bg = vg.getBackground();
			if (bg instanceof ColorDrawable cd) {
				// Preserve transparent/translucent dim scrims; replace opaque menu panel fills
				if (Color.alpha(cd.getColor()) >= 220) {
					vg.setBackgroundColor(p.surfaceMenu);
				}
			} else if (bg != null
					&& !(bg instanceof RippleDrawable)
					&& !(bg instanceof StateListDrawable)) {
				// Style rounded OverlayMenu card containers (including RecyclerViews inside OverlayMenu)
				boolean isFullScreenScrim = isMenuRoot && vg.getWidth() > 0 && vg.getParent() instanceof View pv
						&& vg.getWidth() >= pv.getWidth() - 4 && vg.getHeight() >= pv.getHeight() - 4;
				if (!isFullScreenScrim) {
					int border = ColorUtils.setAlphaComponent(p.menuText, 75);
					applyCachedSurfaceDrawable(vg, Objects.hash(201, p.surfaceMenu, border),
							() -> makeRoundedBox(p.surfaceMenu, UiUtils.toIntPx(vg.getContext(), 12), border,
									Math.max(1, UiUtils.toIntPx(vg.getContext(), 1))));
				}
			}
		}

		if (v instanceof ViewGroup vg && !(v instanceof RecyclerView)) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				styleMenuSurfacesRecursive(vg.getChildAt(i), p, false);
			}
		}
	}

	private static void styleContentZone(@NonNull MainActivityDelegate delegate,
										 @NonNull View root,
										 @NonNull Palette p) {
		styleContentSurfacesRecursive(root, p);
		tintZoneRecursive(delegate, root, p, Zone.CONTENT);
	}

	private static void styleContentSurfacesRecursive(@NonNull View v, @NonNull Palette p) {
		if (isProtectedView(v) || v instanceof OverlayMenu) return;

		if (v instanceof CardView cv) {
			cv.setCardBackgroundColor(p.surfaceMenu);
		} else if (v instanceof RecyclerView rv) {
			// Ensure Folders, IPTV (TV), Favorites, Playlists & Settings list backdrop uses windowBg
			rv.setBackgroundColor(p.windowBg);
			for (int i = 0; i < rv.getChildCount(); i++) {
				styleContentListRow(rv.getChildAt(i), p);
			}
			return;
		} else if (v instanceof ViewGroup vg) {
			Drawable bg = vg.getBackground();
			if (bg instanceof ColorDrawable cd && Color.alpha(cd.getColor()) >= 200) {
				vg.setBackgroundColor(p.windowBg);
			}
		}

		if (v instanceof ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				styleContentSurfacesRecursive(vg.getChildAt(i), p);
			}
		}
	}

	private static void styleContentListRow(@NonNull View row, @NonNull Palette p) {
		if (isProtectedView(row)) return;
		if (row instanceof CardView cv) {
			cv.setCardBackgroundColor(ColorUtils.blendARGB(p.windowBg, p.listText, 0.06f));
			return;
		}
		if (row.getBackground() == null) return;

		int hash = Objects.hash(301, p.windowBg, p.listText, p.accent);
		applyCachedSurfaceDrawable(row, hash, () -> createListRowBackground(row.getContext(), p));
	}

	@NonNull
	private static Drawable createListRowBackground(@NonNull Context ctx, @NonNull Palette p) {
		float radius = UiUtils.toIntPx(ctx, 10);
		int strokeW = Math.max(1, UiUtils.toIntPx(ctx, 1));

		int normalFill = ColorUtils.blendARGB(p.windowBg, p.listText, 0.06f);
		int normalBorder = ColorUtils.setAlphaComponent(p.listText, 55);

		int activeFill = ColorUtils.blendARGB(p.windowBg, p.accent, 0.30f);
		int activeBorder = ColorUtils.setAlphaComponent(p.accent, 210);

		int pressedFill = ColorUtils.blendARGB(p.windowBg, p.accent, 0.20f);
		int pressedBorder = ColorUtils.setAlphaComponent(p.accent, 180);

		StateListDrawable sld = new StateListDrawable();
		sld.addState(new int[]{android.R.attr.state_pressed},
				makeRoundedBox(pressedFill, radius, pressedBorder, strokeW));
		sld.addState(new int[]{android.R.attr.state_focused},
				makeRoundedBox(pressedFill, radius, p.accent, Math.max(2, strokeW * 2)));
		sld.addState(new int[]{android.R.attr.state_activated},
				makeRoundedBox(activeFill, radius, activeBorder, strokeW));
		sld.addState(new int[]{android.R.attr.state_selected},
				makeRoundedBox(activeFill, radius, activeBorder, strokeW));
		sld.addState(new int[]{android.R.attr.state_checked},
				makeRoundedBox(activeFill, radius, activeBorder, strokeW));
		sld.addState(new int[]{},
				makeRoundedBox(normalFill, radius, normalBorder, strokeW));
		return sld;
	}

	private static void applyCachedSurfaceDrawable(@NonNull View v,
												   int hash,
												   @NonNull java.util.function.Supplier<Drawable> factory) {
		Integer prev = STYLED_SURFACE_HASH.get(v);
		if (prev != null && prev == hash && v.getBackground() != null) return;

		int pl = v.getPaddingLeft();
		int pt = v.getPaddingTop();
		int pr = v.getPaddingRight();
		int pb = v.getPaddingBottom();
		v.setBackground(factory.get());
		v.setPadding(pl, pt, pr, pb);
		STYLED_SURFACE_HASH.put(v, hash);
	}

	@NonNull
	private static GradientDrawable makeRoundedBox(@ColorInt int fill,
												   float radius,
												   @ColorInt int strokeColor,
												   int strokeWidth) {
		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.RECTANGLE);
		gd.setCornerRadius(radius);
		gd.setColor(fill);
		if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
			gd.setStroke(strokeWidth, strokeColor);
		}
		return gd;
	}

	private static void hookRecyclerView(@NonNull MainActivityDelegate delegate,
										 @NonNull RecyclerView rv,
										 @NonNull Zone zone) {
		if (!HOOKED_RECYCLERS.add(rv)) return;

		rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
			@Override
			public void onChildViewAttachedToWindow(@NonNull View child) {
				Runnable applyRow = () -> {
					Palette active = resolveEffectiveCustomPalette(delegate.isCarActivity());
					if (active == null) return;
					if (zone == Zone.CONTENT) {
						styleContentListRow(child, active);
					}
					tintZoneRecursive(delegate, child, active, zone);
				};
				applyRow.run();
				// Second pass catches post-attach onBindViewHolder tints (e.g. watched folder icons)
				child.post(applyRow);
			}

			@Override
			public void onChildViewDetachedFromWindow(@NonNull View child) {
			}
		});

		rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				if (dx == 0 && dy == 0) return;
				Palette active = resolveEffectiveCustomPalette(delegate.isCarActivity());
				if (active == null) return;
				for (int i = 0; i < recyclerView.getChildCount(); i++) {
					View child = recyclerView.getChildAt(i);
					if (zone == Zone.CONTENT) {
						styleContentListRow(child, active);
					}
					tintZoneRecursive(delegate, child, active, zone);
				}
			}
		});
	}

	private static void tintZoneRecursive(@NonNull MainActivityDelegate delegate,
										  @NonNull View v,
										  @NonNull Palette p,
										  @NonNull Zone zone) {
		if (isProtectedView(v)) return;
		if (v instanceof OverlayMenu && zone != Zone.MENU) return;

		int textColor;
		int subtitleColor;
		int iconTint;
		boolean isContentArea;
		boolean isMenuOverlay;

		switch (zone) {
			case BARS -> {
				textColor = p.barsText;
				subtitleColor = ColorUtils.blendARGB(p.primaryBars, p.barsText, 0.72f);
				iconTint = p.barsIcons;
				isContentArea = false;
				isMenuOverlay = false;
			}
			case CONTROL_PANEL -> {
				textColor = p.controlPanelText;
				subtitleColor = ColorUtils.blendARGB(p.controlPanelBg, p.controlPanelText, 0.72f);
				iconTint = p.controlPanelIcons;
				isContentArea = false;
				isMenuOverlay = false;
			}
			case MENU -> {
				textColor = p.menuText;
				subtitleColor = ColorUtils.blendARGB(p.surfaceMenu, p.menuText, 0.72f);
				iconTint = p.menuIcons;
				isContentArea = true;
				isMenuOverlay = true;
			}
			default -> {
				textColor = p.listText;
				subtitleColor = p.listSubtitle;
				iconTint = p.listIcons;
				isContentArea = true;
				isMenuOverlay = false;
			}
		}

		int accent = p.accent;

		if (v instanceof SeekBar sb) {
			sb.setThumbTintList(ColorStateList.valueOf(accent));
			sb.setProgressTintList(ColorStateList.valueOf(accent));
			sb.setSecondaryProgressTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 120)));
		} else if (v instanceof BaseProgressIndicator<?> bpi) {
			bpi.setIndicatorColor(accent);
		} else if (v instanceof ProgressBar pb) {
			pb.setIndeterminateTintList(ColorStateList.valueOf(accent));
			pb.setProgressTintList(ColorStateList.valueOf(accent));
		} else if (v instanceof SwitchCompat sw) {
			ColorStateList thumbCsl = new ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{accent, iconTint});
			ColorStateList trackCsl = new ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{ColorUtils.setAlphaComponent(accent, 130), ColorUtils.setAlphaComponent(textColor, 80)});
			sw.setThumbTintList(thumbCsl);
			sw.setTrackTintList(trackCsl);
			sw.setTextColor(textColor);
		} else if (v instanceof Switch sw) {
			ColorStateList thumbCsl = new ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{accent, iconTint});
			ColorStateList trackCsl = new ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{ColorUtils.setAlphaComponent(accent, 130), ColorUtils.setAlphaComponent(textColor, 80)});
			sw.setThumbTintList(thumbCsl);
			sw.setTrackTintList(trackCsl);
			sw.setTextColor(textColor);
		} else if (v instanceof CompoundButton cb) {
			ColorStateList btnCsl = new ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{accent, iconTint});
			CompoundButtonCompat.setButtonTintList(cb, btnCsl);
			cb.setTextColor(textColor);
		} else if (v instanceof EditText et) {
			et.setTextColor(textColor);
			et.setHintTextColor(subtitleColor);
			ViewCompat.setBackgroundTintList(et, ColorStateList.valueOf(accent));
		} else if (v instanceof Button btn) {
			btn.setTextColor(isMenuOverlay ? accent : textColor);
			tintCompoundDrawables(btn, iconTint);
		} else if (v instanceof TextView tv) {
			tv.setTextColor(isSubtitleTextView(tv) ? subtitleColor : textColor);
			tintCompoundDrawables(tv, iconTint);
		} else if (v instanceof ImageView iv) {
			if (shouldTintImageView(iv, zone, isContentArea)) {
				iv.clearColorFilter();
				ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(iconTint));
			}
		} else if (v.getClass().getName().contains("MediaRouteButton")) {
			// Safely tint Cast button without compile-time dependency on cast module
			Paint tintPaint = new Paint();
			tintPaint.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN));
			v.setLayerType(View.LAYER_TYPE_HARDWARE, tintPaint);
		}

		if (v instanceof RecyclerView rv) {
			hookRecyclerView(delegate, rv, zone);
			for (int i = 0; i < rv.getChildCount(); i++) {
				View child = rv.getChildAt(i);
				if (zone == Zone.CONTENT) {
					styleContentListRow(child, p);
				}
				tintZoneRecursive(delegate, child, p, zone);
			}
			return;
		}

		if (v instanceof ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				tintZoneRecursive(delegate, vg.getChildAt(i), p, zone);
			}
		}
	}

	private static boolean isSubtitleTextView(@NonNull TextView tv) {
		int id = tv.getId();
		if (id == android.R.id.summary || id == android.R.id.text2) {
			return true;
		}
		if (id != View.NO_ID) {
			try {
				String entry = tv.getResources().getResourceEntryName(id).toLowerCase(Locale.US);
				if (entry.contains("sub") || entry.contains("summary") || entry.contains("desc")
						|| entry.contains("detail") || entry.contains("secondary") || entry.contains("info")
						|| entry.contains("duration") || entry.contains("artist") || entry.contains("album")
						|| entry.contains("hint")) {
					return true;
				}
			} catch (Exception ignored) {
			}
		}
		if (tv.getParent() instanceof ViewGroup parent) {
			float mySize = tv.getTextSize();
			for (int i = 0; i < parent.getChildCount(); i++) {
				View sibling = parent.getChildAt(i);
				if (sibling != tv && sibling instanceof TextView stv && !(sibling instanceof Button)) {
					if (stv.getVisibility() == View.VISIBLE && stv.getTextSize() > mySize + 1.5f) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean isProtectedView(@NonNull View v) {
		return "keep_own_color".equals(v.getTag())
				|| v instanceof VideoView
				|| v instanceof SurfaceView
				|| v instanceof TextureView
				|| v instanceof WebView;
	}

	private static boolean shouldTintImageView(@NonNull ImageView iv,
											   @NonNull Zone zone,
											   boolean isContentArea) {
		Drawable d = iv.getDrawable();
		if (d == null) return false;
		// Top & Navigation bars only contain monochrome UI icons (including '>' overflow chevron)
		if (zone == Zone.BARS) {
			return true;
		}
		// Never solid-tint album art, channel logos, or video thumbnails
		if (d instanceof BitmapDrawable || d instanceof TransitionDrawable) {
			return false;
		}
		if (iv.getImageTintList() != null) {
			return true;
		}
		if (d instanceof VectorDrawable || d instanceof VectorDrawableCompat) {
			return true;
		}
		return !isContentArea;
	}

	private static void tintCompoundDrawables(@NonNull TextView tv, @ColorInt int iconTint) {
		boolean hasTintable = false;
		for (Drawable d : tv.getCompoundDrawablesRelative()) {
			if (d != null && !(d instanceof BitmapDrawable) && !(d instanceof TransitionDrawable)) {
				hasTintable = true;
				break;
			}
		}
		if (!hasTintable) {
			for (Drawable d : tv.getCompoundDrawables()) {
				if (d != null && !(d instanceof BitmapDrawable) && !(d instanceof TransitionDrawable)) {
					hasTintable = true;
					break;
				}
			}
		}
		if (hasTintable) {
			TextViewCompat.setCompoundDrawableTintList(tv, ColorStateList.valueOf(iconTint));
		}
	}

	// =========================================================================
	// Color Picker Dialog: Live 14-Element Sample Template + 10x10 Swatches + Color Wheel
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
		int previewBoxHeight = UiUtils.toIntPx(ctx, 34);
		int cellMargin = UiUtils.toIntPx(ctx, 2);
		int pad = UiUtils.toIntPx(ctx, 10);

		Palette previewPalette = currentPalette.copy();
		int initialColor = previewPalette.getSlot(editingSlot);
		final int[] selectedColor = new int[]{initialColor};
		final int[] swatches = getCustomSwatches();
		final int[] editingSwatchIdx = new int[]{-1}; // -1 = big preview (+1), 0..99 = 10x10 swatch
		final boolean[] updatingHex = new boolean[]{false};

		ScrollView scroll = new ScrollView(ctx);
		LinearLayout container = new LinearLayout(ctx);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(pad, pad, pad, pad);
		scroll.addView(container);

		// -----------------------------------------------------------------
		// 1. LIVE 14-ELEMENT SAMPLE TEMPLATE
		// -----------------------------------------------------------------
		SampleTemplateView sampleTemplate = new SampleTemplateView(ctx, previewPalette, editingSlot, title);
		LinearLayout.LayoutParams tplLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tplLp.bottomMargin = pad;
		sampleTemplate.setLayoutParams(tplLp);
		container.addView(sampleTemplate);

		// -----------------------------------------------------------------
		// 2. PALETTE PANEL (Big Selected +1 Swatch, Hex Input, Reset 100, 10x10 Grid)
		// -----------------------------------------------------------------
		LinearLayout palettePanel = new LinearLayout(ctx);
		palettePanel.setOrientation(LinearLayout.VERTICAL);
		container.addView(palettePanel);

		LinearLayout previewRow = new LinearLayout(ctx);
		previewRow.setOrientation(LinearLayout.HORIZONTAL);
		previewRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams previewRowLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		previewRowLp.bottomMargin = UiUtils.toIntPx(ctx, 8);
		previewRow.setLayoutParams(previewRowLp);

		View bigPreviewBox = new View(ctx);
		bigPreviewBox.setTag("keep_own_color");
		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(previewBoxHeight * 2, previewBoxHeight);
		boxLp.rightMargin = pad;
		bigPreviewBox.setLayoutParams(boxLp);
		bigPreviewBox.setFocusable(true);
		bigPreviewBox.setClickable(true);
		bigPreviewBox.setBackground(createSwatchDrawable(initialColor, true, false));
		previewRow.addView(bigPreviewBox);

		EditText hexInput = delegate.createEditText(ctx);
		hexInput.setText(toHex(initialColor));
		hexInput.setSingleLine(true);
		hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
		hexInput.setLayoutParams(new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		previewRow.addView(hexInput);
		palettePanel.addView(previewRow);

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

		LinearLayout gridContainer = new LinearLayout(ctx);
		gridContainer.setOrientation(LinearLayout.VERTICAL);
		gridContainer.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		palettePanel.addView(gridContainer);

		// -----------------------------------------------------------------
		// 3. IN-PLACE FULL SPECTRUM COLOR WHEEL PANEL
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

		LinearLayout wheelPreviewRow = new LinearLayout(ctx);
		wheelPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
		wheelPreviewRow.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams wpLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		wpLp.bottomMargin = UiUtils.toIntPx(ctx, 10);
		wheelPreviewRow.setLayoutParams(wpLp);

		View wheelSwatchPreview = new View(ctx);
		wheelSwatchPreview.setTag("keep_own_color");
		LinearLayout.LayoutParams wspLp = new LinearLayout.LayoutParams(previewBoxHeight * 2, UiUtils.toIntPx(ctx, 30));
		wspLp.rightMargin = pad;
		wheelSwatchPreview.setLayoutParams(wspLp);
		wheelPreviewRow.addView(wheelSwatchPreview);

		TextView wheelHexText = new TextView(ctx);
		wheelHexText.setTextSize(14f);
		wheelHexText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		wheelPreviewRow.addView(wheelHexText);
		wheelPanel.addView(wheelPreviewRow);

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
						createSwatchDrawable(swatches[idx], swatches[idx] == selectedColor[0], true));
			}
		};

		IntConsumer applyPreviewColor = c -> {
			selectedColor[0] = c;
			previewPalette.setSlot(editingSlot, c);
			sampleTemplate.update(previewPalette);
			bigPreviewBox.setBackground(createSwatchDrawable(c, true, false));
			updatingHex[0] = true;
			hexInput.setText(toHex(c));
			updatingHex[0] = false;
			refreshSwatchGrid.run();
		};

		final int[] wheelWorkingColor = new int[]{initialColor};
		final int[] colorBeforeWheel = new int[]{initialColor};

		Runnable syncWheelReadout = () -> {
			int c = wheelWorkingColor[0];
			wheelSwatchPreview.setBackground(createSwatchDrawable(c, true, false));
			wheelHexText.setText(toHex(c));
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

		for (int row = 0; row < GRID_ROWS; row++) {
			LinearLayout rowLayout = new LinearLayout(ctx);
			rowLayout.setOrientation(LinearLayout.HORIZONTAL);
			rowLayout.setWeightSum(GRID_COLS);
			rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			for (int col = 0; col < GRID_COLS; col++) {
				final int idx = row * GRID_COLS + col;
				if (idx >= swatches.length) break;

				SquareSwatchView swatch = new SquareSwatchView(ctx);
				swatch.setTag("keep_own_color");
				LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
						0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
				cellLp.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
				swatch.setLayoutParams(cellLp);
				swatch.setFocusable(true);
				swatch.setClickable(true);
				swatch.setBackground(createSwatchDrawable(swatches[idx], swatches[idx] == initialColor, true));
				swatch.setOnClickListener(v -> {
					applyPreviewColor.accept(swatches[idx]);
					openWheelForTarget.accept(idx);
				});
				swatchViews.add(swatch);
				rowLayout.addView(swatch);
			}
			gridContainer.addView(rowLayout);
		}

		bigPreviewBox.setOnClickListener(v -> openWheelForTarget.accept(-1));

		resetBtn.setOnClickListener(v -> {
			int[] restored = resetCustomSwatches();
			System.arraycopy(restored, 0, swatches, 0, Math.min(restored.length, swatches.length));
			refreshSwatchGrid.run();
		});

		wheelBackBtn.setOnClickListener(v -> {
			wheelPanel.setVisibility(View.GONE);
			palettePanel.setVisibility(View.VISIBLE);
			applyPreviewColor.accept(colorBeforeWheel[0]);
		});

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
						bigPreviewBox.setBackground(createSwatchDrawable(parsed, true, false));
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

	private static final class SquareSwatchView extends View {
		SquareSwatchView(@NonNull Context ctx) {
			super(ctx);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int w = MeasureSpec.getSize(widthMeasureSpec);
			int exact = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
			super.onMeasure(exact, exact);
		}
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
	private static GradientDrawable createSwatchDrawable(@ColorInt int fillColor,
														 boolean selected,
														 boolean compact) {
		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.RECTANGLE);
		gd.setCornerRadius(compact ? 6f : 10f);
		gd.setColor(fillColor);
		int strokeColor = selected
				? (ColorUtils.calculateLuminance(fillColor) > 0.5 ? Color.BLACK : Color.WHITE)
				: 0x55888888;
		int strokeWidth = selected ? (compact ? 4 : 6) : (compact ? 1 : 2);
		gd.setStroke(strokeWidth, strokeColor);
		return gd;
	}

	// =========================================================================
	// Live Mini-App Sample Template for All 14 Logical Elements
	// =========================================================================

	private static final class SampleTemplateView extends LinearLayout {
		private final int highlightSlot;
		private final LinearLayout screenBox;
		// Zone 2: Top & Nav Bars
		private final LinearLayout topBar;
		private final ImageView topIcon;
		private final TextView topTitle;
		private final TextView topFocusBadge;
		private final ImageView topMenuIcon;
		// Zone 1: Window & List
		private final TextView bgFocusBadge;
		private final ImageView listIcon;
		private final TextView listText;
		private final TextView listSubText;
		private final TextView listFocusBadge;
		// Zone 5: Accent
		private final LinearLayout accentRow;
		private final View progressFill;
		private final View progressThumb;
		private final LinearLayout miniSwitchTrack;
		private final View miniSwitchKnob;
		private final TextView accentFocusBadge;
		// Zone 4: Menus & Dialogs
		private final LinearLayout menuCard;
		private final TextView menuFocusBadge;
		private final ImageView menuIcon;
		private final TextView menuText;
		// Zone 3: Control Panel
		private final View bottomAccentBar;
		private final LinearLayout bottomBar;
		private final ImageView playIcon;
		private final TextView nowPlayingText;
		private final ImageView nextIcon;
		private final TextView bottomFocusBadge;

		private final List<TextView> activeBadges = new ArrayList<>();
		@Nullable private AnimatorSet pulseAnim;

		SampleTemplateView(@NonNull Context ctx,
						   @NonNull Palette palette,
						   int highlightSlot,
						   @NonNull CharSequence slotTitle) {
			super(ctx);
			this.highlightSlot = highlightSlot;
			setTag("keep_own_color");
			setOrientation(VERTICAL);

			int dp2 = UiUtils.toIntPx(ctx, 2);
			int dp4 = UiUtils.toIntPx(ctx, 4);
			int dp6 = UiUtils.toIntPx(ctx, 6);
			int iconPx = UiUtils.toIntPx(ctx, 15);

			LinearLayout headerRow = new LinearLayout(ctx);
			headerRow.setTag("keep_own_color");
			headerRow.setOrientation(HORIZONTAL);
			headerRow.setGravity(Gravity.CENTER_VERTICAL);
			LayoutParams hdrLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			hdrLp.bottomMargin = dp4;

			TextView headerPill = makeFocusBadge(ctx, "★ FOCUS", true);
			headerPill.setBackground(makeRoundedBox(0xFF00E5FF, UiUtils.toIntPx(ctx, 10), Color.TRANSPARENT, 0));
			headerPill.setTextColor(Color.BLACK);
			headerRow.addView(headerPill);

			TextView headerTitle = new TextView(ctx);
			headerTitle.setTag("keep_own_color");
			headerTitle.setText("  " + slotTitle);
			headerTitle.setTextSize(12.5f);
			headerTitle.setTypeface(null, Typeface.BOLD);
			headerTitle.setTextColor(0xFF00E5FF);
			headerRow.addView(headerTitle);
			addView(headerRow, hdrLp);

			screenBox = new LinearLayout(ctx);
			screenBox.setTag("keep_own_color");
			screenBox.setOrientation(VERTICAL);
			screenBox.setPadding(dp4, dp4, dp4, dp4);
			addView(screenBox, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			topBar = new LinearLayout(ctx);
			topBar.setTag("keep_own_color");
			topBar.setOrientation(HORIZONTAL);
			topBar.setGravity(Gravity.CENTER_VERTICAL);
			topBar.setPadding(dp6, dp4, dp6, dp4);

			topIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_manage, iconPx);
			topBar.addView(topIcon);

			topTitle = new TextView(ctx);
			topTitle.setTag("keep_own_color");
			topTitle.setText("Top & Nav Bar");
			topTitle.setTextSize(10.5f);
			topTitle.setTypeface(null, Typeface.BOLD);
			topTitle.setPadding(dp4, dp2, dp4, dp2);
			LayoutParams ttLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			ttLp.leftMargin = dp4;
			topBar.addView(topTitle, ttLp);

			String topBadgeText = switch (highlightSlot) {
				case SLOT_BARS_BG -> "★ BARS BG";
				case SLOT_BARS_TEXT -> "★ BAR TEXT";
				case SLOT_BARS_ICONS -> "★ BAR ICONS";
				default -> "";
			};
			topFocusBadge = makeFocusBadge(ctx, topBadgeText, !topBadgeText.isEmpty());
			LayoutParams tfbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			tfbLp.rightMargin = dp4;
			topBar.addView(topFocusBadge, tfbLp);

			topMenuIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_more, iconPx);
			topBar.addView(topMenuIcon);
			screenBox.addView(topBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			bgFocusBadge = makeFocusBadge(ctx, "★ WINDOW BACKGROUND", highlightSlot == SLOT_WINDOW_BG);
			LayoutParams bgbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			bgbLp.gravity = Gravity.CENTER_HORIZONTAL;
			bgbLp.topMargin = dp4;
			screenBox.addView(bgFocusBadge, bgbLp);

			LinearLayout contentRow = new LinearLayout(ctx);
			contentRow.setTag("keep_own_color");
			contentRow.setOrientation(HORIZONTAL);
			contentRow.setGravity(Gravity.CENTER_VERTICAL);
			contentRow.setPadding(dp6, dp6, dp6, dp6);

			LinearLayout leftCol = new LinearLayout(ctx);
			leftCol.setTag("keep_own_color");
			leftCol.setOrientation(VERTICAL);
			LayoutParams leftLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
			leftLp.rightMargin = dp6;

			LinearLayout listTrackRow = new LinearLayout(ctx);
			listTrackRow.setTag("keep_own_color");
			listTrackRow.setOrientation(HORIZONTAL);
			listTrackRow.setGravity(Gravity.CENTER_VERTICAL);

			listIcon = makeMiniIcon(ctx, android.R.drawable.ic_media_next, iconPx);
			listTrackRow.addView(listIcon);

			LinearLayout listTextStack = new LinearLayout(ctx);
			listTextStack.setTag("keep_own_color");
			listTextStack.setOrientation(VERTICAL);
			listTextStack.setPadding(dp4, 0, dp2, 0);

			listText = new TextView(ctx);
			listText.setTag("keep_own_color");
			listText.setText("List Title");
			listText.setTextSize(10f);
			listText.setTypeface(null, Typeface.BOLD);
			listText.setPadding(dp2, dp2, dp2, 0);
			listTextStack.addView(listText);

			listSubText = new TextView(ctx);
			listSubText.setTag("keep_own_color");
			listSubText.setText("Subtitle • 3:42");
			listSubText.setTextSize(8.5f);
			listSubText.setPadding(dp2, 0, dp2, dp2);
			listTextStack.addView(listSubText);

			listTrackRow.addView(listTextStack);
			leftCol.addView(listTrackRow);

			String listBadgeText = switch (highlightSlot) {
				case SLOT_LIST_TEXT -> "★ LIST TITLE";
				case SLOT_LIST_SUBTITLE -> "★ SUBTITLES";
				case SLOT_LIST_ICONS -> "★ LIST ICONS";
				default -> "";
			};
			listFocusBadge = makeFocusBadge(ctx, listBadgeText, !listBadgeText.isEmpty());
			LayoutParams lfbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lfbLp.topMargin = dp2;
			leftCol.addView(listFocusBadge, lfbLp);

			accentRow = new LinearLayout(ctx);
			accentRow.setTag("keep_own_color");
			accentRow.setOrientation(HORIZONTAL);
			accentRow.setGravity(Gravity.CENTER_VERTICAL);
			accentRow.setPadding(dp2, dp2, dp2, dp2);
			LayoutParams arLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			arLp.topMargin = dp4;

			LinearLayout progressTrack = new LinearLayout(ctx);
			progressTrack.setTag("keep_own_color");
			progressTrack.setOrientation(HORIZONTAL);
			progressTrack.setGravity(Gravity.CENTER_VERTICAL);
			LayoutParams ptLp = new LayoutParams(0, UiUtils.toIntPx(ctx, 12), 1f);
			ptLp.rightMargin = dp6;

			progressFill = new View(ctx);
			progressFill.setTag("keep_own_color");
			progressTrack.addView(progressFill, new LayoutParams(0, UiUtils.toIntPx(ctx, 4), 0.62f));

			progressThumb = new View(ctx);
			progressThumb.setTag("keep_own_color");
			int thumbPx = UiUtils.toIntPx(ctx, 10);
			progressTrack.addView(progressThumb, new LayoutParams(thumbPx, thumbPx));

			View progressRest = new View(ctx);
			progressRest.setTag("keep_own_color");
			progressRest.setBackgroundColor(0x55888888);
			progressTrack.addView(progressRest, new LayoutParams(0, UiUtils.toIntPx(ctx, 4), 0.38f));
			accentRow.addView(progressTrack, ptLp);

			miniSwitchTrack = new LinearLayout(ctx);
			miniSwitchTrack.setTag("keep_own_color");
			miniSwitchTrack.setOrientation(HORIZONTAL);
			miniSwitchTrack.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
			miniSwitchTrack.setPadding(dp2, dp2, dp2, dp2);
			miniSwitchKnob = new View(ctx);
			miniSwitchKnob.setTag("keep_own_color");
			int knobPx = UiUtils.toIntPx(ctx, 10);
			miniSwitchTrack.addView(miniSwitchKnob, new LayoutParams(knobPx, knobPx));
			accentRow.addView(miniSwitchTrack, new LayoutParams(UiUtils.toIntPx(ctx, 24), UiUtils.toIntPx(ctx, 14)));

			leftCol.addView(accentRow, arLp);

			accentFocusBadge = makeFocusBadge(ctx, "★ ACCENT", highlightSlot == SLOT_ACCENT);
			LayoutParams afbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			afbLp.topMargin = dp2;
			leftCol.addView(accentFocusBadge, afbLp);

			contentRow.addView(leftCol, leftLp);

			menuCard = new LinearLayout(ctx);
			menuCard.setTag("keep_own_color");
			menuCard.setOrientation(VERTICAL);
			menuCard.setGravity(Gravity.CENTER_VERTICAL);
			menuCard.setPadding(dp6, dp6, dp6, dp6);

			String menuBadgeText = switch (highlightSlot) {
				case SLOT_MENU_BG -> "★ MENU BG";
				case SLOT_MENU_TEXT -> "★ MENU TEXT";
				case SLOT_MENU_ICONS -> "★ MENU ICONS";
				default -> "";
			};
			menuFocusBadge = makeFocusBadge(ctx, menuBadgeText, !menuBadgeText.isEmpty());
			LayoutParams mfbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			mfbLp.bottomMargin = dp2;
			menuCard.addView(menuFocusBadge, mfbLp);

			LinearLayout menuInnerRow = new LinearLayout(ctx);
			menuInnerRow.setTag("keep_own_color");
			menuInnerRow.setOrientation(HORIZONTAL);
			menuInnerRow.setGravity(Gravity.CENTER_VERTICAL);

			menuIcon = makeMiniIcon(ctx, android.R.drawable.ic_menu_info_details, iconPx);
			menuInnerRow.addView(menuIcon);

			menuText = new TextView(ctx);
			menuText.setTag("keep_own_color");
			menuText.setText("Popup Menu");
			menuText.setTextSize(9.5f);
			menuText.setPadding(dp4, dp2, dp2, dp2);
			menuInnerRow.addView(menuText);
			menuCard.addView(menuInnerRow);

			contentRow.addView(menuCard, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f));
			screenBox.addView(contentRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
			nowPlayingText.setText("Control Panel");
			nowPlayingText.setTextSize(10f);
			nowPlayingText.setTypeface(null, Typeface.BOLD);
			nowPlayingText.setPadding(dp4, dp2, dp4, dp2);
			LayoutParams npLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			npLp.leftMargin = dp4;
			bottomBar.addView(nowPlayingText, npLp);

			String ctrlBadgeText = switch (highlightSlot) {
				case SLOT_CONTROL_BG -> "★ CONTROL BG";
				case SLOT_CONTROL_TEXT -> "★ CONTROL TEXT";
				case SLOT_CONTROL_ICONS -> "★ CONTROL ICONS";
				default -> "";
			};
			bottomFocusBadge = makeFocusBadge(ctx, ctrlBadgeText, !ctrlBadgeText.isEmpty());
			LayoutParams bfbLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			bfbLp.rightMargin = dp4;
			bottomBar.addView(bottomFocusBadge, bfbLp);

			nextIcon = makeMiniIcon(ctx, android.R.drawable.ic_media_ff, iconPx);
			bottomBar.addView(nextIcon);

			screenBox.addView(bottomBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			TextView affectsGuide = new TextView(ctx);
			affectsGuide.setTag("keep_own_color");
			affectsGuide.setText(getAffectsGuide(highlightSlot));
			affectsGuide.setTextSize(11f);
			affectsGuide.setPadding(dp6, dp4, dp6, dp4);
			affectsGuide.setTextColor(0xFFCFD8DC);
			affectsGuide.setBackground(makeRoundedBox(0xFF1E272E, UiUtils.toIntPx(ctx, 6), 0x4400E5FF, 2));
			LayoutParams agLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			agLp.topMargin = dp4;
			addView(affectsGuide, agLp);

			TextView[] allBadges = new TextView[]{
					topFocusBadge, bgFocusBadge, listFocusBadge, accentFocusBadge, menuFocusBadge, bottomFocusBadge
			};
			for (TextView b : allBadges) {
				if (b.getVisibility() == VISIBLE) activeBadges.add(b);
			}

			update(palette);
		}

		@Override
		protected void onAttachedToWindow() {
			super.onAttachedToWindow();
			startBadgePulse();
		}

		@Override
		protected void onDetachedFromWindow() {
			stopBadgePulse();
			super.onDetachedFromWindow();
		}

		private void startBadgePulse() {
			stopBadgePulse();
			if (activeBadges.isEmpty()) return;
			List<Animator> anims = new ArrayList<>(activeBadges.size());
			for (TextView b : activeBadges) {
				ObjectAnimator oa = ObjectAnimator.ofFloat(b, View.ALPHA, 1f, 0.45f);
				oa.setDuration(650);
				oa.setRepeatCount(ValueAnimator.INFINITE);
				oa.setRepeatMode(ValueAnimator.REVERSE);
				anims.add(oa);
			}
			pulseAnim = new AnimatorSet();
			pulseAnim.playTogether(anims);
			pulseAnim.start();
		}

		private void stopBadgePulse() {
			if (pulseAnim != null) {
				pulseAnim.cancel();
				pulseAnim = null;
			}
			for (TextView b : activeBadges) {
				b.setAlpha(1f);
			}
		}

		@NonNull
		private static String getAffectsGuide(int slot) {
			return switch (slot) {
				case SLOT_WINDOW_BG -> "Affects: Main screen & list cards for Folders, IPTV, Favorites, Playlists & Settings.";
				case SLOT_LIST_TEXT -> "Affects: Primary song/folder titles & Settings row names on the main screen.";
				case SLOT_LIST_SUBTITLE -> "Affects: Secondary descriptions, track durations/artists, hex codes & input hints.";
				case SLOT_LIST_ICONS -> "Affects: Folder, track, settings & unchecked toggle icons on the main screen.";
				case SLOT_BARS_BG -> "Affects: Top ToolBar, Side/Bottom NavBar, NavBar Extension, Floating Button & system bars.";
				case SLOT_BARS_TEXT -> "Affects: Page titles & labels on the Top ToolBar, NavBar & NavBar Extension.";
				case SLOT_BARS_ICONS -> "Affects: Back, menu, Cast, NavBar, Extension & Floating Button icons.";
				case SLOT_CONTROL_BG -> "Affects: Playback Control Panel deck background.";
				case SLOT_CONTROL_TEXT -> "Affects: Track title, artist & playback time displayed inside Control Panel.";
				case SLOT_CONTROL_ICONS -> "Affects: Play/Pause, Skip, Rewind & player control icons on Control Panel.";
				case SLOT_MENU_BG -> "Affects: Pop-up context menus, NavBar menus, option dialogs & floating cards.";
				case SLOT_MENU_TEXT -> "Affects: Menu item labels & dialog text inside popups.";
				case SLOT_MENU_ICONS -> "Affects: Action & option vector icons inside pop-up menus & dialogs.";
				case SLOT_ACCENT -> "Affects: Active track highlight, SeekBar progress, switches, checkboxes & dialog buttons.";
				default -> "Affects: Selected interface theme element.";
			};
		}

		@NonNull
		private static TextView makeFocusBadge(@NonNull Context ctx, @NonNull String label, boolean visible) {
			TextView tv = new TextView(ctx);
			tv.setTag("keep_own_color");
			tv.setText(label);
			tv.setTextSize(8.5f);
			tv.setTypeface(null, Typeface.BOLD);
			int hPad = UiUtils.toIntPx(ctx, 5);
			int vPad = UiUtils.toIntPx(ctx, 1);
			tv.setPadding(hPad, vPad, hPad, vPad);
			tv.setVisibility(visible ? VISIBLE : GONE);
			return tv;
		}

		private static ImageView makeMiniIcon(@NonNull Context ctx, int drawableRes, int sizePx) {
			ImageView iv = new ImageView(ctx);
			iv.setTag("keep_own_color");
			iv.setImageResource(drawableRes);
			iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
			int pad = UiUtils.toIntPx(ctx, 2);
			iv.setPadding(pad, pad, pad, pad);
			iv.setLayoutParams(new LayoutParams(sizePx + pad * 2, sizePx + pad * 2));
			return iv;
		}

		private static void styleBadge(@NonNull TextView badge, @ColorInt int bgColor) {
			if (badge.getVisibility() != VISIBLE) return;
			int hi = pickHighlight(bgColor, 0xFF00E5FF, 0xFFFF1744);
			int txt = ColorUtils.calculateLuminance(hi) > 0.45 ? Color.BLACK : Color.WHITE;
			badge.setBackground(makeRoundedBox(hi, 14f, Color.TRANSPARENT, 0));
			badge.setTextColor(txt);
		}

		void update(@NonNull Palette p) {
			int hiCyan = 0xFF00E5FF;
			int hiRed = 0xFFFF1744;

			int bgHi = pickHighlight(p.windowBg, hiCyan, hiRed);
			int barHi = pickHighlight(p.primaryBars, hiCyan, hiRed);
			int ctrlHi = pickHighlight(p.controlPanelBg, hiCyan, hiRed);
			int menuHi = pickHighlight(p.surfaceMenu, hiCyan, hiRed);

			screenBox.setBackground(makeRoundedBox(p.windowBg, 10f,
					highlightSlot == SLOT_WINDOW_BG ? bgHi : 0x88888888,
					highlightSlot == SLOT_WINDOW_BG ? 7 : 2));
			styleBadge(bgFocusBadge, p.windowBg);

			listText.setTextColor(p.listText);
			listText.setBackground(highlightSlot == SLOT_LIST_TEXT
					? makeRoundedBox(Color.TRANSPARENT, 5f, bgHi, 4) : null);

			listSubText.setTextColor(p.listSubtitle);
			listSubText.setBackground(highlightSlot == SLOT_LIST_SUBTITLE
					? makeRoundedBox(Color.TRANSPARENT, 5f, bgHi, 4) : null);

			applyIconStyle(listIcon, ColorStateList.valueOf(p.listIcons), highlightSlot == SLOT_LIST_ICONS, bgHi);
			styleBadge(listFocusBadge, p.windowBg);

			topBar.setBackground(makeRoundedBox(p.primaryBars, 6f,
					highlightSlot == SLOT_BARS_BG ? barHi : Color.TRANSPARENT,
					highlightSlot == SLOT_BARS_BG ? 6 : 0));
			topTitle.setTextColor(p.barsText);
			topTitle.setBackground(highlightSlot == SLOT_BARS_TEXT
					? makeRoundedBox(Color.TRANSPARENT, 5f, barHi, 4) : null);
			ColorStateList barIconCsl = ColorStateList.valueOf(p.barsIcons);
			applyIconStyle(topIcon, barIconCsl, highlightSlot == SLOT_BARS_ICONS, barHi);
			applyIconStyle(topMenuIcon, barIconCsl, highlightSlot == SLOT_BARS_ICONS, barHi);
			styleBadge(topFocusBadge, p.primaryBars);

			bottomBar.setBackground(makeRoundedBox(p.controlPanelBg, 6f,
					highlightSlot == SLOT_CONTROL_BG ? ctrlHi : Color.TRANSPARENT,
					highlightSlot == SLOT_CONTROL_BG ? 6 : 0));
			nowPlayingText.setTextColor(p.controlPanelText);
			nowPlayingText.setBackground(highlightSlot == SLOT_CONTROL_TEXT
					? makeRoundedBox(Color.TRANSPARENT, 5f, ctrlHi, 4) : null);
			ColorStateList ctrlIconCsl = ColorStateList.valueOf(p.controlPanelIcons);
			applyIconStyle(playIcon, ctrlIconCsl, highlightSlot == SLOT_CONTROL_ICONS, ctrlHi);
			applyIconStyle(nextIcon, ctrlIconCsl, highlightSlot == SLOT_CONTROL_ICONS, ctrlHi);
			styleBadge(bottomFocusBadge, p.controlPanelBg);

			menuCard.setBackground(makeRoundedBox(p.surfaceMenu, 8f,
					highlightSlot == SLOT_MENU_BG ? menuHi : 0x55888888,
					highlightSlot == SLOT_MENU_BG ? 6 : 2));
			menuText.setTextColor(p.menuText);
			menuText.setBackground(highlightSlot == SLOT_MENU_TEXT
					? makeRoundedBox(Color.TRANSPARENT, 5f, menuHi, 4) : null);
			applyIconStyle(menuIcon, ColorStateList.valueOf(p.menuIcons), highlightSlot == SLOT_MENU_ICONS, menuHi);
			styleBadge(menuFocusBadge, p.surfaceMenu);

			progressFill.setBackgroundColor(p.accent);
			bottomAccentBar.setBackgroundColor(p.accent);
			progressThumb.setBackground(makeCircle(p.accent));
			miniSwitchTrack.setBackground(makeRoundedBox(ColorUtils.setAlphaComponent(p.accent, 140), 20f, Color.TRANSPARENT, 0));
			miniSwitchKnob.setBackground(makeCircle(p.accent));
			accentRow.setBackground(makeRoundedBox(Color.TRANSPARENT, 6f,
					highlightSlot == SLOT_ACCENT ? bgHi : Color.TRANSPARENT,
					highlightSlot == SLOT_ACCENT ? 5 : 0));
			styleBadge(accentFocusBadge, p.windowBg);
		}

		private static void applyIconStyle(@NonNull ImageView iv,
										   @NonNull ColorStateList tint,
										   boolean focused,
										   @ColorInt int ringColor) {
			iv.setImageTintList(tint);
			iv.setBackground(focused ? makeRoundedBox(Color.TRANSPARENT, 99f, ringColor, 4) : null);
		}

		@ColorInt
		private static int pickHighlight(@ColorInt int bg, @ColorInt int primaryHi, @ColorInt int altHi) {
			return ColorUtils.calculateContrast(primaryHi, bg) >= 2.8 ? primaryHi : altHi;
		}

		@NonNull
		private static GradientDrawable makeCircle(@ColorInt int fill) {
			GradientDrawable gd = new GradientDrawable();
			gd.setShape(GradientDrawable.OVAL);
			gd.setColor(fill);
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
			canvas.drawCircle(cx, cy, radius, huePaint);
			canvas.drawCircle(cx, cy, radius, satPaint);
			int darkAlpha = Math.round((1f - hsv[2]) * 255f);
			if (darkAlpha > 0) {
				valOverlayPaint.setColor(Color.argb(darkAlpha, 0, 0, 0));
				canvas.drawCircle(cx, cy, radius, valOverlayPaint);
			}
			canvas.drawCircle(cx, cy, radius, borderPaint);

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

	// =========================================================================
	// Settings Row Trailing Color Swatch Overlay (Zones, Slots & Preset Strips)
	// =========================================================================

	public static final class SwatchItemDecoration extends RecyclerView.ItemDecoration {
		private static final class RowSpec {
			final boolean isAutoPrefs;
			@Nullable final int[] slots;
			@Nullable final int[] fixedColors;

			RowSpec(boolean isAutoPrefs, @NonNull int[] slots) {
				this.isAutoPrefs = isAutoPrefs;
				this.slots = slots;
				this.fixedColors = null;
			}

			RowSpec(@NonNull int[] fixedColors) {
				this.isAutoPrefs = false;
				this.slots = null;
				this.fixedColors = fixedColors;
			}
		}

		private final Map<PreferenceSet, SparseArray<RowSpec>> registry = new IdentityHashMap<>();
		@Nullable private PreferenceSet activeSet;

		private final Paint capsuleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint capsuleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint chipFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint chipOuterBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint chipInnerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF tempRect = new RectF();

		private final float edgeMargin;
		private final float singleChipW;
		private final float singleChipH;
		private final float multiChipSize;
		private final float chipGap;
		private final float chipRadius;
		private final float capsulePadH;
		private final float capsulePadV;
		private final float capsuleRadius;

		public SwatchItemDecoration(@NonNull Context ctx) {
			edgeMargin = UiUtils.toIntPx(ctx, 14);
			singleChipW = UiUtils.toIntPx(ctx, 34);
			singleChipH = UiUtils.toIntPx(ctx, 22);
			multiChipSize = UiUtils.toIntPx(ctx, 18);
			chipGap = UiUtils.toIntPx(ctx, 4);
			chipRadius = UiUtils.toIntPx(ctx, 5);
			capsulePadH = UiUtils.toIntPx(ctx, 5);
			capsulePadV = UiUtils.toIntPx(ctx, 4);
			capsuleRadius = UiUtils.toIntPx(ctx, 9);

			capsuleFillPaint.setStyle(Paint.Style.FILL);
			capsuleFillPaint.setColor(0x55000000);

			capsuleStrokePaint.setStyle(Paint.Style.STROKE);
			capsuleStrokePaint.setStrokeWidth(Math.max(1f, UiUtils.toIntPx(ctx, 1)));
			capsuleStrokePaint.setColor(0x44FFFFFF);

			chipFillPaint.setStyle(Paint.Style.FILL);

			chipOuterBorderPaint.setStyle(Paint.Style.STROKE);
			chipOuterBorderPaint.setStrokeWidth(Math.max(1.5f, UiUtils.toIntPx(ctx, 1) * 1.4f));

			chipInnerBorderPaint.setStyle(Paint.Style.STROKE);
			chipInnerBorderPaint.setStrokeWidth(Math.max(1f, UiUtils.toIntPx(ctx, 1)));
		}

		public void setActiveSet(@Nullable PreferenceSet set) {
			this.activeSet = set;
		}

		public void clearSet(@NonNull PreferenceSet set) {
			SparseArray<RowSpec> rows = registry.get(set);
			if (rows != null) rows.clear();
		}

		public void registerRow(@NonNull PreferenceSet set,
								int position,
								boolean isAutoPrefs,
								@NonNull int[] slots) {
			SparseArray<RowSpec> rows = registry.get(set);
			if (rows == null) {
				rows = new SparseArray<>();
				registry.put(set, rows);
			}
			rows.put(position, new RowSpec(isAutoPrefs, slots));
		}

		public void registerFixedRow(@NonNull PreferenceSet set,
									 int position,
									 @NonNull int[] colors) {
			SparseArray<RowSpec> rows = registry.get(set);
			if (rows == null) {
				rows = new SparseArray<>();
				registry.put(set, rows);
			}
			rows.put(position, new RowSpec(colors));
		}

		@Override
		public void onDrawOver(@NonNull Canvas c,
							   @NonNull RecyclerView parent,
							   @NonNull RecyclerView.State state) {
			if (activeSet == null) return;
			SparseArray<RowSpec> rows = registry.get(activeSet);
			if (rows == null || rows.size() == 0) return;

			Palette phonePalette = null;
			Palette autoPalette = null;
			boolean isRtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

			for (int i = 0; i < parent.getChildCount(); i++) {
				View child = parent.getChildAt(i);
				int pos = parent.getChildAdapterPosition(child);
				if (pos == RecyclerView.NO_POSITION) continue;

				RowSpec spec = rows.get(pos);
				if (spec == null) continue;

				int count = (spec.fixedColors != null)
						? spec.fixedColors.length
						: (spec.slots != null ? spec.slots.length : 0);
				if (count == 0) continue;

				Palette palette = null;
				if (spec.fixedColors == null) {
					if (spec.isAutoPrefs) {
						if (autoPalette == null) autoPalette = getActiveCustomPalette(true);
						palette = autoPalette;
					} else {
						if (phonePalette == null) phonePalette = getActiveCustomPalette(false);
						palette = phonePalette;
					}
				}

				boolean single = (count == 1);
				float chipW = single ? singleChipW : multiChipSize;
				float chipH = single ? singleChipH : multiChipSize;
				float totalChipsW = count * chipW + (count - 1) * chipGap;

				float centerY = (child.getTop() + child.getBottom()) / 2f;
				float startX = isRtl
						? (child.getLeft() + edgeMargin)
						: (child.getRight() - edgeMargin - totalChipsW);

				if (!single) {
					tempRect.set(
							startX - capsulePadH,
							centerY - (chipH / 2f) - capsulePadV,
							startX + totalChipsW + capsulePadH,
							centerY + (chipH / 2f) + capsulePadV
					);
					c.drawRoundRect(tempRect, capsuleRadius, capsuleRadius, capsuleFillPaint);
					c.drawRoundRect(tempRect, capsuleRadius, capsuleRadius, capsuleStrokePaint);
				}

				float topY = centerY - (chipH / 2f);
				for (int s = 0; s < count; s++) {
					int color = (spec.fixedColors != null)
							? spec.fixedColors[s]
							: palette.getSlot(spec.slots[s]);
					float x = startX + s * (chipW + chipGap);

					tempRect.set(x, topY, x + chipW, topY + chipH);
					chipFillPaint.setColor(color);
					c.drawRoundRect(tempRect, chipRadius, chipRadius, chipFillPaint);

					boolean lightChip = ColorUtils.calculateLuminance(color) > 0.5;
					chipOuterBorderPaint.setColor(lightChip ? 0xAA000000 : 0xDDFFFFFF);
					chipInnerBorderPaint.setColor(lightChip ? 0x66FFFFFF : 0x66000000);
					c.drawRoundRect(tempRect, chipRadius, chipRadius, chipOuterBorderPaint);
					tempRect.inset(1.5f, 1.5f);
					c.drawRoundRect(tempRect, Math.max(2f, chipRadius - 1.5f),
							Math.max(2f, chipRadius - 1.5f), chipInnerBorderPaint);
				}
			}
		}
	}
}