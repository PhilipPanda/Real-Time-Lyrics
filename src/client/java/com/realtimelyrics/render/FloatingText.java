package com.realtimelyrics.render;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class FloatingText {
	enum Kind {
		LYRIC,
		TITLE,
		NOTES
	}

	static final class Word {
		final int index;
		final FormattedCharSequence seq;
		final float width;
		final float scale;
		final int fixedColor;
		int row;
		float cx, cy;
		long appearAt;
		boolean popped;
		float driftX, driftY, driftZ;

		Word(int index, FormattedCharSequence seq, float width, float scale, int fixedColor) {
			this.index = index;
			this.seq = seq;
			this.width = width;
			this.scale = scale;
			this.fixedColor = fixedColor;
		}
	}

	static final long EXIT_MS = 900;

	final Kind kind;
	final List<Word> words;
	final float yawDeg;
	final float height;
	final double targetDistance;
	final float hue;
	final float seed;
	final long spawnNanos = System.nanoTime();

	Vec3 worldPos;
	float pitchDeg;
	long retireNanos = -1;
	long endSongMs = Long.MAX_VALUE;
	long lastNoteParticleNanos;

	FloatingText(Kind kind, List<Word> words, float yawDeg, float height, double targetDistance, float hue, float seed) {
		this.kind = kind;
		this.words = words;
		this.yawDeg = yawDeg;
		this.height = height;
		this.targetDistance = targetDistance;
		this.hue = hue;
		this.seed = seed;
	}

	boolean retired() {
		return retireNanos >= 0;
	}

	float exitProgress(long nowNanos) {
		return retireNanos < 0 ? 0 : (nowNanos - retireNanos) / 1_000_000f / EXIT_MS;
	}

	long localTime(long songMs, long nowNanos) {
		return kind == Kind.LYRIC ? songMs : (nowNanos - spawnNanos) / 1_000_000L;
	}

	long ageMs(long nowNanos) {
		return (nowNanos - spawnNanos) / 1_000_000L;
	}
}
