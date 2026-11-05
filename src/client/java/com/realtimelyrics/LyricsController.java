package com.realtimelyrics;

import com.realtimelyrics.lyrics.LrcLibClient;
import com.realtimelyrics.lyrics.Lyrics;
import com.realtimelyrics.lyrics.TitleCleaner;
import com.realtimelyrics.media.MediaSample;
import com.realtimelyrics.media.MediaSource;
import com.realtimelyrics.render.LyricsRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class LyricsController {
	private static final long CORRECTION_THRESHOLD_MS = 400;

	private final LyricsConfig config;
	private final MediaSource source;
	private final LrcLibClient lrclib = new LrcLibClient();
	private final LyricsRenderer renderer;

	private long anchorMs;
	private long anchorNanos = System.nanoTime();
	private boolean playing;

	private String trackKey;
	private boolean positionKnown;
	private long nudgeMs;
	private long noMediaSinceNanos = -1;
	private Lyrics lyrics;
	private String displayTitle = "";
	private String displayArtist = "";

	public LyricsController(LyricsConfig config, MediaSource source, LyricsRenderer renderer) {
		this.config = config;
		this.source = source;
		this.renderer = renderer;
	}

	public Lyrics lyrics() {
		return lyrics;
	}

	public boolean isPlaying() {
		return playing;
	}

	public long songPositionMs() {
		long pos = anchorMs + (playing ? (System.nanoTime() - anchorNanos) / 1_000_000L : 0);
		return pos + config.globalOffsetMs + nudgeMs;
	}

	private long rawPosition(long nowNanos) {
		return anchorMs + (playing ? (nowNanos - anchorNanos) / 1_000_000L : 0);
	}

	private void setClock(long positionMs, boolean isPlaying, long nowNanos) {
		anchorMs = positionMs;
		anchorNanos = nowNanos;
		playing = isPlaying;
	}

	public void tick(Minecraft mc) {
		long now = System.nanoTime();
		MediaSample s = source.latest();

		if (s == null || !s.hasTrack()) {
			if (trackKey != null) {
				if (noMediaSinceNanos < 0) noMediaSinceNanos = now;
				else if (now - noMediaSinceNanos > 3_000_000_000L) clearTrack();
			}
			return;
		}
		noMediaSinceNanos = -1;

		String key = s.app() + "\n" + s.artist() + "\n" + s.title();
		if (!key.equals(trackKey)) {
			startTrack(mc, s, key, now);
			return;
		}

		if (s.positionKnown()) {
			long expected = s.positionAt(now);
			if (!positionKnown || s.playing() != playing || Math.abs(expected - rawPosition(now)) > CORRECTION_THRESHOLD_MS) {
				setClock(expected, s.playing(), now);
			}
			positionKnown = true;
		} else if (s.playing() != playing) {
			setClock(rawPosition(now), s.playing(), now);
		}
	}

	private void clearTrack() {
		trackKey = null;
		lyrics = null;
		renderer.clear();
	}

	private void startTrack(Minecraft mc, MediaSample s, String key, long now) {
		trackKey = key;
		lyrics = null;
		nudgeMs = 0;
		positionKnown = s.positionKnown();
		setClock(positionKnown ? s.positionAt(now) : 0, s.playing(), now);

		String[] display = TitleCleaner.display(s.artist(), s.title());
		displayArtist = display[0];
		displayTitle = display[1];
		renderer.onNewTrack(displayTitle, displayArtist);
		RealTimeLyricsClient.LOG.info("Now playing: {} - {} ({}, position {})", displayArtist, displayTitle, s.app(),
				positionKnown ? "reported" : "not reported");
		status(mc, Component.literal("♫ Finding lyrics for ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(displayTitle).withStyle(ChatFormatting.WHITE)));

		lrclib.find(s.artist(), s.title(), s.album(), s.durationMs()).whenComplete((result, err) -> mc.execute(() -> {
			if (!key.equals(trackKey)) return;
			if (err != null || result == null || result.isEmpty()) {
				RealTimeLyricsClient.LOG.info("No lyrics found for {} - {}", displayArtist, displayTitle);
				status(mc, Component.literal("✖ No lyrics found for ").withStyle(ChatFormatting.GRAY)
						.append(Component.literal(displayTitle).withStyle(ChatFormatting.WHITE)));
				return;
			}
			lyrics = result.get();
			RealTimeLyricsClient.LOG.info("Lyrics found for {}: {} lines, synced={}, instrumental={}",
					displayTitle, lyrics.lines().size(), lyrics.synced(), lyrics.instrumental());
			if (lyrics.instrumental()) {
				status(mc, Component.literal("♪ Instrumental").withStyle(ChatFormatting.LIGHT_PURPLE));
			} else if (!lyrics.synced()) {
				status(mc, Component.literal("Only unsynced lyrics found — timing is a guess").withStyle(ChatFormatting.YELLOW));
			} else if (!positionKnown) {
				status(mc, Component.literal("♫ Lyrics ready · this player doesn't share its position, timed from song start — use [ ] to sync")
						.withStyle(ChatFormatting.AQUA));
			} else {
				status(mc, Component.literal("♫ Lyrics synced").withStyle(ChatFormatting.AQUA));
			}
		}));
	}

	public void nudge(Minecraft mc, long deltaMs) {
		nudgeMs += deltaMs;
		long total = nudgeMs + config.globalOffsetMs;
		status(mc, Component.literal(String.format("Lyrics timing: %+.1fs", total / 1000.0)).withStyle(ChatFormatting.AQUA));
	}

	public void restartTiming(Minecraft mc) {
		long now = System.nanoTime();
		setClock(0, true, now);
		nudgeMs = 0;
		status(mc, Component.literal("Lyrics timing reset to the start of the song").withStyle(ChatFormatting.AQUA));
	}

	public void status(Minecraft mc, Component message) {
		if (config.statusMessages && mc.player != null) mc.player.displayClientMessage(message, true);
	}
}
