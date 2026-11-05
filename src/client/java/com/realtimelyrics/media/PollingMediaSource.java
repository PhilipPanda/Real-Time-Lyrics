package com.realtimelyrics.media;

import com.realtimelyrics.RealTimeLyricsClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class PollingMediaSource implements MediaSource {
	private volatile MediaSample latest;
	private volatile boolean running;
	private boolean warned;

	protected abstract List<String> command();

	protected abstract MediaSample parse(String output, long receivedNanos);

	@Override
	public MediaSample latest() {
		return latest;
	}

	@Override
	public void start() {
		if (running) return;
		running = true;
		Thread t = new Thread(this::loop, "RealTimeLyrics-Media");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void stop() {
		running = false;
	}

	private void loop() {
		while (running) {
			try {
				Process p = new ProcessBuilder(command()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
				if (p.waitFor(3, TimeUnit.SECONDS)) {
					String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
					latest = p.exitValue() == 0 && !out.isEmpty() ? parse(out, System.nanoTime()) : MediaSample.none();
				} else {
					p.destroyForcibly();
				}
			} catch (IOException e) {
				if (!warned) {
					warned = true;
					RealTimeLyricsClient.LOG.warn("Can't read now playing ({}): {}", command().get(0), e.toString());
				}
				latest = null;
			} catch (Exception e) {
				latest = null;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	protected static long parseLong(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (Exception e) {
			return -1;
		}
	}

	protected static double parseDouble(String s) {
		try {
			return Double.parseDouble(s.trim().replace(',', '.'));
		} catch (Exception e) {
			return -1;
		}
	}
}
