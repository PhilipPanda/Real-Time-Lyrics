package com.realtimelyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LyricsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean enabled = true;
	public double distance = 4.5;
	public double textScale = 1.0;
	public boolean particles = true;
	public boolean showTitleCard = true;
	public boolean seeThroughWalls = false;
	public boolean glowOutline = true;
	public boolean statusMessages = true;
	public int globalOffsetMs = 0;
	public boolean followPlayer = false;
	public boolean floatingMotion = false;
	public double spreadDegrees = 34;
	public double heightOffset = 0;
	public ColorStyle colorStyle = ColorStyle.RAINBOW;

	public enum ColorStyle {
		RAINBOW("Rainbow", -1, 0.30f),
		WHITE("White", 0.66f, 0f),
		GOLD("Gold", 0.12f, 0.45f),
		AQUA("Aqua", 0.50f, 0.40f),
		PINK("Pink", 0.92f, 0.38f),
		PURPLE("Purple", 0.76f, 0.38f),
		GREEN("Green", 0.33f, 0.38f);

		public final String label;
		public final float hue;
		public final float saturation;

		ColorStyle(String label, float hue, float saturation) {
			this.label = label;
			this.hue = hue;
			this.saturation = saturation;
		}
	}

	private transient volatile boolean dirty;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realtimelyrics.json");
	}

	public static LyricsConfig load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				LyricsConfig c = GSON.fromJson(r, LyricsConfig.class);
				if (c != null) {
					c.sanitize();
					c.save();
					return c;
				}
			} catch (Exception e) {
				Path backup = p.resolveSibling("realtimelyrics.json.broken");
				RealTimeLyricsClient.LOG.warn("Could not read {}, using defaults (old file kept as {})", p, backup.getFileName(), e);
				try {
					Files.move(p, backup, StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {
				}
			}
		}
		LyricsConfig c = new LyricsConfig();
		c.save();
		return c;
	}

	private void sanitize() {
		if (colorStyle == null) colorStyle = ColorStyle.RAINBOW;
		distance = clamp(distance, 2, 12, 4.5);
		textScale = clamp(textScale, 0.4, 3, 1);
		spreadDegrees = clamp(spreadDegrees, 0, 40, 34);
		heightOffset = clamp(heightOffset, -1.5, 2.5, 0);
		globalOffsetMs = (int) clamp(globalOffsetMs, -3000, 3000, 0);
	}

	private static double clamp(double v, double min, double max, double fallback) {
		return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
	}

	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (dirty) save();
	}

	public synchronized void save() {
		dirty = false;
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			Path tmp = p.resolveSibling("realtimelyrics.json.tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
			try {
				Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			RealTimeLyricsClient.LOG.warn("Could not save config", e);
		}
	}
}
