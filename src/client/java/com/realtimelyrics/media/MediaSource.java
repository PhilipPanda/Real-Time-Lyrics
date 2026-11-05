package com.realtimelyrics.media;

import java.util.Locale;

public interface MediaSource {
	MediaSample latest();

	void start();

	void stop();

	static MediaSource forThisOs() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) return new WindowsMediaSource();
		if (os.contains("mac")) return new MacMediaSource();
		return new PlayerctlMediaSource();
	}
}
