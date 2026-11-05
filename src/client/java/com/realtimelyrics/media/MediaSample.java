package com.realtimelyrics.media;

public record MediaSample(String app, String title, String artist, String album, boolean playing,
						  long positionMs, long durationMs, long receivedNanos) {
	public static MediaSample none() {
		return new MediaSample("", "", "", "", false, -1, 0, System.nanoTime());
	}

	public boolean hasTrack() {
		return title != null && !title.isBlank();
	}

	public boolean positionKnown() {
		return positionMs >= 0;
	}

	public long positionAt(long nowNanos) {
		if (positionMs < 0) return -1;
		long pos = positionMs + (playing ? (nowNanos - receivedNanos) / 1_000_000L : 0);
		return durationMs > 0 ? Math.min(pos, durationMs) : pos;
	}
}
