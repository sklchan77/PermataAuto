package my.app.permata.ui.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import my.app.permata.R;
import my.app.permata.ui.theme.CustomThemeManager.Palette;

/**
 * 100 (10x10) curated 14-slot Permata Auto themes across 8 categories,
 * plus an astronomical 24 Solar Terms (二十四节令) resolver.
 */
public final class CuratedThemeCatalog {

	public enum Category {
		WORLD_CLASSICS(R.string.custom_theme_cat_classics),
		SOLAR_TERMS(R.string.custom_theme_cat_solar_terms),
		FOUR_SEASONS(R.string.custom_theme_cat_seasons),
		FESTIVALS(R.string.custom_theme_cat_festivals),
		AUTOMOTIVE(R.string.custom_theme_cat_automotive),
		GEMSTONES(R.string.custom_theme_cat_gemstones),
		NATURE(R.string.custom_theme_cat_nature),
		STUDIO_VIBES(R.string.custom_theme_cat_studio);

		@StringRes public final int titleRes;

		Category(@StringRes int titleRes) {
			this.titleRes = titleRes;
		}
	}

	public static final class Entry {
		public final Category category;
		public final Palette palette;

		Entry(@NonNull Category category, @NonNull Palette palette) {
			this.category = category;
			this.palette = palette;
		}
	}

	private static final List<Entry> ALL_ENTRIES;
	private static final Map<String, Category> NAME_TO_CATEGORY = new HashMap<>();
	private static final List<Palette> SOLAR_TERM_PALETTES;

