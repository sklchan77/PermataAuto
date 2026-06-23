package my.app.permata.media.engine;

import static android.media.session.PlaybackState.STATE_PAUSED;
import static android.media.session.PlaybackState.STATE_PLAYING;
import static android.media.session.PlaybackState.STATE_STOPPED;
import static my.app.permata.media.sub.SubGrid.Position.BOTTOM_CENTER;
import static my.app.permata.media.sub.SubGrid.Position.BOTTOM_LEFT;
import static my.app.permata.media.sub.SubGrid.Position.BOTTOM_RIGHT;
import static my.app.utils.async.Completed.cancelled;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedEmptyList;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.collection.CollectionUtils.comparing;
import static my.app.utils.text.TextUtils.timeToString;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import my.app.permata.BuildConfig;
import my.app.permata.addon.SubGenAddon;
import my.app.permata.media.sub.FileSubtitles;
import my.app.permata.media.sub.SubGrid;
import my.app.permata.media.sub.SubScheduler;
import my.app.permata.media.sub.Subtitles;
import my.app.permata.ui.view.VideoView;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.BiConsumer;
import my.app.utils.log.Log;
import my.app.utils.vfs.VirtualFile;
import my.app.utils.vfs.VirtualResource;

/** 
 * Enterprise-Grade Thread-Safe Abstraction Base Engine for Permata Auto.
 * Re-engineered with atomic state mapping models, defensive bounds tracking,
 * and fluent Java language enhancements. 
 * 
 * @author sklchan77 
 */ 
public abstract class MediaEngineBase implements MediaEngine {
	protected final Listener listener; 
	@Nullable protected VideoView videoView;
	
	// Synchronization locks safeguard internal state state-machine updates
	protected final Object engineLock = new Object(); 
	private int state = STATE_STOPPED; 
	private SubMgr subMgr;

	protected MediaEngineBase(@NonNull Listener listener) { 
		this.listener = listener; 
	}
	
	@CallSuper 
	@Override 
	public void setVideoView(@Nullable VideoView view) { 
		synchronized (engineLock) { 
			if (subMgr != null && videoView != null) {
				subMgr.removeSubtitleConsumer(videoView); 
			} 
			videoView = view; 
			if (view == null) return;
			
			view.clearSubtitleSurface(); 
			if (subMgr != null) { 
				subMgr.addSubtitleConsumer(view);
			} else { 
				selectSubtitleStream(); 
			} 
		} 
	}


	protected boolean isPlaying() {
		synchronized (engineLock) {
			return state == STATE_PLAYING;
		}
	}

	protected boolean isPaused() {
		synchronized (engineLock) {
			return state == STATE_PAUSED;
		}
	}

	protected int getEngineState() {
		synchronized (engineLock) {
			return state;
		}
	}

	protected void setEngineState(int newState) {
		synchronized (engineLock) {
			this.state = newState;
		}
	}

	protected FutureSupplier<Long> getSubtitlePosition() {
		return getPosition();
	}

	protected long subSchedulerClock() {
		return Optional.ofNullable(getSubtitlePosition())
				.filter(FutureSupplier::isDoneNotFailed)
				.map(FutureSupplier::getOrThrow)
				.orElseGet(System::currentTimeMillis);
	}

	@Override
	public int getSubtitleDelay() {
		synchronized (engineLock) {
			return subMgr == null ? 0 : subMgr.getSubtitleDelay();
		}
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		synchronized (engineLock) {
			if (milliseconds == 0 && subMgr == null) return;
			sub().setSubtitleDelay(milliseconds);
		}
	}

	@Override
	public boolean isSubtitlesSupported() {
		return true;
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		synchronized (engineLock) {
			return subMgr == null ? null : subMgr.getCurrentSubtitleStreamInfo();
		}
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
		synchronized (engineLock) {
			if (i == null && subMgr == null) return;
			sub().setCurrentSubtitleStream(i);
		}
	}

	@Override
	public FutureSupplier<SubGrid> getCurrentSubtitles() {
		synchronized (engineLock) {
			return subMgr == null ? completed(SubGrid.EMPTY) : subMgr.getCurrentSubtitles();
		}
	}

