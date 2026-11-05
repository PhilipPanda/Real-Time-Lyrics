package com.realtimelyrics.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Lyrics(List<Line> lines, boolean synced, boolean instrumental) {
	public record Line(long timeMs, String text) {
		public boolean isBlank() {
			return text.isBlank();
		}
	}

	private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)]");
	private static final Pattern WORD_TAG = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

	public static Lyrics instrumentalSong() {
		return new Lyrics(List.of(), true, true);
	}

	public int indexAt(long ms) {
		int lo = 0, hi = lines.size() - 1, found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= ms) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}

	public static Lyrics parseLrc(String lrc) {
		List<Line> out = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = TIME_TAG.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1));
				double sec = Double.parseDouble(m.group(2).replace(':', '.'));
				times.add(min * 60_000 + Math.round(sec * 1000));
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = WORD_TAG.matcher(raw.substring(end)).replaceAll("").trim();
			for (long t : times) out.add(new Line(t, text));
		}
		out.sort(Comparator.comparingLong(Line::timeMs));
		return new Lyrics(List.copyOf(out), true, false);
	}

	public static Lyrics fromPlain(String plain, long durationMs) {
		List<String> texts = new ArrayList<>();
		for (String s : plain.split("\\r?\\n")) {
			if (!s.isBlank()) texts.add(s.trim());
		}
		List<Line> out = new ArrayList<>();
		if (texts.isEmpty()) return new Lyrics(List.of(), false, false);
		long start = durationMs > 0 ? (long) (durationMs * 0.06) : 8000;
		long step = durationMs > 0 ? Math.max(1500, (long) (durationMs * 0.88) / texts.size()) : 3500;
		for (int i = 0; i < texts.size(); i++) out.add(new Line(start + i * step, texts.get(i)));
		return new Lyrics(List.copyOf(out), false, false);
	}
}