	static {
		List<Entry> list = new ArrayList<>(100);

		// =====================================================================
		// 1. WORLD ICONIC PALETTES (10)
		// =====================================================================
		list.add(t(Category.WORLD_CLASSICS, "Nordic Aurora",
				0xFF242933, 0xFFECEFF4, 0xFF9BA8BF, 0xFF88C0D0,
				0xFF2E3440, 0xFFECEFF4, 0xFF88C0D0,
				0xFF3B4252, 0xFFECEFF4, 0xFF8FBCBB,
				0xFF333A48, 0xFFE5E9F0, 0xFF81A1C1, 0xFF88C0D0));

		list.add(t(Category.WORLD_CLASSICS, "Tokyo Midnight",
				0xFF16161E, 0xFFC0CAF5, 0xFF7882A8, 0xFF7AA2F7,
				0xFF1F2335, 0xFFE0E6FF, 0xFF7DCFFF,
				0xFF24283B, 0xFFE0E6FF, 0xFFBB9AF7,
				0xFF292E42, 0xFFC0CAF5, 0xFF7DCFFF, 0xFF7AA2F7));

		list.add(t(Category.WORLD_CLASSICS, "Velvet Mocha",
				0xFF181825, 0xFFCDD6F4, 0xFF9399B2, 0xFFCBA6F7,
				0xFF1E1E2E, 0xFFCDD6F4, 0xFF89B4FA,
				0xFF262637, 0xFFF5E0DC, 0xFFF5C2E7,
				0xFF313244, 0xFFCDD6F4, 0xFF94E2D5, 0xFFCBA6F7));

		list.add(t(Category.WORLD_CLASSICS, "Kanagawa Wave",
				0xFF16161D, 0xFFDCD7BA, 0xFF9A9682, 0xFF7E9CD8,
				0xFF1F1F28, 0xFFDCD7BA, 0xFFE6C384,
				0xFF252535, 0xFFDCD7BA, 0xFF7FB4CA,
				0xFF2A2A37, 0xFFDCD7BA, 0xFFE6C384, 0xFFE6C384));

		list.add(t(Category.WORLD_CLASSICS, "Dracula Nocturne",
				0xFF1E1F29, 0xFFF8F8F2, 0xFF95A2C9, 0xFFBD93F9,
				0xFF282A36, 0xFFF8F8F2, 0xFF8BE9FD,
				0xFF343746, 0xFFF8F8F2, 0xFFFF79C6,
				0xFF383A4B, 0xFFF8F8F2, 0xFF50FA7B, 0xFFBD93F9));

		list.add(t(Category.WORLD_CLASSICS, "Rosé Pine Dusk",
				0xFF191724, 0xFFE0DEF4, 0xFF908CAA, 0xFFEBBCBA,
				0xFF1F1D2E, 0xFFE0DEF4, 0xFFF6C177,
				0xFF26233A, 0xFFE0DEF4, 0xFFC4A7E7,
				0xFF2A273F, 0xFFE0DEF4, 0xFF9CCFD8, 0xFFEBBCBA));

		list.add(t(Category.WORLD_CLASSICS, "Gruvbox Amber",
				0xFF1D2021, 0xFFEBDBB2, 0xFFA89984, 0xFFFABD2F,
				0xFF282828, 0xFFFBF1C7, 0xFFFE8019,
				0xFF32302F, 0xFFFBF1C7, 0xFFFABD2F,
				0xFF3C3836, 0xFFEBDBB2, 0xFF8EC07C, 0xFFFE8019));

		list.add(t(Category.WORLD_CLASSICS, "Solarized Abyss",
				0xFF00212B, 0xFFEEE8D5, 0xFF839496, 0xFF2AA198,
				0xFF002B36, 0xFFFDF6E3, 0xFF268BD2,
				0xFF073642, 0xFFFDF6E3, 0xFFB58900,
				0xFF06323D, 0xFFEEE8D5, 0xFF2AA198, 0xFF2AA198));

		list.add(t(Category.WORLD_CLASSICS, "Monokai Carbon",
				0xFF221F22, 0xFFFCFCFA, 0xFF939293, 0xFF78DCE8,
				0xFF2D2A2E, 0xFFFCFCFA, 0xFFFFD866,
				0xFF38353A, 0xFFFCFCFA, 0xFFFF6188,
				0xFF403E41, 0xFFFCFCFA, 0xFFA9DC76, 0xFFFFD866));

		list.add(t(Category.WORLD_CLASSICS, "Everforest Pine",
				0xFF232A2E, 0xFFD3C6AA, 0xFF9DA9A0, 0xFFA7C080,
				0xFF2D353B, 0xFFE5DCC5, 0xFF83C092,
				0xFF343F44, 0xFFE5DCC5, 0xFFDBBC7F,
				0xFF3D484D, 0xFFD3C6AA, 0xFFA7C080, 0xFFA7C080));

		// =====================================================================
		// 2. 24 SOLAR TERMS • 二十四节令 (24)
		// =====================================================================
		// Spring (春)
		list.add(t(Category.SOLAR_TERMS, "立春 • Start of Spring",
				0xFF141E18, 0xFFEAF5E4, 0xFF99B29E, 0xFFA3D977,
				0xFF1D2D22, 0xFFF2FAEC, 0xFFA3D977,
				0xFF253B2B, 0xFFF2FAEC, 0xFFDCE775,
				0xFF283D2F, 0xFFEAF5E4, 0xFF81C784, 0xFFA3D977));

		list.add(t(Category.SOLAR_TERMS, "雨水 • Rain Water",
				0xFF131D24, 0xFFE4F0F6, 0xFF8FA8B8, 0xFF80CBC4,
				0xFF1C2C36, 0xFFEDF7FC, 0xFF80DEEA,
				0xFF243845, 0xFFEDF7FC, 0xFFA7FFEB,
				0xFF273C4A, 0xFFE4F0F6, 0xFF80CBC4, 0xFF64D8CB));

		list.add(t(Category.SOLAR_TERMS, "惊蛰 • Awakening Insects",
				0xFF171926, 0xFFEDEFFA, 0xFF969DB8, 0xFFAED581,
				0xFF23273B, 0xFFF5F7FF, 0xFFFFEE58,
				0xFF2C314A, 0xFFF5F7FF, 0xFF9CCC65,
				0xFF313652, 0xFFEDEFFA, 0xFFFFEE58, 0xFF9CCC65));

		list.add(t(Category.SOLAR_TERMS, "春分 • Spring Equinox",
				0xFF211722, 0xFFFAEBF3, 0xFFB89CB0, 0xFFF48FB1,
				0xFF302133, 0xFFFFF2F8, 0xFFF8BBD0,
				0xFF3D2A40, 0xFFFFF2F8, 0xFFA5D6A7,
				0xFF422F45, 0xFFFAEBF3, 0xFFF48FB1, 0xFFF48FB1));

		list.add(t(Category.SOLAR_TERMS, "清明 • Pure Brightness",
				0xFF11211E, 0xFFE6F5F0, 0xFF8CB0A6, 0xFF66D19E,
				0xFF1A332E, 0xFFF0FAF6, 0xFF80E2B5,
				0xFF22423C, 0xFFF0FAF6, 0xFFB2DFDB,
				0xFF264741, 0xFFE6F5F0, 0xFF66D19E, 0xFF66D19E));

		list.add(t(Category.SOLAR_TERMS, "谷雨 • Grain Rain",
				0xFF1B1921, 0xFFF2ECF7, 0xFFA398B0, 0xFFEC709F,
				0xFF282433, 0xFFF9F4FC, 0xFFF48FB1,
				0xFF332E42, 0xFFF9F4FC, 0xFF81C784,
				0xFF383347, 0xFFF2ECF7, 0xFFCE93D8, 0xFFEC709F));

		// Summer (夏)
		list.add(t(Category.SOLAR_TERMS, "立夏 • Start of Summer",
				0xFF152019, 0xFFEBF5ED, 0xFF92B09B, 0xFFFF7043,
				0xFF1F3126, 0xFFF4FAF5, 0xFFFF8A65,
				0xFF294032, 0xFFF4FAF5, 0xFF69F0AE,
				0xFF2C4536, 0xFFEBF5ED, 0xFFFF7043, 0xFFFF7043));

		list.add(t(Category.SOLAR_TERMS, "小满 • Grain Buds",
				0xFF1D1D14, 0xFFF5F3E1, 0xFFB0AD8D, 0xFFE6C24C,
				0xFF2B2B1E, 0xFFFAF8EA, 0xFFFFD54F,
				0xFF383826, 0xFFFAF8EA, 0xFFAED581,
				0xFF3E3D2B, 0xFFF5F3E1, 0xFFE6C24C, 0xFFE6C24C));

		list.add(t(Category.SOLAR_TERMS, "芒种 • Grain in Ear",
				0xFF1E1A13, 0xFFF7F0E0, 0xFFB5A486, 0xFFFFB74D,
				0xFF2E271C, 0xFFFFF8EA, 0xFFFFCA28,
				0xFF3D3324, 0xFFFFF8EA, 0xFF81C784,
				0xFF423828, 0xFFF7F0E0, 0xFFFFB74D, 0xFFFFB300));

		list.add(t(Category.SOLAR_TERMS, "夏至 • Summer Solstice",
				0xFF121C22, 0xFFEBF4FA, 0xFF8DA6B8, 0xFFFFCA28,
				0xFF1C2C36, 0xFFF5FAFF, 0xFFFF7043,
				0xFF243A47, 0xFFF5FAFF, 0xFFFFD54F,
				0xFF29404F, 0xFFEBF4FA, 0xFF4DD0E1, 0xFFFF9800));

		list.add(t(Category.SOLAR_TERMS, "小暑 • Minor Heat",
				0xFF12211E, 0xFFE9F5F2, 0xFF8EAEA6, 0xFFFF80AB,
				0xFF1B322E, 0xFFF2FAF8, 0xFFFF80AB,
				0xFF24423C, 0xFFF2FAF8, 0xFF80CBC4,
				0xFF284741, 0xFFE9F5F2, 0xFFFF80AB, 0xFFFF80AB));

		list.add(t(Category.SOLAR_TERMS, "大暑 • Major Heat",
				0xFF211312, 0xFFFCECE8, 0xFFBD968E, 0xFFFF5722,
				0xFF331D1A, 0xFFFFF3F0, 0xFFFFAB40,
				0xFF422521, 0xFFFFF3F0, 0xFFFFD740,
				0xFF4A2A25, 0xFFFCECE8, 0xFFFF7043, 0xFFFF5722));

		// Autumn (秋)
		list.add(t(Category.SOLAR_TERMS, "立秋 • Start of Autumn",
				0xFF1F1B16, 0xFFF5EFE6, 0xFFB0A18E, 0xFFD99B52,
				0xFF2E2820, 0xFFFAF5EE, 0xFFE5B36A,
				0xFF3B3329, 0xFFFAF5EE, 0xFFC5A880,
				0xFF42392E, 0xFFF5EFE6, 0xFFD99B52, 0xFFD99B52));

		list.add(t(Category.SOLAR_TERMS, "处暑 • End of Heat",
				0xFF141D26, 0xFFEAF2FA, 0xFF90A6BD, 0xFFFFB07C,
				0xFF1F2D3B, 0xFFF2F7FC, 0xFF64B5F6,
				0xFF283A4D, 0xFFF2F7FC, 0xFFFFB07C,
				0xFF2D4054, 0xFFEAF2FA, 0xFF81D4FA, 0xFFFFB07C));

		list.add(t(Category.SOLAR_TERMS, "白露 • White Dew",
				0xFF161D24, 0xFFEDF3F8, 0xFF98A9B8, 0xFFB8D4E3,
				0xFF212C36, 0xFFF4F8FC, 0xFFDCEBF5,
				0xFF2B3945, 0xFFF4F8FC, 0xFF90CAF9,
				0xFF303F4D, 0xFFEDF3F8, 0xFFB8D4E3, 0xFF90CAF9));

		list.add(t(Category.SOLAR_TERMS, "秋分 • Autumn Equinox",
				0xFF211913, 0xFFFAF0E1, 0xFFB8A286, 0xFFFFC145,
				0xFF33261C, 0xFFFFF6EA, 0xFFFFCA28,
				0xFF423124, 0xFFFFF6EA, 0xFFFFB74D,
				0xFF4A3729, 0xFFFAF0E1, 0xFFFFC145, 0xFFFFB300));

		list.add(t(Category.SOLAR_TERMS, "寒露 • Cold Dew",
				0xFF1F1621, 0xFFF5ECF7, 0xFFAE99B5, 0xFFE0A952,
				0xFF2E2133, 0xFFFAF2FC, 0xFFEBC16E,
				0xFF3B2B42, 0xFFFAF2FC, 0xFFCE93D8,
				0xFF42304A, 0xFFF5ECF7, 0xFFE0A952, 0xFFE0A952));

		list.add(t(Category.SOLAR_TERMS, "霜降 • Frost's Descent",
				0xFF211416, 0xFFFAECEE, 0xFFB5969B, 0xFFE5534B,
				0xFF331F22, 0xFFFFF2F4, 0xFFFF7043,
				0xFF42282C, 0xFFFFF2F4, 0xFFFFAB91,
				0xFF4A2D31, 0xFFFAECEE, 0xFFE5534B, 0xFFE5534B));

		// Winter (冬)
		list.add(t(Category.SOLAR_TERMS, "立冬 • Start of Winter",
				0xFF1A191B, 0xFFEEECEB, 0xFFA19FA6, 0xFF9DB4C0,
				0xFF272629, 0xFFF5F4F2, 0xFFB8CED9,
				0xFF333136, 0xFFF5F4F2, 0xFFC2B29F,
				0xFF3A383D, 0xFFEEECEB, 0xFF9DB4C0, 0xFF9DB4C0));

		list.add(t(Category.SOLAR_TERMS, "小雪 • Minor Snow",
				0xFF141B24, 0xFFEDF4FA, 0xFF93A8BD, 0xFF9AD0EC,
				0xFF1E2936, 0xFFF4F9FC, 0xFFB3E5FC,
				0xFF273647, 0xFFF4F9FC, 0xFFE1F5FE,
				0xFF2C3C4F, 0xFFEDF4FA, 0xFF9AD0EC, 0xFF81D4FA));

		list.add(t(Category.SOLAR_TERMS, "大雪 • Major Snow",
				0xFF111821, 0xFFF0F6FC, 0xFF96ABB8, 0xFFC5E8F7,
				0xFF1B2533, 0xFFFFFFFF, 0xFFE0F7FA,
				0xFF233042, 0xFFFFFFFF, 0xFF80DEEA,
				0xFF28374B, 0xFFF0F6FC, 0xFFC5E8F7, 0xFFB2EBF2));

		list.add(t(Category.SOLAR_TERMS, "冬至 • Winter Solstice",
				0xFF1C1317, 0xFFF8ECF0, 0xFFB0969F, 0xFFE6B85C,
				0xFF2C1D24, 0xFFFFF3F6, 0xFFFF6B81,
				0xFF3A262F, 0xFFFFF3F6, 0xFFFFD166,
				0xFF422B36, 0xFFF8ECF0, 0xFFE6B85C, 0xFFFF6B81));

		list.add(t(Category.SOLAR_TERMS, "小寒 • Minor Cold",
				0xFF151924, 0xFFEDF1FA, 0xFF939FB8, 0xFFF06292,
				0xFF1F2636, 0xFFF5F8FF, 0xFFF48FB1,
				0xFF293247, 0xFFF5F8FF, 0xFF90CAF9,
				0xFF2E384F, 0xFFEDF1FA, 0xFFF06292, 0xFFF06292));

		list.add(t(Category.SOLAR_TERMS, "大寒 • Major Cold",
				0xFF0E1420, 0xFFE8F1FA, 0xFF899EB8, 0xFF64B5F6,
				0xFF172033, 0xFFF2F8FF, 0xFF90CAF9,
				0xFF1F2B44, 0xFFF2F8FF, 0xFF80D8FF,
				0xFF24324E, 0xFFE8F1FA, 0xFF64B5F6, 0xFF40C4FF));

		// =====================================================================
		// 3. FOUR SEASONS • 四季 (4)
		// =====================================================================
		list.add(t(Category.FOUR_SEASONS, "Spring Blossom",
				0xFF16211B, 0xFFEEF7F1, 0xFF94B2A0, 0xFFF48FB1,
				0xFF203229, 0xFFF5FBF7, 0xFF81C784,
				0xFF2A4236, 0xFFF5FBF7, 0xFFF8BBD0,
				0xFF2F4A3C, 0xFFEEF7F1, 0xFF81C784, 0xFFF48FB1));

		list.add(t(Category.FOUR_SEASONS, "Summer Sunburst",
				0xFF0E1E29, 0xFFEBF6FC, 0xFF88ACC2, 0xFFFFCA28,
				0xFF152E40, 0xFFF5FAFF, 0xFF26C6DA,
				0xFF1D3E54, 0xFFF5FAFF, 0xFFFFD54F,
				0xFF21465E, 0xFFEBF6FC, 0xFF4DD0E1, 0xFFFFCA28));

		list.add(t(Category.FOUR_SEASONS, "Autumn Maple",
				0xFF211612, 0xFFFAF0E6, 0xFFBA9E8C, 0xFFFF7043,
				0xFF33211A, 0xFFFFF5EC, 0xFFFFB74D,
				0xFF422B22, 0xFFFFF5EC, 0xFFFF8A65,
				0xFF4A3127, 0xFFFAF0E6, 0xFFFF7043, 0xFFFF7043));

		list.add(t(Category.FOUR_SEASONS, "Winter Frost",
				0xFF121A26, 0xFFEBF3FA, 0xFF8FA6C0, 0xFF81D4FA,
				0xFF1B273A, 0xFFF5FAFF, 0xFFB3E5FC,
				0xFF24344D, 0xFFF5FAFF, 0xFFE1F5FE,
				0xFF293B57, 0xFFEBF3FA, 0xFF81D4FA, 0xFF4FC3F7));

		// =====================================================================
		// 4. WORLD MAJOR FESTIVALS & HOLIDAYS (22)
		// =====================================================================
		list.add(t(Category.FESTIVALS, "Chinese New Year",
				0xFF26090B, 0xFFFFF3E0, 0xFFD4A28E, 0xFFFFD700,
				0xFF8B0000, 0xFFFFF8E1, 0xFFFFD700,
				0xFF6B080D, 0xFFFFF8E1, 0xFFFFD54F,
				0xFF3D0E12, 0xFFFFF3E0, 0xFFFFD700, 0xFFFFD700));

		list.add(t(Category.FESTIVALS, "Lantern Festival • 元宵",
				0xFF1E0D14, 0xFFFFF0E5, 0xFFC79BA0, 0xFFFF6E40,
				0xFF4A1220, 0xFFFFF5EB, 0xFFFFCA28,
				0xFF5E1728, 0xFFFFF5EB, 0xFFFF8A65,
				0xFF3B1522, 0xFFFFF0E5, 0xFFFFCA28, 0xFFFF6E40));

		list.add(t(Category.FESTIVALS, "Hari Raya Aidilfitri",
				0xFF0A2116, 0xFFEDF7F0, 0xFF8EB8A0, 0xFFFFD54F,
				0xFF0E4D2F, 0xFFF5FBF7, 0xFFFFD700,
				0xFF145E3B, 0xFFF5FBF7, 0xFF69F0AE,
				0xFF153827, 0xFFEDF7F0, 0xFFFFD54F, 0xFF00E676));

		list.add(t(Category.FESTIVALS, "Hari Raya Aidiladha",
				0xFF0C1F21, 0xFFEBF6F5, 0xFF8BB2B5, 0xFFE6C367,
				0xFF124147, 0xFFF4FAFA, 0xFFE6C367,
				0xFF185259, 0xFFF4FAFA, 0xFF80CBC4,
				0xFF17363A, 0xFFEBF6F5, 0xFFE6C367, 0xFFE6C367));

		list.add(t(Category.FESTIVALS, "Deepavali",
				0xFF210E24, 0xFFFDF0E6, 0xFFC29DB8, 0xFFFFB300,
				0xFF4A154B, 0xFFFFF7ED, 0xFFFFD54F,
				0xFF5E1B5E, 0xFFFFF7ED, 0xFF26C6DA,
				0xFF3A163D, 0xFFFDF0E6, 0xFFFFB300, 0xFFFF9800));

		list.add(t(Category.FESTIVALS, "Thaipusam",
				0xFF24140B, 0xFFFFF3E0, 0xFFC7A489, 0xFFFFB300,
				0xFF6E260E, 0xFFFFF8E1, 0xFFFFD740,
				0xFF853112, 0xFFFFF8E1, 0xFFFFAB40,
				0xFF402112, 0xFFFFF3E0, 0xFFFFB300, 0xFFFFAB00));

		list.add(t(Category.FESTIVALS, "Christmas Eve",
				0xFF0F2117, 0xFFF2F7F2, 0xFF93B39F, 0xFFFF5252,
				0xFF164A2C, 0xFFFFFFFF, 0xFFFFD700,
				0xFF6E141B, 0xFFFFFFFF, 0xFFFFD54F,
				0xFF1A3826, 0xFFF2F7F2, 0xFFFF5252, 0xFFFF5252));

		list.add(t(Category.FESTIVALS, "White Christmas",
				0xFF141D26, 0xFFF4F8FC, 0xFF9BB0C4, 0xFFE6D095,
				0xFF1E2D3D, 0xFFFFFFFF, 0xFFFFE082,
				0xFF273A4F, 0xFFFFFFFF, 0xFFB3E5FC,
				0xFF253547, 0xFFF4F8FC, 0xFFE6D095, 0xFFFFD54F));

		list.add(t(Category.FESTIVALS, "Valentine's Romance",
				0xFF240D17, 0xFFFFECF2, 0xFFC799AB, 0xFFFF4081,
				0xFF5C1330, 0xFFFFF2F6, 0xFFFF80AB,
				0xFF73193D, 0xFFFFF2F6, 0xFFF8BBD0,
				0xFF3E1426, 0xFFFFECF2, 0xFFFF4081, 0xFFFF4081));

		list.add(t(Category.FESTIVALS, "Easter Pastel Dawn",
				0xFFF8F4FC, 0xFF2D223B, 0xFF6B5B7B, 0xFF7E57C2,
				0xFF6A4C93, 0xFFFFFFFF, 0xFFFFE082,
				0xFF563C7A, 0xFFFFFFFF, 0xFF80CBC4,
				0xFFFFFFFF, 0xFF2D223B, 0xFF6A4C93, 0xFF26A69A));

		list.add(t(Category.FESTIVALS, "Mid-Autumn • 中秋",
				0xFF111529, 0xFFF7F2E4, 0xFF98A0BF, 0xFFFFD54F,
				0xFF1B2342, 0xFFFFF8E8, 0xFFFFD54F,
				0xFF242F57, 0xFFFFF8E8, 0xFFFFCA28,
				0xFF222B4D, 0xFFF7F2E4, 0xFFFFD54F, 0xFFFFCA28));

		list.add(t(Category.FESTIVALS, "Dragon Boat • 端午",
				0xFF0E1F18, 0xFFEBF5EE, 0xFF8EB09F, 0xFF4CAF50,
				0xFF173D2D, 0xFFF4FAF6, 0xFFFF7043,
				0xFF1F4F3B, 0xFFF4FAF6, 0xFFFFCA28,
				0xFF1C382D, 0xFFEBF5EE, 0xFF66BB6A, 0xFFFF7043));

		list.add(t(Category.FESTIVALS, "Wesak Day",
				0xFF1B1626, 0xFFF8F2FA, 0xFFA89BBF, 0xFFFFB74D,
				0xFF33264A, 0xFFFFF8F0, 0xFFFFCA28,
				0xFF42315E, 0xFFFFF8F0, 0xFFF48FB1,
				0xFF2E2442, 0xFFF8F2FA, 0xFFFFB74D, 0xFFFFB300));

		list.add(t(Category.FESTIVALS, "Hari Merdeka",
				0xFF0B142B, 0xFFF2F5FA, 0xFF93A3C2, 0xFFFFCC00,
				0xFF0F265C, 0xFFFFFFFF, 0xFFFFCC00,
				0xFF7A121C, 0xFFFFFFFF, 0xFFFFD740,
				0xFF162447, 0xFFF2F5FA, 0xFFFFCC00, 0xFFFFCC00));

		list.add(t(Category.FESTIVALS, "Gawai & Kaamatan",
				0xFF1F1610, 0xFFFAF2E6, 0xFFB8A08A, 0xFFE6B33E,
				0xFF4A2511, 0xFFFFF7EB, 0xFFFFCA28,
				0xFF5E2F16, 0xFFFFF7EB, 0xFFFF7043,
				0xFF382216, 0xFFFAF2E6, 0xFFE6B33E, 0xFFE6B33E));

		list.add(t(Category.FESTIVALS, "Songkran Splash",
				0xFF0D2029, 0xFFEAF7FC, 0xFF88B0C2, 0xFF00E5FF,
				0xFF123B4D, 0xFFF2FBFF, 0xFF40E0D0,
				0xFF194E66, 0xFFF2FBFF, 0xFFFF80AB,
				0xFF173847, 0xFFEAF7FC, 0xFF00E5FF, 0xFF00E5FF));

		list.add(t(Category.FESTIVALS, "Holi Color Burst",
				0xFF1E1126, 0xFFFBF0FA, 0xFFB89BC7, 0xFFFF4081,
				0xFF3E1854, 0xFFFFFFFF, 0xFFFFD740,
				0xFF4E1F6B, 0xFFFFFFFF, 0xFF00E5FF,
				0xFF331945, 0xFFFBF0FA, 0xFFFF4081, 0xFFFF4081));

		list.add(t(Category.FESTIVALS, "Halloween Midnight",
				0xFF17111F, 0xFFF7EFE6, 0xFFA898B8, 0xFFFF7518,
				0xFF2B193D, 0xFFFFF5EB, 0xFFFF7518,
				0xFF38204F, 0xFFFFF5EB, 0xFF76FF03,
				0xFF291A38, 0xFFF7EFE6, 0xFFFF7518, 0xFFFF7518));

		list.add(t(Category.FESTIVALS, "Thanksgiving Harvest",
				0xFF211610, 0xFFFAF0E4, 0xFFB89E8A, 0xFFEA8C3A,
				0xFF472514, 0xFFFFF6EB, 0xFFFFB74D,
				0xFF5C311A, 0xFFFFF6EB, 0xFFE57373,
				0xFF3B2418, 0xFFFAF0E4, 0xFFEA8C3A, 0xFFEA8C3A));

		list.add(t(Category.FESTIVALS, "Sakura Hanami",
				0xFF1C1624, 0xFFFCEEF5, 0xFFB59CB5, 0xFFFFB7C5,
				0xFF2E223B, 0xFFFFF5FA, 0xFFFFB7C5,
				0xFF3D2D4F, 0xFFFFF5FA, 0xFFF48FB1,
				0xFF332642, 0xFFFCEEF5, 0xFFFFB7C5, 0xFFF48FB1));

		list.add(t(Category.FESTIVALS, "Oktoberfest Bavaria",
				0xFF101B29, 0xFFEEF5FA, 0xFF8FA8C2, 0xFF42A5F5,
				0xFF154378, 0xFFFFFFFF, 0xFFFFD54F,
				0xFF1D5494, 0xFFFFFFFF, 0xFF90CAF9,
				0xFF1B2E45, 0xFFEEF5FA, 0xFFFFD54F, 0xFF42A5F5));

		list.add(t(Category.FESTIVALS, "New Year's Eve Gala",
				0xFF101014, 0xFFFAF6EB, 0xFFA8A296, 0xFFE5C158,
				0xFF1D1D24, 0xFFFFFBF0, 0xFFFFD700,
				0xFF282833, 0xFFFFFBF0, 0xFFE5C158,
				0xFF22222B, 0xFFFAF6EB, 0xFFE5C158, 0xFFFFD700));

		// =====================================================================
		// 5. AUTOMOTIVE & MOTORSPORT LEGENDS (12)
		// =====================================================================
		list.add(t(Category.AUTOMOTIVE, "Ferrari Corsa Rosso",
				0xFF141010, 0xFFFAF2F2, 0xFFAD9999, 0xFFFF2800,
				0xFF9E0B0F, 0xFFFFFFFF, 0xFFFFF200,
				0xFF241C1C, 0xFFFFFFFF, 0xFFFF2800,
				0xFF261E1E, 0xFFFAF2F2, 0xFFFFF200, 0xFFFF2800));

		list.add(t(Category.AUTOMOTIVE, "British Racing Green",
				0xFF0B1C14, 0xFFF2EFE6, 0xFF96ADA0, 0xFFD4AF37,
				0xFF004225, 0xFFF8F5EC, 0xFFD4AF37,
				0xFF123626, 0xFFF8F5EC, 0xFFC89D66,
				0xFF152E22, 0xFFF2EFE6, 0xFFD4AF37, 0xFFD4AF37));

		list.add(t(Category.AUTOMOTIVE, "Porsche Miami Cyan",
				0xFF12181C, 0xFFEDF6FA, 0xFF8EA4B0, 0xFF00B4D8,
				0xFF1C262E, 0xFFF5FBFF, 0xFF00E5FF,
				0xFF24323D, 0xFFF5FBFF, 0xFF00B4D8,
				0xFF222E38, 0xFFEDF6FA, 0xFF00E5FF, 0xFF00C8E6));

		list.add(t(Category.AUTOMOTIVE, "Lamborghini Giallo",
				0xFF121212, 0xFFF5F5F0, 0xFFA3A399, 0xFFFFD600,
				0xFF1F1F1D, 0xFFFFFFFF, 0xFFFFD600,
				0xFF2B2B27, 0xFFFFFFFF, 0xFFFFEA00,
				0xFF262624, 0xFFF5F5F0, 0xFFFFD600, 0xFFFFD600));

		list.add(t(Category.AUTOMOTIVE, "BMW M Motorsport",
				0xFF0F131C, 0xFFEEF3FA, 0xFF8F9EB5, 0xFF009ADA,
				0xFF182238, 0xFFFFFFFF, 0xFF009ADA,
				0xFF212E4A, 0xFFFFFFFF, 0xFFE7222E,
				0xFF1D273D, 0xFFEEF3FA, 0xFF009ADA, 0xFF009ADA));

		list.add(t(Category.AUTOMOTIVE, "AMG Petronas Silver",
				0xFF101617, 0xFFEBF5F4, 0xFF8EA8A6, 0xFF00D2BE,
				0xFF1A2426, 0xFFFFFFFF, 0xFF00D2BE,
				0xFF243236, 0xFFFFFFFF, 0xFFC0C8CA,
				0xFF202D30, 0xFFEBF5F4, 0xFF00D2BE, 0xFF00D2BE));

		list.add(t(Category.AUTOMOTIVE, "McLaren Papaya",
				0xFF141518, 0xFFF5F5F7, 0xFF9EA2AB, 0xFFFF8000,
				0xFF212429, 0xFFFFFFFF, 0xFFFF8000,
				0xFF2C3038, 0xFFFFFFFF, 0xFF47C7FC,
				0xFF262A30, 0xFFF5F5F7, 0xFFFF8000, 0xFFFF8000));

		list.add(t(Category.AUTOMOTIVE, "Subaru Rally Blue",
				0xFF0B1426, 0xFFEDF3FA, 0xFF8FA3C4, 0xFFFFD700,
				0xFF0E2A66, 0xFFFFFFFF, 0xFFFFD700,
				0xFF153985, 0xFFFFFFFF, 0xFFFFE066,
				0xFF142447, 0xFFEDF3FA, 0xFFFFD700, 0xFFFFD700));

		list.add(t(Category.AUTOMOTIVE, "Aston Martin Heritage",
				0xFF0E1B1A, 0xFFEBF5F3, 0xFF8BA8A4, 0xFFCEDC00,
				0xFF143632, 0xFFF5FAF9, 0xFFCEDC00,
				0xFF1C4742, 0xFFF5FAF9, 0xFF80CBC4,
				0xFF1A302E, 0xFFEBF5F3, 0xFFCEDC00, 0xFFCEDC00));

		list.add(t(Category.AUTOMOTIVE, "Bugatti Atlantic",
				0xFF0B101C, 0xFFEDF3FC, 0xFF8C9BB8, 0xFF2979FF,
				0xFF122347, 0xFFFFFFFF, 0xFF448AFF,
				0xFF193061, 0xFFFFFFFF, 0xFFCFD8DC,
				0xFF16213B, 0xFFEDF3FC, 0xFF448AFF, 0xFF2979FF));

		list.add(t(Category.AUTOMOTIVE, "JDM Midnight Purple",
				0xFF130B1F, 0xFFF2EBFA, 0xFFA292BA, 0xFFBB86FC,
				0xFF281447, 0xFFF8F2FF, 0xFF00E5FF,
				0xFF341B5C, 0xFFF8F2FF, 0xFFD4A373,
				0xFF261740, 0xFFF2EBFA, 0xFFBB86FC, 0xFFBB86FC));

		list.add(t(Category.AUTOMOTIVE, "Carbon Fiber Stealth",
				0xFF0D0E10, 0xFFEAECEF, 0xFF8E939C, 0xFFB0BEC5,
				0xFF181A1F, 0xFFFFFFFF, 0xFFFF3D3D,
				0xFF22252B, 0xFFFFFFFF, 0xFFCFD8DC,
				0xFF1E2126, 0xFFEAECEF, 0xFFFF3D3D, 0xFFFF3D3D));

		// =====================================================================
		// 6. PERMATA GEMSTONES & LUXURY (10)
		// =====================================================================
		list.add(t(Category.GEMSTONES, "Permata Delima • Ruby",
				0xFF1C0A0F, 0xFFFAEBEF, 0xFFBA939D, 0xFFE0115F,
				0xFF420D1E, 0xFFFFF2F5, 0xFFFF6B9D,
				0xFF571228, 0xFFFFF2F5, 0xFFE6C384,
				0xFF33101B, 0xFFFAEBEF, 0xFFE0115F, 0xFFE0115F));

		list.add(t(Category.GEMSTONES, "Permata Nilam • Sapphire",
				0xFF0A1226, 0xFFEBF2FC, 0xFF8A9EC2, 0xFF4D88FF,
				0xFF112659, 0xFFF5F9FF, 0xFF82B1FF,
				0xFF183475, 0xFFF5F9FF, 0xFFCFD8DC,
				0xFF142245, 0xFFEBF2FC, 0xFF4D88FF, 0xFF4D88FF));

		list.add(t(Category.GEMSTONES, "Permata Zamrud • Emerald",
				0xFF091C14, 0xFFE8F7EF, 0xFF87B09B, 0xFF2ECC71,
				0xFF0F3B27, 0xFFF2FCF7, 0xFF50C878,
				0xFF164F35, 0xFFF2FCF7, 0xFFE6C97A,
				0xFF133324, 0xFFE8F7EF, 0xFF50C878, 0xFF2ECC71));

		list.add(t(Category.GEMSTONES, "Permata Berlian • Diamond",
				0xFF0B0E14, 0xFFF5F8FC, 0xFF9AA5B8, 0xFFE3F2FD,
				0xFF161C26, 0xFFFFFFFF, 0xFFB3E5FC,
				0xFF202938, 0xFFFFFFFF, 0xFFFFFFFF,
				0xFF1C2330, 0xFFF5F8FC, 0xFFB3E5FC, 0xFF90CAF9));

		list.add(t(Category.GEMSTONES, "Permata Jed • Jadeite",
				0xFF0D1E19, 0xFFEAF5F0, 0xFF8FB0A3, 0xFF00A86B,
				0xFF16382E, 0xFFF2FAF7, 0xFF52D1A0,
				0xFF1F4A3D, 0xFFF2FAF7, 0xFFD9B96A,
				0xFF1B362E, 0xFFEAF5F0, 0xFF52D1A0, 0xFF00C47D));

		list.add(t(Category.GEMSTONES, "Permata Kecubung • Amethyst",
				0xFF170F24, 0xFFF4ECFA, 0xFFA696BD, 0xFFB388FF,
				0xFF2D1B4A, 0xFFFAF5FF, 0xFFD1B3FF,
				0xFF3C2561, 0xFFFAF5FF, 0xFFE1BEE7,
				0xFF2A1D42, 0xFFF4ECFA, 0xFFB388FF, 0xFFB388FF));

		list.add(t(Category.GEMSTONES, "Imperial Topaz",
				0xFF1F150C, 0xFFFAF0E1, 0xFFB89E82, 0xFFFFB347,
				0xFF3B2512, 0xFFFFF7EB, 0xFFFFCA66,
				0xFF4F3219, 0xFFFFF7EB, 0xFFFFB347,
				0xFF362415, 0xFFFAF0E1, 0xFFFFB347, 0xFFFFA726));

		list.add(t(Category.GEMSTONES, "Black Opal",
				0xFF11131A, 0xFFEDF2FA, 0xFF929CB3, 0xFF18FFFF,
				0xFF1C202B, 0xFFF5F8FF, 0xFF18FFFF,
				0xFF262B3A, 0xFFF5F8FF, 0xFFFF80AB,
				0xFF222633, 0xFFEDF2FA, 0xFFB388FF, 0xFF18FFFF));

		list.add(t(Category.GEMSTONES, "Rose Gold Satin",
				0xFF1F171A, 0xFFFAF0F2, 0xFFB89EA5, 0xFFE09F9E,
				0xFF33252A, 0xFFFFF5F7, 0xFFE8B4B8,
				0xFF423037, 0xFFFFF5F7, 0xFFF3D5C0,
				0xFF38292E, 0xFFFAF0F2, 0xFFE09F9E, 0xFFE09F9E));

		list.add(t(Category.GEMSTONES, "Saddle Leather",
				0xFF1D130E, 0xFFF7EDE4, 0xFFB39B8B, 0xFFC68B59,
				0xFF3B2417, 0xFFFFF6EE, 0xFFD9A066,
				0xFF4F3120, 0xFFFFF6EE, 0xFFE6C28B,
				0xFF362218, 0xFFF7EDE4, 0xFFC68B59, 0xFFD99B52));

		// =====================================================================
		// 7. NATURE, COSMOS & LANDMARKS (10)
		// =====================================================================
		list.add(t(Category.NATURE, "Aurora Borealis",
				0xFF0B141E, 0xFFE8F7F4, 0xFF8AA8B5, 0xFF00E6A8,
				0xFF132436, 0xFFF2FCFA, 0xFF00E6A8,
				0xFF1C324A, 0xFFF2FCFA, 0xFFB388FF,
				0xFF182A3D, 0xFFE8F7F4, 0xFF00E6A8, 0xFF00E6A8));

		list.add(t(Category.NATURE, "Pacific Abyss",
				0xFF071521, 0xFFE6F4FA, 0xFF80A2BA, 0xFF00B8D4,
				0xFF0D253B, 0xFFF0F9FF, 0xFF26C6DA,
				0xFF133452, 0xFFF0F9FF, 0xFF80DEEA,
				0xFF112C45, 0xFFE6F4FA, 0xFF00B8D4, 0xFF00B8D4));

		list.add(t(Category.NATURE, "Sahara Dusk",
				0xFF211613, 0xFFFAF0E6, 0xFFB89E90, 0xFFEB9860,
				0xFF3B241E, 0xFFFFF6EE, 0xFFF2B880,
				0xFF4F3128, 0xFFFFF6EE, 0xFFE57373,
				0xFF382520, 0xFFFAF0E6, 0xFFEB9860, 0xFFEB9860));

		list.add(t(Category.NATURE, "Kyoto Bamboo",
				0xFF111C14, 0xFFEEF5EB, 0xFF93AA96, 0xFF8BC34A,
				0xFF1B2E20, 0xFFF5FAF2, 0xFF9CCC65,
				0xFF243D2B, 0xFFF5FAF2, 0xFFDCE775,
				0xFF213627, 0xFFEEF5EB, 0xFF8BC34A, 0xFF8BC34A));

		list.add(t(Category.NATURE, "Penang Peranakan",
				0xFF0E2124, 0xFFF5F2EB, 0xFF93B3B5, 0xFF26D0CE,
				0xFF154247, 0xFFFFFBF2, 0xFFFF7BAC,
				0xFF1E565C, 0xFFFFFBF2, 0xFFFFD166,
				0xFF193B40, 0xFFF5F2EB, 0xFF26D0CE, 0xFFFF7BAC));

		list.add(t(Category.NATURE, "Santorini Aegean",
				0xFF0D1A2B, 0xFFF2F7FC, 0xFF92AAC7, 0xFF4FC3F7,
				0xFF153563, 0xFFFFFFFF, 0xFF81D4FA,
				0xFF1D4680, 0xFFFFFFFF, 0xFFFFAB91,
				0xFF192E4D, 0xFFF2F7FC, 0xFF4FC3F7, 0xFF29B6F6));

		list.add(t(Category.NATURE, "Grand Canyon",
				0xFF211410, 0xFFFAEEE6, 0xFFB89C8E, 0xFFD96B43,
				0xFF3D2218, 0xFFFFF5EE, 0xFFE88A5E,
				0xFF522E21, 0xFFFFF5EE, 0xFFF0B67F,
				0xFF3A231B, 0xFFFAEEE6, 0xFFD96B43, 0xFFD96B43));

		list.add(t(Category.NATURE, "Milky Way Nebula",
				0xFF0E0D1F, 0xFFF0EEFA, 0xFF9694BA, 0xFFE066FF,
				0xFF1A183B, 0xFFF7F5FF, 0xFF64D2FF,
				0xFF242152, 0xFFF7F5FF, 0xFFFF70C8,
				0xFF1F1D42, 0xFFF0EEFA, 0xFF64D2FF, 0xFFE066FF));

		list.add(t(Category.NATURE, "Volcanic Magma",
				0xFF141010, 0xFFFAF0EC, 0xFFB09992, 0xFFFF5722,
				0xFF261816, 0xFFFFF5F0, 0xFFFF6D00,
				0xFF38201C, 0xFFFFF5F0, 0xFFFFAB00,
				0xFF2B1C1A, 0xFFFAF0EC, 0xFFFF5722, 0xFFFF5722));

		list.add(t(Category.NATURE, "Rainforest Mist",
				0xFF0E1D1A, 0xFFEAF5F2, 0xFF8CAEA6, 0xFF4DB6AC,
				0xFF17332D, 0xFFF2FAF8, 0xFF80CBC4,
				0xFF20453D, 0xFFF2FAF8, 0xFFA5D6A7,
				0xFF1C3832, 0xFFEAF5F2, 0xFF4DB6AC, 0xFF26A69A));

		// =====================================================================
		// 8. STUDIO, VIBES & DAYLIGHT (8)
		// =====================================================================
		list.add(t(Category.STUDIO_VIBES, "Cyberpunk 2077",
				0xFF0D0B14, 0xFFF5F7FA, 0xFF9A96B5, 0xFF00F0FF,
				0xFF1A1429, 0xFFFFFFFF, 0xFFFCEE0A,
				0xFF241C38, 0xFFFFFFFF, 0xFFFF007F,
				0xFF1F1830, 0xFFF5F7FA, 0xFF00F0FF, 0xFFFCEE0A));

		list.add(t(Category.STUDIO_VIBES, "Synthwave Outrun",
				0xFF170D26, 0xFFF7EEFA, 0xFFA894C2, 0xFFFF499E,
				0xFF2B1547, 0xFFFFFFFF, 0xFF00F5D4,
				0xFF3B1D61, 0xFFFFFFFF, 0xFFFF9E00,
				0xFF2A1845, 0xFFF7EEFA, 0xFFFF499E, 0xFFFF499E));

		list.add(t(Category.STUDIO_VIBES, "Lo-Fi Coffee",
				0xFF1C1613, 0xFFF2E8DF, 0xFFA8988C, 0xFFD4A373,
				0xFF2B221D, 0xFFFAF2EB, 0xFFE6CCB2,
				0xFF382C26, 0xFFFAF2EB, 0xFFD4A373,
				0xFF332822, 0xFFF2E8DF, 0xFFD4A373, 0xFFD4A373));

		list.add(t(Category.STUDIO_VIBES, "Vacuum Tube Hi-Fi",
				0xFF141312, 0xFFF5EFE8, 0xFFA69C92, 0xFFFF914D,
				0xFF22201E, 0xFFFAF5EE, 0xFFFFA94D,
				0xFF2E2B28, 0xFFFAF5EE, 0xFFFFB74D,
				0xFF292624, 0xFFF5EFE8, 0xFFFF914D, 0xFFFF8C42));

		list.add(t(Category.STUDIO_VIBES, "Vinyl Studio Console",
				0xFF15181A, 0xFFEDF0F2, 0xFF949DA3, 0xFFE6B84C,
				0xFF212629, 0xFFF5F7F8, 0xFFE6B84C,
				0xFF2C3338, 0xFFF5F7F8, 0xFF7CB342,
				0xFF272D30, 0xFFEDF0F2, 0xFFE6B84C, 0xFFE6B84C));

		list.add(t(Category.STUDIO_VIBES, "Matcha Zen",
				0xFF191F18, 0xFFEFF5EC, 0xFF9BA898, 0xFFA5C882,
				0xFF263024, 0xFFF5FAF2, 0xFFB8D898,
				0xFF324030, 0xFFF5FAF2, 0xFFE6D5B8,
				0xFF2D382B, 0xFFEFF5EC, 0xFFA5C882, 0xFFA5C882));

		list.add(t(Category.STUDIO_VIBES, "Lavender Cloud (Light)",
				0xFFF4F1FA, 0xFF1F1A30, 0xFF5D5378, 0xFF673AB7,
				0xFF4527A0, 0xFFFFFFFF, 0xFFE1BEE7,
				0xFF311B92, 0xFFFFFFFF, 0xFFD1C4E9,
				0xFFFFFFFF, 0xFF1F1A30, 0xFF5E35B1, 0xFF7C4DFF));

		list.add(t(Category.STUDIO_VIBES, "Daylight Ivory (Light)",
				0xFFF5F3EE, 0xFF1C2430, 0xFF5A6577, 0xFF2E5A88,
				0xFF1E3A5F, 0xFFF8FAFC, 0xFF93C5FD,
				0xFF152C49, 0xFFF8FAFC, 0xFF60A5FA,
				0xFFFFFFFF, 0xFF1C2430, 0xFF1E3A5F, 0xFF2563EB));

		ALL_ENTRIES = Collections.unmodifiableList(list);
		List<Palette> solarTerms = new ArrayList<>(24);
		for (Entry e : ALL_ENTRIES) {
			NAME_TO_CATEGORY.put(e.palette.name.toLowerCase(Locale.ROOT), e.category);
			if (e.category == Category.SOLAR_TERMS) {
				solarTerms.add(e.palette);
			}
		}
		SOLAR_TERM_PALETTES = Collections.unmodifiableList(solarTerms);
	}

