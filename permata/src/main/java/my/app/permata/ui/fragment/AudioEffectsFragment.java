package my.app.permata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import my.app.permata.R;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityListener;
import my.app.permata.ui.view.AudioEffectsView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;

/**
 * Enterprise-grade, modern UI controller for managing system audio engineering curves.
 * Upgraded to fully leverage automated state persistence via localized channel tracking signatures.
 * 
 * @author sklchan77
 */
public class AudioEffectsFragment extends MainActivityFragment implements
		MediaSessionCallback.Listener, MainActivityListener {

	private static final String TAG = "AudioEffectsFragment";
	
	// Keeps track of the current channel to prevent redundant binding/sync iterations
	@Nullable private String boundChannelId = null;

	@Override
	public int getFragmentId() {
		return R.id.audio_effects_fragment;
	}

	@Override
	@NonNull
	public CharSequence getTitle() {
		Context ctx = getContext();
		return ctx != null ? ctx.getString(R.string.audio_effects) : "Audio Effects";
	}

	@SuppressLint("RestrictedApi")
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getMainActivity().onSuccess(delegate -> {
			PermataServiceUiBinder binder = delegate.getMediaServiceBinder();
			delegate.addBroadcastListener(this, ACTIVITY_FINISH | ACTIVITY_DESTROY);
			binder.getMediaSessionCallback().addBroadcastListener(this);
			Log.d(TAG, "AudioEffects Fragment lifecycle bindings linked successfully.");
		});
	}

	@Override
	public void onDestroy() {
		getMainActivity().onSuccess(this::removeListeners);
		super.onDestroy();
	}

	private void removeListeners(@NonNull MainActivityDelegate delegate) {
		delegate.removeBroadcastListener(this);
		MediaSessionCallback callback = delegate.getMediaServiceBinder().getMediaSessionCallback();
		if (callback != null) {
			callback.removeBroadcastListener(this);
		}
		Log.d(TAG, "Dangling broadcast listeners unlinked during destruction workflows.");
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return new AudioEffectsView(inflater.getContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		onHiddenChanged(isHidden());
	}

	@Override
	public void onDestroyView() {
		getMainActivity().onSuccess(delegate -> {
			AudioEffectsView view = getView();
			if (view != null) {
				view.apply(delegate.getMediaServiceBinder().getMediaSessionCallback());
				view.cleanup();
			}
		});
		boundChannelId = null;
		super.onDestroyView();
	}

	@Nullable
	@Override
	public AudioEffectsView getView() {
		View view = super.getView();
		return view instanceof AudioEffectsView ? (AudioEffectsView) view : null;
	}
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		getMainActivity().onSuccess(delegate -> handleVisibilityUpdate(delegate, hidden));
	}

	private void handleVisibilityUpdate(@NonNull MainActivityDelegate delegate, boolean hidden) {
		AudioEffectsView view = getView();
		if (view == null) return;

		MediaSessionCallback callback = delegate.getMediaServiceBinder().getMediaSessionCallback();

		if (hidden) {
			view.apply(callback);
			view.cleanup();
			boundChannelId = null;
			return;
		}

		MediaEngine engine = callback.getEngine();
		if (engine != null) {
			PlayableItem item = engine.getSource();
			AudioEffects effects = engine.getAudioEffects();

			if (item != null && effects != null) {
				// Patched compilation node: extract String from Uri safely
				String channelId = item.getLocation() != null ? item.getLocation().toString() : null; 
				
				if (channelId != null) {
					Context ctx = getContext();
					if (ctx != null && !Objects.equals(boundChannelId, channelId)) {
						effects.loadAndApplyPersistedSettingsForChannel(ctx.getApplicationContext(), channelId);
						boundChannelId = channelId;
					}
				}
				
				view.init(callback, effects, item);
				return;
			}
		}
		
		close(delegate);
	}

	@Override
	public boolean onBackPressed() {
		getMainActivity().onSuccess(this::close);
		return true;
	}

	private void applyAndCleanup(@NonNull MainActivityDelegate delegate) {
		AudioEffectsView view = getView();
		if (view != null) {
			view.apply(delegate.getMediaServiceBinder().getMediaSessionCallback());
			view.cleanup();
		}
		boundChannelId = null;
	}

	private void close(@NonNull MainActivityDelegate delegate) {
		applyAndCleanup(delegate);
		delegate.backToNavFragment();
	}

	@NonNull
	private FutureSupplier<MainActivityDelegate> getMainActivity() {
		return MainActivityDelegate.getActivityDelegate(Objects.requireNonNull(getContext()));
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void onPlaybackStateChanged(@NonNull MediaSessionCallback callback, @NonNull PlaybackStateCompat state) {
		if (isHidden()) return;

		AudioEffectsView view = getView();
		if (view == null) return;

		switch (state.getState()) {
			case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
			case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
			case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
				view.apply(callback);
				view.cleanup();
				boundChannelId = null;
				break;

			case PlaybackStateCompat.STATE_STOPPED:
				getMainActivity().onSuccess(this::close);
				break;

			default:
				MediaEngine engine = callback.getEngine();
				if (engine == null) {
					getMainActivity().onSuccess(this::close);
					return;
				}

				PlayableItem item = engine.getSource();
				AudioEffects effects = engine.getAudioEffects();

				if (item == null || effects == null) {
					getMainActivity().onSuccess(this::close);
					return;
				}

				// Patched compilation node: extract String from Uri safely
				String currentChannelId = item.getLocation() != null ? item.getLocation().toString() : null;
				boolean channelChanged = !Objects.equals(boundChannelId, currentChannelId);

				if (view.getEffects() != effects || channelChanged) {
					view.cleanup();
					
					Context ctx = getContext();
					if (ctx != null && currentChannelId != null && channelChanged) {
						effects.loadAndApplyPersistedSettingsForChannel(ctx.getApplicationContext(), currentChannelId);
						boundChannelId = currentChannelId;
					}
					
					view.init(callback, effects, item);
				}
				break;
		}
	}

	@Override
	public void onActivityEvent(@NonNull MainActivityDelegate delegate, long event) {
		if (event == ACTIVITY_FINISH) {
			applyAndCleanup(delegate);
		} else if (event == ACTIVITY_DESTROY) {
			removeListeners(delegate);
		}
	}
}
