package com.realtimelyrics.lyrics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class TitleCleaner {
	private static final Pattern JUNK_BRACKETS = Pattern.compile(
			"\\s*[(\\[【][^)\\]】]*(official|video|audio|lyric|visuali[sz]er|\\bhd\\b|\\b4k\\b|\\bmv\\b|m/v|explicit|clean|remaster|color coded|performance|music)[^)\\]】]*[)\\]】]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern FEAT = Pattern.compile("\\s*[(\\[]?\\s*\\b(feat\\.?|ft\\.?|featuring)\\s[^)\\]]*[)\\]]?", Pattern.CASE_INSENSITIVE);
	private static final Pattern SUFFIX = Pattern.compile(
			"\\s+-\\s+(\\d{4}\\s+)?(remaster(ed)?|single version|radio edit|mono|stereo|bonus track|from .*)(\\s+\\d{4})?(\\s+version)?\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ARTIST_JUNK = Pattern.compile("\\s*(-\\s*topic|vevo|official|music)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(,|&|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\band\\b|/)\\s*", Pattern.CASE_INSENSITIVE);

	private TitleCleaner() {
	}

	public static String cleanArtist(String artist) {
		String a = artist == null ? "" : artist.trim();
		String prev;
		do {
			prev = a;
			a = ARTIST_JUNK.matcher(a).replaceAll("").trim();
		} while (!a.equals(prev) && !a.isEmpty());
		return a.isEmpty() ? artist.trim() : a;
	}

	public static String cleanTitle(String title) {
		String t = title == null ? "" : title.trim();
		t = JUNK_BRACKETS.matcher(t).replaceAll("");
		t = FEAT.matcher(t).replaceAll("");
		t = SUFFIX.matcher(t).replaceAll("");
		t = t.replaceAll("[\"“”]", "").replaceAll("\\s+", " ").trim();
		return t.isEmpty() ? title.trim() : t;
	}

	public static String firstArtist(String artist) {
		String[] parts = ARTIST_SPLIT.split(artist, 2);
		return parts.length > 0 ? parts[0].trim() : artist;
	}

	public static String[] display(String artist, String title) {
		String a = cleanArtist(artist);
		String t = cleanTitle(title);
		if ((a.isEmpty() || looksLikeChannel(artist)) && t.contains(" - ")) {
			String[] split = t.split(" - ", 2);
			return new String[]{split[0].trim(), cleanTitle(split[1])};
		}
		return new String[]{a, t};
	}

	private static boolean looksLikeChannel(String artist) {
		return artist == null || ARTIST_JUNK.matcher(artist.trim()).find();
	}

	public static List<String[]> candidates(String artist, String title) {
		String a = cleanArtist(artist);
		String t = cleanTitle(title);
		Set<String> seen = new LinkedHashSet<>();
		List<String[]> out = new ArrayList<>();

		String[] split = t.contains(" - ") ? t.split(" - ", 2) : null;
		boolean channel = a.isEmpty() || looksLikeChannel(artist);
		if (split != null && channel) add(out, seen, cleanArtist(split[0]), cleanTitle(split[1]));
		add(out, seen, a, t);
		if (split != null && !channel) add(out, seen, cleanArtist(split[0]), cleanTitle(split[1]));
		add(out, seen, firstArtist(a), t);
		if (!t.equals(title.trim())) add(out, seen, a, title.trim());
		return out;
	}

	private static void add(List<String[]> out, Set<String> seen, String a, String t) {
		if (t == null || t.isBlank()) return;
		a = a == null ? "" : a.trim();
		if (seen.add(a.toLowerCase() + "\n" + t.toLowerCase())) out.add(new String[]{a, t.trim()});
	}
}