	private CuratedThemeCatalog() {
	}

	private static Entry t(@NonNull Category category,
						   @NonNull String name,
						   @ColorInt int winBg,
						   @ColorInt int listText,
						   @ColorInt int listSub,
						   @ColorInt int listIcons,
						   @ColorInt int barsBg,
						   @ColorInt int barsText,
						   @ColorInt int barsIcons,
						   @ColorInt int ctrlBg,
						   @ColorInt int ctrlText,
						   @ColorInt int ctrlIcons,
						   @ColorInt int menuBg,
						   @ColorInt int menuText,
						   @ColorInt int menuIcons,
						   @ColorInt int accent) {
		return new Entry(category, new Palette(
				name,
				winBg, listText, listSub, listIcons,
				barsBg, barsText, barsIcons,
				ctrlBg, ctrlText, ctrlIcons,
				menuBg, menuText, menuIcons,
				accent
		));
	}

	public static int count() {
		return ALL_ENTRIES.size();
	}

	@NonNull
	public static List<Palette> getAllPalettes() {
		List<Palette> copies = new ArrayList<>(ALL_ENTRIES.size());
		for (Entry e : ALL_ENTRIES) {
			copies.add(e.palette.copy());
		}
		return copies;
	}

	@Nullable
	public static Category getCategoryForName(@Nullable String name) {
		if (name == null) return null;
		return NAME_TO_CATEGORY.get(name.trim().toLowerCase(Locale.ROOT));
	}

