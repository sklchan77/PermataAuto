package my.app.permata.addon.felex.view;

import static android.view.View.GONE;
import static java.util.Objects.requireNonNull;
import static my.app.permata.addon.felex.FelexAddon.CACHE_FOLDER;
import static my.app.permata.addon.felex.FelexAddon.DICT_FOLDER;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.collection.CollectionUtils.comparing;
import static my.app.utils.collection.CollectionUtils.contains;
import static my.app.utils.text.TextUtils.isNullOrBlank;
import static my.app.utils.ui.UiUtils.queryPrefs;
import static my.app.utils.ui.UiUtils.queryText;
import static my.app.utils.ui.UiUtils.showAlert;
import static my.app.utils.ui.UiUtils.showQuestion;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import my.app.permata.PermataApplication;
import my.app.permata.addon.felex.FelexAddon;
import my.app.permata.addon.felex.R;
import my.app.permata.addon.felex.dict.Dict;
import my.app.permata.addon.felex.dict.DictInfo;
import my.app.permata.addon.felex.dict.DictMgr;
import my.app.permata.addon.felex.dict.Example;
import my.app.permata.addon.felex.dict.Translation;
import my.app.permata.addon.felex.dict.Word;
import my.app.permata.addon.felex.media.FelexItem;
import my.app.permata.addon.felex.tutor.DictTutor;
import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityListener;
import my.app.permata.ui.activity.MainActivityPrefs;
import my.app.permata.ui.fragment.FloatingButtonMediator;
import my.app.permata.ui.fragment.MainActivityFragment;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.function.BooleanConsumer;
import my.app.utils.function.IntSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.FloatingButton;
import my.app.utils.ui.view.ToolBarView;
import my.app.utils.voice.TextToSpeech;

/**
 * @author sklchan77
 */
