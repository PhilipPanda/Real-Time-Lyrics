package com.realtimelyrics.media;

import java.util.List;

final class MacMediaSource extends PollingMediaSource {
	private static final String SCRIPT = """
			on info(appName)
				using terms from application "Spotify"
					tell application appName
						set t to current track
						return (player state as string) & tab & (artist of t) & tab & (name of t) & tab & (album of t) & tab & (duration of t) & tab & (player position) & tab & appName
					end tell
				end using terms from
			end info
			if application "Spotify" is running then return info("Spotify")
			if application "Music" is running then
				tell application "Music"
					if player state is not stopped then
						set t to current track
						return (player state as string) & tab & (artist of t) & tab & (name of t) & tab & (album of t) & tab & ((duration of t) * 1000) & tab & (player position) & tab & "Music"
					end if
				end tell
			end if
			return ""
			""";

	@Override
	protected List<String> command() {
		return List.of("osascript", "-e", SCRIPT);
	}

	@Override
	protected MediaSample parse(String out, long receivedNanos) {
		String[] f = out.split("\t", -1);
		if (f.length < 7) return MediaSample.none();
		boolean playing = f[0].trim().equalsIgnoreCase("playing");
		long duration = (long) Math.max(0, parseDouble(f[4]));
		double posSec = parseDouble(f[5]);
		long position = posSec >= 0 && duration > 0 ? (long) (posSec * 1000) : -1;
		return new MediaSample(f[6], f[2], f[1], f[3], playing, position, duration, receivedNanos);
	}
}
