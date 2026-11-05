package com.realtimelyrics.render;

final class Anim {
	private Anim() {
	}

	static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	static float easeOutCubic(float t) {
		float u = 1 - clamp01(t);
		return 1 - u * u * u;
	}

	static float easeInQuad(float t) {
		t = clamp01(t);
		return t * t;
	}

	static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.9f, c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	static int mixRgb(int a, int b, float t) {
		t = clamp01(t);
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	static int argb(float alpha, int rgb) {
		return ((int) (clamp01(alpha) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