	public static boolean isCuratedName(@Nullable String name) {
		return getCategoryForName(name) != null;
	}

	/**
	 * Computes the active 24 Solar Term index (0 = 立春 at 315° through 23 = 大寒 at 300°)
	 * from the Sun's apparent ecliptic longitude (accurate to ~15 min, 1900–2100+).
	 */
	public static int getCurrentSolarTermIndex() {
		return getSolarTermIndex(System.currentTimeMillis());
	}

	public static int getSolarTermIndex(long utcMillis) {
		double jd = 2440587.5 + (utcMillis / 86400000.0);
		double n = jd - 2451545.0;
		double meanLon = normalizeDegrees(280.460 + 0.9856474 * n);
		double meanAnomalyRad = Math.toRadians(normalizeDegrees(357.528 + 0.9856003 * n));
		double eclipticLon = normalizeDegrees(
				meanLon
						+ 1.915 * Math.sin(meanAnomalyRad)
						+ 0.020 * Math.sin(2.0 * meanAnomalyRad)
		);
		double shifted = normalizeDegrees(eclipticLon - 315.0);
		return ((int) Math.floor(shifted / 15.0)) % SOLAR_TERM_PALETTES.size();
	}

	@NonNull
	public static Palette getSolarTermPalette(int index) {
		int safeIdx = ((index % SOLAR_TERM_PALETTES.size()) + SOLAR_TERM_PALETTES.size())
				% SOLAR_TERM_PALETTES.size();
		return SOLAR_TERM_PALETTES.get(safeIdx).copy();
	}

	@NonNull
	public static Palette getCurrentSolarTermPalette() {
		return getSolarTermPalette(getCurrentSolarTermIndex());
	}

	@NonNull
	public static String getCurrentSolarTermName() {
		return SOLAR_TERM_PALETTES.get(getCurrentSolarTermIndex()).name;
	}

	public static boolean isCurrentSolarTermName(@Nullable String name) {
		return name != null && getCurrentSolarTermName().equalsIgnoreCase(name.trim());
	}

	private static double normalizeDegrees(double deg) {
		double r = deg % 360.0;
		return (r < 0.0) ? (r + 360.0) : r;
	}
}