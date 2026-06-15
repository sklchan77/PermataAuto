package my.app.permata.ui.fragment;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static my.app.permata.BuildConfig.VERSION_CODE;
import static my.app.permata.BuildConfig.VERSION_NAME;
import static my.app.utils.collection.CollectionUtils.newLinkedHashSet;
import static my.app.utils.ui.UiUtils.isVisible;
import static my.app.utils.ui.UiUtils.showInfo;
import static my.app.utils.ui.view.NavBarItem.create;
import static my.app.utils.ui.view.NavBarView.POSITION_LEFT;
import static my.app.utils.ui.view.NavBarView.POSITION_RIGHT;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.AddonManager;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.PermataFragmentAddon;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.BodyLayout;
import my.app.permata.ui.view.ControlPanelView;
import my.app.permata.ui.view.MediaItemListView;
import my.app.permata.util.Utils;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.function.Supplier;
import my.app.utils.holder.IntHolder;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.fragment.GenericFragment;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.NavBarItem;
import my.app.utils.ui.view.NavBarView;
import my.app.utils.ui.view.NavButtonView;
import my.app.utils.ui.view.PrefNavBarMediator;
import my.app.utils.ui.view.ScalableTextView;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
public class NavBarMediator extends PrefNavBarMediator
		implements AddonManager.Listener, OverlayMenu.SelectionHandler {
	private static final Pref<Supplier<String[]>> PREF_B =
			Pref.sa("NAV_BAR_ITEMS_B", (String[]) null);
	private static final Pref<Supplier<String[]>> PREF_L =
			Pref.sa("NAV_BAR_ITEMS_L", (String[]) null);
	private static final Pref<Supplier<String[]>> PREF_R =
			Pref.sa("NAV_BAR_ITEMS_R", (String[]) null);

	@Override
	protected Collection<NavBarItem> getItems(NavBarView nb) {
		int max = nb.suggestItemCount() - 1;
		Collection<String> names = getLayout(nb);
		List<NavBarItem> items = new ArrayList<>(names.size());
		AddonManager amgr = getAddonManager();
		Context ctx = nb.getContext();

		for (String name : names) {
			switch (name) {
				case "folders":
					items.add(
							create(ctx, R.id.folders_fragment, my.app.utils.R.drawable.folder, R.string.folders,
									items.size() < max));
					continue;
				case "favorites":
					items.add(
							create(ctx, R.id.favorites_fragment, R.drawable.favorite_filled, R.string.favorites,
									items.size() < max));
					continue;
				case "playlists":
					items.add(create(ctx, R.id.playlists_fragment, R.drawable.playlist, R.string.playlists,
							items.size() < max));
					continue;
				case "menu":
					items.add(create(ctx, R.id.menu, my.app.utils.R.drawable.menu, R.string.menu,
							items.size() < max));
					continue;
			}

			PermataAddon a = amgr.getAddon(name);
			if (a instanceof PermataFragmentAddon) {
				AddonInfo ai = a.getInfo();
				items.add(create(ctx, a.getAddonId(), ai.icon, ai.addonName, items.size() < max));
				continue;
			}
			Log.e("Unknown NavBarItem name: ", name);
		}

		return items;
	}

	@Override
	protected boolean canSwap(NavBarView nb) {
		return true;
	}

	@Override
	protected boolean swap(NavBarView nb, @IdRes int id1, @IdRes int id2) {
		List<String> names = new ArrayList<>(getLayout(nb));
		String name1 = idToName(id1);
		String name2 = idToName(id2);
		int idx1 = names.indexOf(name1);
		int idx2 = names.indexOf(name2);

		if ((idx1 != -1) && (idx2 != -1)) {
			Collections.swap(names, idx1, idx2);
			getPreferenceStore(nb).applyStringArrayPref(getPref(nb), names.toArray(new String[0]));
			return true;
		} else {
			Log.e("Unable to swap ", name1, " and ", name2);
			return false;
		}
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		PermataApplication.get().getAddonManager().addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		PermataApplication.get().getAddonManager().removeBroadcastListener(this);
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		NavBarView nb = navBar;
		if (nb != null) reload(nb);
	}

	@Override
	protected PreferenceStore getPreferenceStore(NavBarView nb) {
		return MainActivityDelegate.get(nb.getContext()).getPrefs();
	}

	@Override
	protected Pref<Supplier<String[]>> getPref(NavBarView nb) {
		switch (nb.getPosition()) {
			default:
				return PREF_B;
			case POSITION_LEFT:
				return PREF_L;
			case POSITION_RIGHT:
				return PREF_R;
		}
	}

	@Override
	public void itemSelected(View item, int id, ActivityDelegate a) {
		if (id == R.id.menu) {
			showMenu(MainActivityDelegate.get(item.getContext()));
		} else {
			super.itemSelected(item, id, a);
		}
	}

	@Override
	protected boolean extItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.menu) {
			NavButtonView.Ext ext = getExtButton();

			if ((ext != null) && !ext.isSelected()) {
				NavBarItem i = item.getData();
				setExtButton(null, i);
			}

			showMenu(MainActivityDelegate.get(item.getContext()));
			return true;
		} else {
			return super.extItemSelected(item);
		}
	}

	@Override
	public void itemReselected(View item, int id, ActivityDelegate a) {
		BodyLayout b = ((MainActivityDelegate) a).getBody();
		if (b.isVideoMode()) b.setMode(BodyLayout.Mode.BOTH);
		else super.itemReselected(item, id, a);
	}

	@Nullable
	@Override
	public View focusSearch(NavBarView nb, View focused, int direction) {
		if (direction == FOCUS_UP) {
			if (!nb.isBottom()) return null;
			Context ctx = nb.getContext();
			ControlPanelView p = MainActivityDelegate.get(ctx).getControlPanel();
			return isVisible(p) ? p.focusSearch() : MediaItemListView.focusSearchLast(ctx, focused);
		} else if (direction == FOCUS_DOWN) {
			if (!nb.isBottom()) return null;
			Context ctx = nb.getContext();
			ToolBarView tb = MainActivityDelegate.get(ctx).getToolBar();
			if (isVisible(tb)) return tb.focusSearch();
		} else if (direction == FOCUS_RIGHT) {
			if (nb.isLeft()) return MediaItemListView.focusSearchActive(nb.getContext(), focused);
		} else if (direction == FOCUS_LEFT) {
			if (nb.isRight()) return MediaItemListView.focusSearchActive(nb.getContext(), focused);
		}

		return null;
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu(MainActivityDelegate.get(nb.getContext()));
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.show(b -> {
			b.setSelectionHandler(this);

			if (a.hasCurrent())
				b.addItem(R.id.nav_got_to_current, R.drawable.go_to_current, R.string.got_to_current);

			ActivityFragment f = a.getActiveFragment();
			if (f instanceof MainActivityFragment) ((MainActivityFragment) f).contributeToNavBarMenu(b);

			b.addItem(R.id.nav_about, R.drawable.about, R.string.about);
			b.addItem(R.id.settings_fragment, R.drawable.settings, R.string.settings);
			if (!a.isCarActivityNotMirror()) b.addItem(R.id.nav_exit, R.drawable.exit, R.string.exit);

			if (BuildConfig.AUTO) b.addItem(R.id.nav_donate, R.drawable.coffee, R.string.donate);
		});
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.nav_got_to_current) {
			MainActivityDelegate.get(item.getContext()).goToCurrent();
			return true;
		} else if (itemId == R.id.nav_about) {
			MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
			if (!(a.showFragment(my.app.utils.R.id.generic_fragment) instanceof GenericFragment f))
				return false;
			f.setTitle(item.getContext().getString(R.string.about));
			f.setContentProvider(g -> {
				Context ctx = g.getContext();
				ScalableTextView v = new ScalableTextView(ctx);
				String url = "https://github.com/sklchan77/Permata";
				String html = ctx.getString(R.string.about_html, VERSION_NAME, VERSION_CODE, url);
				int pad = UiUtils.toIntPx(ctx, 10);
				v.setPadding(pad, pad, pad, pad);
				v.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY));
				v.setOnClickListener(t -> openUrl(t.getContext(), url));
				g.addView(v);
			});
			return true;
		} else if (itemId == R.id.settings_fragment) {
			MainActivityDelegate.get(item.getContext()).showFragment(R.id.settings_fragment);
			return true;
		} else if (itemId == R.id.nav_exit) {
			MainActivityDelegate.get(item.getContext()).finish();
			return true;
		}
		MainActivityDelegate a;
		if (BuildConfig.AUTO && (item.getItemId() == R.id.nav_donate)) {
			Context ctx = item.getContext();
			a = MainActivityDelegate.get(ctx);

			DialogInterface.OnClickListener ok = (d, i) -> {
				IntHolder selection = new IntHolder();
				String[] wallets = new String[]{"PayPal", "CloudTips", "Yandex",};
				String[] urls =
						new String[]{"https://www.paypal.com/donate/?hosted_button_id=NP5Q3YDSCJ98N",
								"https://pay.cloudtips.ru/p/a03a73da", "https://yoomoney.ru/to/410014661137336"};

				a.createDialogBuilder().setTitle(R.drawable.coffee, R.string.donate)
						.setSingleChoiceItems(wallets, 0, (dlg, which) -> selection.value = which)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, (d1, w1) -> openUrl(ctx,
								urls[selection.value]))
						.show();
			};

			a.createDialogBuilder().setTitle(R.drawable.coffee, R.string.donate)
					.setMessage(R.string.donate_text).setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, ok).show();

			return true;
		}

		return false;
	}

	private static void openUrl(Context ctx, String url) {
		if (!Utils.openUrl(ctx, url)) showInfo(ctx, R.string.use_phone_for_donation);
	}

	private Collection<String> getLayout(NavBarView nb) {
		AddonManager amgr = PermataApplication.get().getAddonManager();
		Set<String> names = newLinkedHashSet(BuildConfig.ADDONS.length + 4);
		String[] pref = getPreferenceStore(nb).getStringArrayPref(getPref(nb));
		CollectionUtils.addAll(names, pref);
		names.add("folders");
		names.add("favorites");
		names.add("playlists");
		for (AddonInfo ai : BuildConfig.ADDONS) {
			PermataAddon a = amgr.getAddon(ai.className);
			if (a instanceof PermataFragmentAddon) names.add(ai.className);
		}
		names.add("menu");
		return names;
	}

	private static String idToName(@IdRes int id) {
		if (id == R.id.folders_fragment) return "folders";
		else if (id == R.id.favorites_fragment) return "favorites";
		else if (id == R.id.playlists_fragment) return "playlists";
		else if (id == R.id.menu) return "menu";

		AddonManager amgr = getAddonManager();
		for (AddonInfo ai : BuildConfig.ADDONS) {
			PermataAddon a = amgr.getAddon(ai.className);
			if ((a instanceof PermataFragmentAddon) && (a.getAddonId() == id)) return ai.className;
		}

		Log.e("Unknown NavBarItem id: ", id);
		return String.valueOf(id);
	}

	private static AddonManager getAddonManager() {
		return PermataApplication.get().getAddonManager();
	}
}
