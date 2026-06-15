package my.app.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.utils.net.NetChannel;

/**
 * @author sklchan77
 */
public interface HttpResponse extends HttpMessage {

	int getStatusCode();

	@NonNull
	CharSequence getReason();

	@Nullable
	CharSequence getLocation();

	@Nullable
	CharSequence getEtag();

	@NonNull
	HttpConnection getConnection();

	@NonNull
	@Override
	default NetChannel getChannel() {
		return getConnection().getChannel();
	}
}
