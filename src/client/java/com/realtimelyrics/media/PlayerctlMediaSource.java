package com.realtimelyrics.media;

import java.util.List;

final class PlayerctlMediaSource extends PollingMediaSource {
	@Override
	protected List<String> command() {
		return List.of("playerctl", "--player=spotify,%any", "metadata", "--format",
				"{{status}}\t{{artist}}\t{{title}}\t{{album}}\t{{mpris:length}}\t{{position}}\t{{playerName}}");
	}

	@Override
	protected MediaSample parse(String out, long receivedNanos) {
		String[] f = out.split("\t", -1);
		if (f.length < 7) return MediaSample.none();
		boolean playing = "Playing".equalsIgnoreCase(f[0].trim());
		long lengthUs = parseLong(f[4]);
		long positionUs = parseLong(f[5]);
		long duration = lengthUs > 0 ? lengthUs / 1000 : 0;
		long position = positionUs >= 0 && duration > 0 ? positionUs / 1000 : -1;
		return new MediaSample(f[6], f[2], f[1], f[3], playing, position, duration, receivedNanos);
	}
}
