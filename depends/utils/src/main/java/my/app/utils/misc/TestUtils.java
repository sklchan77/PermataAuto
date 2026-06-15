package my.app.utils.misc;

import my.app.utils.BuildConfig;

/**
 * @author sklchan77
 */
public class TestUtils {
	static volatile boolean logExceptions = true;

	public static boolean isTestMode() {
		return BuildConfig.D && TestMode.isTestMode;
	}

	public static void enableTestMode() {
		System.setProperty("my.app.utils.testMode", "true");
	}

	public static void enableExceptionLogging(boolean enabled) {
		logExceptions = enabled;
	}

	public static boolean logExceptions() {
		return logExceptions;
	}

	private static final class TestMode {
		static final boolean isTestMode = Boolean.getBoolean("my.app.utils.testMode");
	}
}