public class FelexFragment extends MainActivityFragment
		implements MainActivityListener, PreferenceStore.Listener, ToolBarView.Listener {
	private Uri importDict;

	@Override
	public int getFragmentId() {
		return my.app.permata.R.id.felex_fragment;
	}

	@Override
	public boolean onBackPressed() {
		return view().onBackPressed();
	}

	@Override
	public boolean isRootPage() {
		return view().isRoot();
	}

	@Override
	public boolean isVideoModeSupported() {
		return true;
	}

	@Override
	public CharSequence getTitle() {
		return view().getTitle();
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FbMediator.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		activity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getPrefs().addBroadcastListener(this);
			a.getToolBar().addBroadcastListener(this);
			PermataApplication.get().getPreferenceStore().addBroadcastListener(this);
		});
		return inflater.inflate(R.layout.felex_list, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (importDict()) return;
		getActivityDelegate().getLib().getLastPlayedItem().onSuccess(i -> {
			if (i instanceof FelexItem.Tutor t) {
				view().setContent(t.getParent().getDict());
			}
		});
	}

	@Override
	public void onDestroyView() {
		view().close();
		activity().onSuccess(this::cleanUp);
		super.onDestroyView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
	}

	public boolean canScrollUp() {
		FelexListView v = view();
		Object content = v.getContent();
		if (!(content instanceof DictMgr) && !(content instanceof Dict)) return true;
		return (v.getScrollY() > 0);
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		refresh(view().getContent()).onCompletion((r, f) -> refreshing.accept(false));
	}

	private FutureSupplier<?> refresh(Object content) {
		if (content instanceof DictMgr) {
			return ((DictMgr) content).reset().thenRun(() -> view().refresh(0));
		} else if (content instanceof Dict) {
			return ((Dict) content).reset().thenRun(() -> view().refresh(0));
		} else {
			return completedVoid();
		}
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		if ((getCurrentDict() == null)) return;
		OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
		b.addItem(R.id.start_tutor, my.app.permata.R.drawable.record_voice, R.string.start_tutor)
				.setSubmenu(this::buildTutorMenu);

		Object content = view().getContent();

		if ((content instanceof DictMgr) || (content instanceof Dict)) {
			b.addItem(my.app.permata.R.id.refresh, my.app.permata.R.drawable.refresh,
					my.app.permata.R.string.refresh).setData(content);
		}

		super.contributeToNavBarMenu(b);
	}

	@Nullable
	private Dict getCurrentDict() {
		return (getView() instanceof FelexListView v) ? v.getCurrentDict() : null;
	}

	private void buildTutorMenu(OverlayMenu.Builder sb) {
		sb.setSelectionHandler(this::navBarMenuItemSelected);
		sb.addItem(R.id.start_tutor_dir, R.string.start_tutor_dir);
		sb.addItem(R.id.start_tutor_rev, R.string.start_tutor_rev);
		sb.addItem(R.id.start_tutor_mix, R.string.start_tutor_mix);
		sb.addItem(R.id.start_tutor_listen, R.string.start_tutor_listen);
		sb.addItem(R.id.offline_mode, R.string.offline_mode)
				.setChecked(FelexAddon.get().isOfflineMode());
	}

	/**
	 * @noinspection SameReturnValue
	 */
	private boolean navBarMenuItemSelected(OverlayMenuItem item) {
		var d = getCurrentDict();
		if (d == null) return true;
		int id = item.getItemId();

		if (id == R.id.start_tutor_dir) {
			startTutor(d, DictTutor.Mode.DIRECT);
		} else if (id == R.id.start_tutor_rev) {
			startTutor(d, DictTutor.Mode.REVERSE);
		} else if (id == R.id.start_tutor_mix) {
			startTutor(d, DictTutor.Mode.MIXED);
		} else if (id == R.id.start_tutor_listen) {
			startTutor(d, DictTutor.Mode.LISTENING);
		} else if (id == R.id.offline_mode) {
			var addon = FelexAddon.get();
			addon.setOfflineMode(!addon.isOfflineMode());
		} else if (id == my.app.permata.R.id.refresh) {
			refresh(item.getData());
		}

		return true;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		activity().onSuccess(a -> {
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
				View v = getView();
				if (v instanceof FelexListView) ((FelexListView) v).scale(a.getTextIconSize());
			} else if (prefs.contains(DICT_FOLDER) || prefs.contains(CACHE_FOLDER)) {
				view().onFolderChanged();
			}
		});
	}

	@Override
	public void onToolBarEvent(ToolBarView tb, byte event) {
		if (event == FILTER_CHANGED) {
			view().setFilter(tb.getFilter().getText().toString());
		}
	}

	@Override
	public void setInput(Object input) {
		if (!(input instanceof Uri)) return;
		importDict = (Uri) input;
		if (getView() != null) importDict();
	}

	private boolean importDict() {
		if (importDict == null) return false;
		Uri uri = importDict;
		importDict = null;
		Context ctx = requireContext();
		App.get().execute(() -> {
			try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
				return DictInfo.read(in);
			}
		}).main().onFailure(err -> {
			Log.e(err);
			showAlert(ctx, ctx.getString(R.string.err_invalid_dict_hdr, uri));
		}).then(info -> {
			String title = ctx.getString(R.string.import_dict);
			String msg = ctx.getString(R.string.import_dict_q, info);
			Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.dictionary);
			return showQuestion(ctx, title, msg, icon).then(
					v -> DictMgr.get().getDictionary(info.getPath()).then(d -> {
						if (d == null) {
							return DictMgr.get().createDictionary(info);
						} else if (d.getSourceLang().equals(info.getSourceLang()) &&
								d.getTargetLang().equals(info.getTargetLang())) {
							return showQuestion(ctx, title, ctx.getString(R.string.import_dict_exist, d),
									icon).then(ok -> completed(d), cancel -> createDict(info));
						} else {
							return createDict(info);
						}
					}));
		}).then(d -> d.addWords(uri)).onSuccess(view()::setContent);
		return true;
	}

	private FutureSupplier<Dict> createDict(DictInfo i) {
		return queryText(requireContext(), R.string.dict_name, R.drawable.dictionary).then(
				name -> DictMgr.get().createDictionary(i.rename(name)));
	}

	@NonNull
	private FelexListView view() {
		return (FelexListView) requireView();
	}

	private void startTutor(Dict d, DictTutor.Mode mode) {
		var a = getActivityDelegate();
		var lib = (DefaultMediaLib) a.getLib();
		a.getMediaServiceBinder().playItem(FelexItem.Tutor.create(lib, d, mode));
	}

	private void cleanUp(MainActivityDelegate a) {
		a.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		a.getToolBar().removeBroadcastListener(this);
		PermataApplication.get().getPreferenceStore().removeBroadcastListener(this);
	}

	private FutureSupplier<MainActivityDelegate> activity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	private static final class ToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
		static final ToolBarView.Mediator instance = new ToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			BackTitleFilter.super.enable(tb, f);
			addButton(tb, my.app.permata.R.drawable.playlist_add, ToolBarMediator::add, R.id.add);
			addButton(tb, my.app.permata.R.drawable.record_voice, ToolBarMediator::tutor,
					R.id.start_tutor);
			setButtonsVisibility(tb, f);
		}

		@Override
		public void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			ToolBarView.Mediator.BackTitleFilter.super.onActivityEvent(tb, a, e);
			if ((e == FRAGMENT_CHANGED) || e == FRAGMENT_CONTENT_CHANGED) {
				setButtonsVisibility(tb, a.getActiveFragment());
			}
		}

		private void setButtonsVisibility(ToolBarView tb, ActivityFragment f) {
			if (!(f instanceof FelexFragment ff)) return;
			Object content = ff.view().getContent();
			int add;
			int start;
			if (content instanceof Dict) {
				add = start = View.VISIBLE;
			} else if ((content instanceof DictMgr) || (content instanceof Word) ||
					(content instanceof Translation)) {
				add = View.VISIBLE;
				start = GONE;
			} else {
				add = start = GONE;
			}
			tb.findViewById(R.id.add).setVisibility(add);
			tb.findViewById(R.id.start_tutor).setVisibility(start);
		}

		private static void add(View v) {
			MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
			ActivityFragment f = a.getActiveFragment();
			if (!(f instanceof FelexFragment ff)) return;
			FelexListView lv = ff.view();
			Object content = lv.getContent();

			if (content instanceof DictMgr) {
				addDict(ff, (DictMgr) content);
			} else if (content instanceof Dict) {
				addWord(ff, (Dict) content);
			} else if (content instanceof Word) {
				addTrans(ff, requireNonNull(lv.getCurrentDict()), (Word) content);
			} else if (content instanceof Translation) {
				addExample(ff, requireNonNull(lv.getCurrentDict()), requireNonNull(lv.getCurrentWord()),
						(Translation) content);
			}
		}

		private static void addDict(FelexFragment ff, DictMgr mgr) {
			Context ctx = ff.requireContext();
			TextToSpeech.create(ctx).onSuccess(tts -> {
				List<Locale> locales = new ArrayList<>(tts.getAvailableLanguages());
				tts.close();

				if (locales.isEmpty()) {
					showAlert(ctx, R.string.no_lang_supported).thenRun(
							() -> TextToSpeech.installTtsData(ctx));
					return;
				}

				Collections.sort(locales, comparing(Locale::getDisplayName));
				String[] langs = new String[locales.size()];
				Locale defaultLang = Locale.getDefault();
				int defaultLangIdx = 0;

				for (int i = 0; i < langs.length; i++) {
					Locale l = locales.get(i);
					langs[i] = l.getDisplayName();
					if (l.equals(defaultLang)) defaultLangIdx = i;
				}

				Pref<Supplier<String>> namePref = Pref.s("NAME", "");
				Pref<IntSupplier> srcLangPref = Pref.i("SRC_LANG", defaultLangIdx);
				Pref<IntSupplier> targetLangPref = Pref.i("TARGET_LANG", defaultLangIdx);
				Pref<Supplier<String>> ackPref = Pref.s("ACK", "");
				Pref<Supplier<String>> skipPref = Pref.s("SKIP", "");
				queryPrefs(ctx, R.string.add_dict, (store, set) -> {
					set.addStringPref(o -> {
						o.pref = namePref;
						o.title = R.string.dict_name;
						o.store = store;
					});
					set.addListPref(o -> {
						o.pref = srcLangPref;
						o.title = R.string.src_lang;
						o.store = store;
						o.stringValues = langs;
						o.formatSubtitle = true;
						o.subtitle = my.app.permata.R.string.string_format;
					});
					set.addListPref(o -> {
						o.pref = targetLangPref;
						o.title = R.string.target_lang;
						o.store = store;
						o.stringValues = langs;
						o.formatSubtitle = true;
						o.subtitle = my.app.permata.R.string.string_format;
					});
					set.addStringPref(o -> {
						o.pref = ackPref;
						o.title = R.string.ack_phrase;
						o.hint = R.string.ack_phrase_sub;
						o.store = store;
					});
					set.addStringPref(o -> {
						o.pref = skipPref;
						o.title = R.string.skip_phrase;
						o.hint = R.string.skip_phrase_sub;
						o.store = store;
					});
				}, p -> {
					String name = p.getStringPref(namePref);
					if (isNullOrBlank(name)) return false;
					List<Dict> dicts = mgr.getDictionaries().peek();
					return (dicts == null) || !contains(dicts, d -> d.getName().equalsIgnoreCase(name));
				}).onSuccess(p -> {
					String name = p.getStringPref(namePref);
					String ackPhrase = p.getStringPref(ackPref);
					String skipPhrase = p.getStringPref(skipPref);
					if (isNullOrBlank(name)) return;
					int srcLangIdx = p.getIntPref(srcLangPref);
					int targetLangIdx = p.getIntPref(targetLangPref);
					mgr.createDictionary(name, locales.get(srcLangIdx), locales.get(targetLangIdx),
							ackPhrase, skipPhrase).main().onCompletion((d, err) -> {
						if (err != null) {
							Log.e(err);
							showAlert(ctx, err.toString());
						} else {
							ff.view().setContent(d);
						}
					});
				});
			});
		}

		private static void addWord(FelexFragment ff, Dict d) {
			Pref<Supplier<String>> wordPref = Pref.s("WORD");
			Pref<Supplier<String>> transPref = Pref.s("TRANS");
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			queryPrefs(ff.getContext(), R.string.add_word, (store, set) -> {
				set.addStringPref(o -> {
					o.pref = wordPref;
					o.title = R.string.word;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = transPref;
					o.title = R.string.trans;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = exPref;
					o.title = R.string.example;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = exTransPref;
					o.title = R.string.example_trans;
					o.store = store;
				});
			}, p -> {
				if (isNullOrBlank(p.getStringPref(transPref))) return false;
				String word = p.getStringPref(wordPref);
				if (isNullOrBlank(word)) return false;
				Boolean exist = d.hasWord(word).peek();
				return (exist == null) || !exist;
			}).onSuccess(p -> {
				String word = p.getStringPref(wordPref);
				String trans = p.getStringPref(transPref);
				String ex = p.getStringPref(exPref);
				String exTrans = p.getStringPref(exTransPref);
				d.addWord(word, trans, ex, exTrans).main().onCompletion((idx, err) -> {
					if (err != null) {
						Log.e(err);
						showAlert(ff.getContext(), err.toString());
					} else {
						ff.view().refresh(idx);
					}
				});
			});
		}

		private static void addTrans(FelexFragment ff, Dict d, Word w) {
			Pref<Supplier<String>> transPref = Pref.s("TRANS");
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			w.getTranslations(d)
					.then(translations -> queryPrefs(ff.getContext(), R.string.add_trans, (store, set) -> {
						set.addStringPref(o -> {
							o.pref = transPref;
							o.title = R.string.trans;
							o.store = store;
						});
						set.addStringPref(o -> {
							o.pref = exPref;
							o.title = R.string.example;
							o.store = store;
						});
						set.addStringPref(o -> {
							o.pref = exTransPref;
							o.title = R.string.example_trans;
							o.store = store;
						});
					}, p -> {
						String trans = p.getStringPref(transPref);
						if (isNullOrBlank(trans)) return false;
						return !CollectionUtils.contains(translations,
								t -> trans.equalsIgnoreCase(t.getTranslation()));
					}).onSuccess(p -> {
						String trans = p.getStringPref(transPref);
						String ex = p.getStringPref(exPref);
						String exTrans = p.getStringPref(exTransPref);
						List<Translation> newTrans = new ArrayList<>(translations.size() + 1);
						newTrans.addAll(translations);
						newTrans.add(new Translation(trans, ex, exTrans));
						w.setTranslations(d, newTrans).onCompletion((v, err) -> {
							if (err != null) {
								Log.e(err);
								showAlert(ff.getContext(), err.toString());
							} else {
								ff.view().refresh(newTrans.size() - 1);
							}
						});
					}));
		}

		private static void addExample(FelexFragment ff, Dict d, Word w, Translation tr) {
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			w.getTranslations(d).then(translations -> {
				int idx = CollectionUtils.indexOf(translations,
						t -> t.getTranslation().equalsIgnoreCase(tr.getTranslation()));
				if (idx == -1) return completedNull();
				translations.set(idx, tr);
				return queryPrefs(ff.getContext(), R.string.add_example, (store, set) -> {
					set.addStringPref(o -> {
						o.pref = exPref;
						o.title = R.string.example;
						o.store = store;
					});
					set.addStringPref(o -> {
						o.pref = exTransPref;
						o.title = R.string.example_trans;
						o.store = store;
					});
				}, p -> {
					String ex = p.getStringPref(exPref);
					if (isNullOrBlank(ex)) return false;
					return !CollectionUtils.contains(tr.getExamples(),
							e -> ex.equalsIgnoreCase(e.getSentence()));
				}).onSuccess(p -> {
					String ex = p.getStringPref(exPref);
					String exTrans = p.getStringPref(exTransPref);
					List<Example> examples = tr.getExamples();
					List<Example> newExamples = new ArrayList<>(examples.size() + 1);
					newExamples.addAll(examples);
					newExamples.add(new Example(ex, exTrans));
					tr.setExamples(newExamples);
					w.setTranslations(d, translations).onCompletion((v, err) -> {
						if (err != null) {
							Log.e(err);
							showAlert(ff.getContext(), err.toString());
						} else {
							ff.view().refresh(newExamples.size() - 1);
						}
					});
				});
			});
		}

		private static void tutor(View v) {
			MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
			ActivityFragment f = a.getActiveFragment();
			if (!(f instanceof FelexFragment ff)) return;
			a.getToolBarMenu().show(ff::buildTutorMenu);
		}
	}

	private static final class FbMediator extends FloatingButtonMediator {
		static final FbMediator instance = new FbMediator();

		@Override
		public boolean onLongClick(View v) {
			MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
			if (a.getActiveFragment() instanceof FelexFragment f) {
				if (f.view().getContent() instanceof Dict d) {
					f.startTutor(d, DictTutor.Mode.DIRECT);
					return true;
				}
			}
			return super.onLongClick(v);
		}
	}
}
