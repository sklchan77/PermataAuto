package my.app.permata.media.service;

import androidx.annotation.NonNull;

import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.utils.async.FutureSupplier;

/**
 * @author sklchan77
 */
public interface MediaSessionCallbackAssistant {

	default void startVoiceAssistant(){}

	@NonNull
	default FutureSupplier<MediaLib.PlayableItem> getPrevPlayable(Item i) {
		return i.getPrevPlayable();
	}

	@NonNull
	default FutureSupplier<MediaLib.PlayableItem> getNextPlayable(Item i) {
		return i.getNextPlayable();
	}
}