	@Override
	public void addSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
		synchronized (engineLock) {
			sub().addSubtitleConsumer(consumer);
		}
	}

	@Override
	public void removeSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
		synchronized (engineLock) {
			if (subMgr != null) {
				subMgr.removeSubtitleConsumer(consumer);
			}
		}
	}

	protected SubGrid createSubStreamGrid() {
		return new SubGrid(new Subtitles.Stream());
	}
	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		var src = getSource();
		if (src == null) return completedEmptyList();

		var srcFile = src.getResource();
		if (srcFile == null) return completedEmptyList();

		String srcName = srcFile.getName();
		if (srcName == null) return completedEmptyList();

		int idx = srcName.lastIndexOf('.');
		String baseName = (idx == -1) ? srcName : srcName.substring(0, idx);

		var parentDir = srcFile.getParent();
		if (parentDir == null) return completedEmptyList();

		// Refactored safely leveraging modern fluent async transformations
		return parentDir.then(srcDir -> {
			if (srcDir == null) return completedEmptyList();
			var filter = srcDir.filterChildren();
			for (String ext : FileSubtitles.getSupportedFileExtensions()) {
				filter = filter.or().startsEnds(baseName, ext);
			}
			return filter.apply();
		}).map(children -> {

if (children == null || children.isEmpty()) return java.util.Collections.emptyList();


			int[] idCounter = { 0xFFFF };
			var list = children.stream()
					.filter(VirtualResource::isFile)
					.map(f -> {
						String name = f.getName();
						int langStart = baseName.length() + 1;
						int langEnd = name.length() - 4;
						String lang = (langStart >= langEnd) ? null : name.substring(langStart, langEnd);
						return new SubtitleStreamInfo(idCounter[0]++, lang, null, (VirtualFile) f);
					})
					.collect(Collectors.toCollection(ArrayList::new));

			int size = list.size();
			IntStream.range(0, size).forEach(i -> 
				IntStream.range(0, size)
					.filter(j -> i != j)
					.forEach(j -> list.add(list.get(i).join(idCounter[0]++, list.get(j))))
			);

			return list;
		});
	}

	@CallSuper
	@Override
	public void close() {
		stopped(false);
	}

	protected void started() {
		synchronized (engineLock) {
			if (state == STATE_PLAYING) return;
			if (state == STATE_PAUSED) {
				if (subMgr != null) subMgr.start();
				state = STATE_PLAYING;
				return;
			}
			state = STATE_PLAYING;
			if (videoView != null) {
				if (subMgr != null) subMgr.addSubtitleConsumer(videoView);
				else selectSubtitleStream();
			} else {
				var src = getSource();
				if (src != null && src.getPrefs().getBooleanPref(SubGenAddon.ENABLED)) {
					selectSubtitleStream();
				}
			}
		}
	}

	protected void stopped(boolean paused) {
		synchronized (engineLock) {
			if (paused) {
				if (state == STATE_PAUSED || state == STATE_STOPPED) return;
				state = STATE_PAUSED;
				if (subMgr != null) subMgr.stop(true);
			} else {
				state = STATE_STOPPED;
				videoView = null;
				
				// --- NEW: Reset Active Channel Parameters on Global Engine Stop ---
Optional.ofNullable(getAudioEffects()).ifPresent(fx -> fx.resetToGlobalSettings(App.get())); 
				// ------------------------------------------------------------------
				
				if (subMgr != null) {
					subMgr.stop(false);
					subMgr = null;
				}
			}
		}
	}


	protected void syncSub(long position, float speed, boolean restart) {
		synchronized (engineLock) {
			if (subMgr != null) {
				subMgr.sync(position, speed, restart);
			}
		}
	}

	private SubMgr sub() {
		synchronized (engineLock) {
			if (subMgr == null) {
				subMgr = new SubMgr();
			}
			return subMgr;
		}
	}
	// =========================================================================
	// NESTED CLASS COMPONENT: SUBTITLE MANAGER
	// =========================================================================

	private final class SubMgr implements BiConsumer<SubGrid.Position, Subtitles.Text> {
		private final List<BiConsumer<SubGrid.Position, Subtitles.Text>> consumers = new ArrayList<>(3);
		private final Object subMgrLock = new Object();
		private int delay;
		private SubScheduler sub;
		private SubtitleStreamInfo streamInfo;
		private FutureSupplier<SubScheduler> loading = cancelled();

		int getSubtitleDelay() {
			synchronized (subMgrLock) {
				return delay;
			}
		}

		void setSubtitleDelay(int milliseconds) {
			synchronized (subMgrLock) {
				if (delay == milliseconds) return;
				delay = milliseconds;
				if (sub == null) return;
			}

			// Decoupled position querying blocks to completely eliminate deadlocks
			getSubtitlePosition().then(pos ->
				getSpeed().main().onSuccess(speed -> {
					synchronized (subMgrLock) {
						if (sub != null) {
							sub.stop(false);
							sub.start(getSubtitleDelay(), getSubtitleDelay(), speed);
						}
					}
				})
			);
		}

		SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
			synchronized (subMgrLock) {
				return streamInfo;
			}
		}

		void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
			synchronized (subMgrLock) {
				stop(false);
				streamInfo = i;
			}

			if (videoView == null) {
				Optional.ofNullable(listener).ifPresent(l -> l.onSubtitleStreamChanged(MediaEngineBase.this, i));
			} else {
				addSubtitleConsumer(videoView);
				load();
			}
		}

		FutureSupplier<SubGrid> getCurrentSubtitles() {
			return load().map(s -> s == null ? SubGrid.EMPTY : s.getSubtitles());
		}

		void addSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
			synchronized (subMgrLock) {
				if (consumers.contains(consumer)) return;
				consumers.add(consumer);
				if (consumer == videoView) prepareDrawer(videoView);
				if (sub == null) {
					load();
				} else if (getEngineState() == STATE_PLAYING && !sub.isStarted()) {
					start();
				}
			}
		}

		void removeSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
			synchronized (subMgrLock) {
				if (!consumers.remove(consumer)) return;
				if (consumers.isEmpty()) stop(true);
				if (consumer == videoView && videoView != null) {
					videoView.releaseSubDrawer();
				}
			}
		}

		private FutureSupplier<SubScheduler> load() {
			final SubtitleStreamInfo inf;
			synchronized (subMgrLock) {
				if (!loading.isCancelled()) return loading;
				inf = streamInfo;
				if (inf == null) return loading;
			}

			FutureSupplier<SubGrid> load;
			if (inf instanceof SubtitleStreamInfo.Generated) {
				load = completed(createSubStreamGrid());
			} else if (inf.getFiles() != null && !inf.getFiles().isEmpty()) {
				load = App.get().execute(() -> {
					var src = getSource();
					if (src == null) return null;

					var files = inf.getFiles();
					if (files == null || files.isEmpty()) return null;

					var sg = FileSubtitles.load(files.get(0));
					if (sg == null) return null;

					if (files.size() == 1) {
						sg.mergeAtPosition(BOTTOM_LEFT);
						if (!src.isVideo()) return sg;
					} else if (files.size() >= 2) {
						var sg1 = FileSubtitles.load(files.get(1));
						if (sg1 != null) {
							sg.mergeAtPosition(BOTTOM_LEFT);
							sg1.mergeAtPosition(BOTTOM_RIGHT);
							sg.mergeWith(sg1);
						}
					}

					if (src.isVideo()) {
						var s1 = sg.get(BOTTOM_LEFT);
						var s2 = sg.get(BOTTOM_RIGHT);
						if (s1 != null && s2 != null && s1.compareTime(s2)) {
							int size = Math.min(s1.size(), s2.size());
							IntStream.range(0, size).forEach(i -> {
								if (s1.get(i) != null && s2.get(i) != null) {
									s1.get(i).setTranslation(s2.get(i).getText());
								}
							});
							sg.remove(BOTTOM_RIGHT);
							sg.move(BOTTOM_LEFT, BOTTOM_CENTER);
						}
					}
					return sg;
				});
			} else {
				return loading;
			}

			synchronized (subMgrLock) {
				return loading = load.main().map(sg -> {
					synchronized (subMgrLock) {
						if (sg == null || sub != null || inf != streamInfo) return null;
sub = new SubScheduler(App.get().getHandler(), sg, this, MediaEngineBase.this::subSchedulerClock); 
						if (getEngineState() == STATE_PLAYING && !consumers.isEmpty()) {
							start();
						}
						return sub;
					}
				});
			}
		}

		@Override
		public void accept(SubGrid.Position position, Subtitles.Text text) {
			List<BiConsumer<SubGrid.Position, Subtitles.Text>> localConsumers;
			synchronized (subMgrLock) {
				localConsumers = new ArrayList<>(consumers);
			}
			localConsumers.forEach(c -> c.accept(position, text));

			if (BuildConfig.D) {
				Optional.ofNullable(getSubtitlePosition()).ifPresent(p -> p.onSuccess(t -> {
					String time = timeToString((int) (t / 1000));
					if (text == null) {
						Log.d("[", time, "][", position, "] null");
					} else {
						Log.d("[", time, "][", position, "][",
								timeToString((int) (text.getTime() / 1000)), "-",
								timeToString((int) ((text.getTime() + text.getDuration()) / 1000)), "]",
								text.getText());
					}
				}));
			}
		}

		void start() {
			synchronized (subMgrLock) {
				if (sub == null) return;
			}

			getSubtitlePosition().then(pos ->
				getSpeed().main().onSuccess(speed -> {
					synchronized (subMgrLock) {
						if (sub == null) return;
						for (var c : consumers) {
							if (c == videoView && videoView != null) {
								prepareDrawer(videoView);
								break;
							}
						}
						sub.start(pos, delay, speed);
					}
				})
			);
		}

		void stop(boolean pause) {
			synchronized (subMgrLock) {
				if (pause) {
					if (sub != null) sub.stop(true);
				} else {
					if (loading != null) loading.cancel();
					loading = cancelled();
					if (sub != null) {
						sub.stop(false);
						sub = null;
					}
					consumers.clear();
				}
			}
		}

		void sync(long position, float speed, boolean restart) {
			synchronized (subMgrLock) {
				if (sub == null) return;
				if (restart) {
					sub.stop(false);
					sub.start(position, getSubtitleDelay(), speed);
					if (!isPlaying()) {
App.get().getHandler().submit(() -> { 
							synchronized (subMgrLock) {
								if (!isPlaying() && sub != null) sub.stop(true);
							}
						});
					}
				} else {
					sub.sync(position, getSubtitleDelay(), speed);
				}
			}
		}

		private void prepareDrawer(@NonNull VideoView view) {
			synchronized (subMgrLock) {
				boolean dbl = streamInfo != null && 
						((streamInfo.getFiles() != null && streamInfo.getFiles().size() == 2) || 
						(streamInfo instanceof SubtitleStreamInfo.Generated));
				view.prepareSubDrawer(dbl);
			}
		}
	}
}
