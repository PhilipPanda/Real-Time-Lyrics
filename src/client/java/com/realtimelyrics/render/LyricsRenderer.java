package com.realtimelyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realtimelyrics.LyricsConfig;
import com.realtimelyrics.LyricsController;
import com.realtimelyrics.lyrics.Lyrics;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class LyricsRenderer {
	private static final float BLOCKS_PER_PX = 0.028f;
	private static final float MAX_ROW_PX = 190;

	private final LyricsConfig config;
	private final List<FloatingText> texts = new ArrayList<>();
	private final Random random = new Random();
	private final long startNanos = System.nanoTime();

	private FloatingText currentLine;
	private FloatingText notes;
	private Lyrics shownLyrics;
	private int shownIndex = Integer.MIN_VALUE;
	private int lineCounter;
	private float trackHue = random.nextFloat();
	private String[] pendingTitle;

	private Vec3 anchor;
	private Vec3 anchorDelta = Vec3.ZERO;

	private Vec3 viewOrigin = Vec3.ZERO;
	private float viewPitch;
	private float viewHalfWidthDeg = 50;
	private float viewHalfHeightDeg = 35;
	private long lastFrameNanos;

	public LyricsRenderer(LyricsConfig config) {
		this.config = config;
	}

	public void clear() {
		long now = System.nanoTime();
		for (FloatingText t : texts) retire(t, now);
		currentLine = null;
		notes = null;
		shownLyrics = null;
		shownIndex = Integer.MIN_VALUE;
		pendingTitle = null;
	}

	public void onNewTrack(String title, String artist) {
		clear();
		trackHue = ((title + artist).hashCode() & 0xFFFF) / 65535f;
		lineCounter = 0;
		if (config.showTitleCard && !title.isBlank()) pendingTitle = new String[]{title, artist};
	}

	public void render(WorldRenderContext ctx, LyricsController controller) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!config.enabled) {
			texts.clear();
			currentLine = null;
			notes = null;
			shownLyrics = null;
			shownIndex = Integer.MIN_VALUE;
			pendingTitle = null;
			return;
		}

		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;
		float time = (now - startNanos) / 1e9f;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(partial);
		Vec3 oldAnchor = anchor;
		if (anchor == null || anchor.distanceToSqr(eye) > 16 * 16) anchor = eye;
		else anchor = anchor.lerp(eye, 1 - Math.exp(-dt * 6));
		anchorDelta = oldAnchor == null || oldAnchor.distanceToSqr(anchor) > 16 * 16 ? Vec3.ZERO : anchor.subtract(oldAnchor);

		if (notes != null && notes.worldPos != null && !config.followPlayer && notes.worldPos.distanceToSqr(eye) > Math.pow(config.distance + 8, 2)) {
			retire(notes, now);
			notes = null;
		}

		Camera camera = mc.gameRenderer.getMainCamera();
		CameraRenderState cam = ctx.worldState().cameraRenderState;
		viewOrigin = cam.pos;
		float viewYaw = camera.yRot();
		viewPitch = Math.max(-60f, Math.min(60f, camera.xRot()));
		double vHalf = Math.toRadians(mc.options.fov().get() / 2.0);
		double aspect = mc.getWindow().getHeight() > 0 ? (double) mc.getWindow().getWidth() / mc.getWindow().getHeight() : 16 / 9.0;
		viewHalfWidthDeg = (float) Math.toDegrees(Math.atan(Math.tan(vHalf) * aspect));
		viewHalfHeightDeg = (float) Math.toDegrees(vHalf);
		Font font = mc.font;

		if (pendingTitle != null) {
			texts.add(spawnTitle(font, pendingTitle[0], pendingTitle[1], viewYaw));
			pendingTitle = null;
		}

		long songMs = controller.songPositionMs();
		updateLines(controller.lyrics(), songMs, now, viewYaw, font);

		draw(ctx.matrices(), ctx.commandQueue(), cam.pos, cam.orientation, mc, songMs, now, dt, time);
	}

	private void updateLines(Lyrics lyrics, long songMs, long now, float viewYaw, Font font) {
		if (lyrics != shownLyrics) {
			if (currentLine != null) retire(currentLine, now);
			if (notes != null) retire(notes, now);
			currentLine = null;
			notes = null;
			shownLyrics = lyrics;
			shownIndex = Integer.MIN_VALUE;
		}
		if (lyrics == null) return;

		boolean gap;
		if (lyrics.instrumental() || lyrics.lines().isEmpty()) {
			gap = lyrics.instrumental();
		} else {
			List<Lyrics.Line> lines = lyrics.lines();
			int idx = lyrics.indexAt(songMs);
			if (idx != shownIndex) {
				if (currentLine != null) retire(currentLine, now);
				currentLine = null;
				if (idx >= 0 && !lines.get(idx).isBlank()) {
					currentLine = spawnLyric(lyrics, idx, viewYaw, font);
					texts.add(currentLine);
				}
				shownIndex = idx;
			}
			if (currentLine != null && songMs >= currentLine.endSongMs) {
				retire(currentLine, now);
				currentLine = null;
			}
			long nextStart = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : Long.MAX_VALUE;
			gap = currentLine == null && nextStart != Long.MAX_VALUE && nextStart - songMs > 1500;
		}

		if (gap && notes == null) {
			notes = spawnNotes(font, viewYaw);
			texts.add(notes);
		} else if (!gap && notes != null) {
			retire(notes, now);
			notes = null;
		}
	}

	private void draw(PoseStack ps, SubmitNodeCollector out, Vec3 camPos, Quaternionf rot, Minecraft mc,
					  long songMs, long now, float dt, float time) {
		Vector3f right = rot.transform(new Vector3f(1, 0, 0));
		Vector3f up = rot.transform(new Vector3f(0, 1, 0));
		Font.DisplayMode mode = config.seeThroughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

		Iterator<FloatingText> it = texts.iterator();
		while (it.hasNext()) {
			FloatingText ft = it.next();
			if (ft.kind == FloatingText.Kind.TITLE && !ft.retired() && ft.ageMs(now) > 5200) retire(ft, now);
			float exit = ft.exitProgress(now);
			if (exit >= 1) {
				it.remove();
				continue;
			}

			Vec3 pos = place(ft, mc, time);
			float s = (float) (BLOCKS_PER_PX * config.textScale);
			long local = ft.localTime(songMs, now);
			float exitEase = Anim.easeOutCubic(exit);
			float exitFade = 1 - Anim.easeInQuad(exit);

			ps.pushPose();
			ps.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
			ps.mulPose(rot);
			ps.scale(s, -s, s);

			for (FloatingText.Word w : ft.words) {
				float age = local - w.appearAt;
				if (age < 0) continue;

				float x = w.cx + w.driftX * exitEase;
				float rise = (1 - Anim.easeOutCubic(age / 400f)) * 9f;
				float wave = !config.floatingMotion ? 0
						: (float) Math.sin(time * 2.2f + w.index * 0.7f + ft.seed) * (ft.kind == FloatingText.Kind.NOTES ? 2.5f : 0.7f);
				float y = w.cy + rise + wave + w.driftY * exitEase;
				float z = w.driftZ * exitEase;

				if (!w.popped && exit == 0) {
					w.popped = true;
					if (config.particles && age < 350 && ft.kind != FloatingText.Kind.NOTES) {
						Vec3 wp = pos.add(right.x() * x * s - up.x() * y * s,
								right.y() * x * s - up.y() * y * s,
								right.z() * x * s - up.z() * y * s);
						popParticles(mc.level, wp, ft, w);
					}
				}

				float alpha = Anim.clamp01(age / 160f) * exitFade;
				if (ft.kind == FloatingText.Kind.NOTES) alpha *= 0.7f + 0.3f * (float) Math.sin(time * 4 + w.index);
				if (alpha < 0.04f) continue;

				float k = Anim.easeOutBack(age / 300f) * w.scale * (1 + 0.25f * exitEase);
				LyricsConfig.ColorStyle style = config.colorStyle;
				boolean rainbow = style.hue < 0;
				float hue = !rainbow ? style.hue
						: ft.kind == FloatingText.Kind.NOTES ? time * 0.15f + w.index * 0.12f : ft.hue + w.index * 0.03f;
				int base = w.fixedColor >= 0 ? w.fixedColor : Anim.hsv(hue, style.saturation, 1f);
				int color = Anim.mixRgb(base, 0xFFFFFF, (1 - Anim.clamp01(age / 700f)) * 0.85f);
				int glow = style == LyricsConfig.ColorStyle.WHITE ? 0x2C2C3C : Anim.hsv(hue, 0.85f, 0.45f);

				ps.pushPose();
				ps.translate(x, y, z);
				ps.scale(k, k, k);
				float tx = -w.width / 2f;
				if (config.seeThroughWalls) {
					out.submitText(ps, tx, -4f, w.seq, false, mode, LightTexture.FULL_BRIGHT, Anim.argb(alpha, color), 0, 0);
				} else if (config.glowOutline) {
					out.submitText(ps, tx, -4f, w.seq, false, mode, LightTexture.FULL_BRIGHT, Anim.argb(alpha, color), 0, Anim.argb(alpha * 0.9f, glow));
				} else {
					out.submitText(ps, tx, -4f, w.seq, true, mode, LightTexture.FULL_BRIGHT, Anim.argb(alpha, color), 0, 0);
				}
				ps.popPose();
			}
			ps.popPose();

			if (ft.kind == FloatingText.Kind.NOTES && config.particles && exit == 0 && now - ft.lastNoteParticleNanos > 1_300_000_000L) {
				ft.lastNoteParticleNanos = now;
				mc.level.addParticle(ParticleTypes.NOTE, pos.x + (random.nextDouble() - 0.5), pos.y + 0.4, pos.z + (random.nextDouble() - 0.5),
						random.nextInt(25) / 24.0, 0, 0);
			}
		}
	}

	private Vec3 place(FloatingText ft, Minecraft mc, float time) {
		if (ft.worldPos == null) ft.worldPos = spawnPosition(ft, mc);
		else if (config.followPlayer) ft.worldPos = ft.worldPos.add(anchorDelta);
		double bob = config.floatingMotion ? Math.sin(time * 1.3f + ft.seed) * 0.06 : 0;
		return ft.worldPos.add(0, bob, 0);
	}

	private Vec3 spawnPosition(FloatingText ft, Minecraft mc) {
		double yaw = Math.toRadians(ft.yawDeg);
		double pitch = Math.toRadians(ft.pitchDeg);
		Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 target = viewOrigin.add(dir.scale(ft.targetDistance)).add(0, ft.height + config.heightOffset, 0);
		return clearOfWalls(mc, viewOrigin, target);
	}

	private static Vec3 clearOfWalls(Minecraft mc, Vec3 from, Vec3 to) {
		BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == HitResult.Type.MISS) return to;
		Vec3 d = to.subtract(from);
		double full = d.length();
		double allowed = Math.max(1.2, hit.getLocation().distanceTo(from) - 0.45);
		return full < 1e-6 ? to : from.add(d.scale(Math.min(1, allowed / full)));
	}

	private float spawnYaw(float viewYaw, int side) {
		float maxOff = Math.max(0, Math.min((float) config.spreadDegrees, viewHalfWidthDeg * 0.6f));
		return viewYaw + side * maxOff * (0.25f + 0.75f * random.nextFloat());
	}

	private float spawnPitch(float spreadFraction) {
		float maxOff = viewHalfHeightDeg * 0.3f * spreadFraction;
		return viewPitch + (random.nextFloat() * 2 - 1) * maxOff;
	}

	private void retire(FloatingText ft, long now) {
		if (ft.retired()) return;
		ft.retireNanos = now;
		for (FloatingText.Word w : ft.words) {
			float side = Math.signum(w.cx == 0 ? random.nextFloat() - 0.5f : w.cx);
			w.driftX = side * (6 + random.nextFloat() * 22) + (random.nextFloat() - 0.5f) * 10;
			w.driftY = -(8 + random.nextFloat() * 22);
			w.driftZ = (random.nextFloat() - 0.5f) * 30;
		}
	}

	private void popParticles(ClientLevel level, Vec3 p, FloatingText ft, FloatingText.Word w) {
		double sx = (random.nextDouble() - 0.5) * 0.02;
		double sz = (random.nextDouble() - 0.5) * 0.02;
		level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, sx, 0.012 + random.nextDouble() * 0.015, sz);
		if (ft.kind == FloatingText.Kind.LYRIC && w.index % 4 == 0) {
			level.addParticle(ParticleTypes.NOTE, p.x, p.y + 0.35, p.z, ((ft.hue + w.index * 0.03f) % 1f + 1f) % 1f, 0, 0);
		}
	}

	private FloatingText spawnLyric(Lyrics lyrics, int idx, float viewYaw, Font font) {
		List<Lyrics.Line> lines = lyrics.lines();
		Lyrics.Line line = lines.get(idx);
		long next = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : line.timeMs() + 6000;
		long dur = Math.max(300, next - line.timeMs());

		String[] tokens = line.text().trim().split("\\s+");
		List<FloatingText.Word> words = new ArrayList<>(tokens.length);
		int totalWeight = 0;
		for (int i = 0; i < tokens.length; i++) {
			FormattedCharSequence seq = Component.literal(tokens[i]).getVisualOrderText();
			words.add(new FloatingText.Word(i, seq, font.width(seq), 1f, -1));
			totalWeight += tokens[i].length() + 2;
		}

		long reveal = Math.min((long) (dur * 0.7), 380L * tokens.length);
		if (!lyrics.synced()) reveal = Math.min(reveal, 160L * tokens.length);
		int weight = 0;
		for (int i = 0; i < tokens.length; i++) {
			words.get(i).appearAt = line.timeMs() + reveal * weight / Math.max(1, totalWeight);
			weight += tokens[i].length() + 2;
		}
		wrap(words, font.width(" "));
		layout(words, font.width(" "));

		int side = lineCounter % 2 == 0 ? -1 : 1;
		float yaw = spawnYaw(viewYaw, side);
		float height = -0.3f + random.nextFloat() * 0.75f;
		double dist = config.distance + random.nextDouble() * 1.2;
		float hue = trackHue + lineCounter * 0.09f;
		lineCounter++;

		FloatingText ft = new FloatingText(FloatingText.Kind.LYRIC, words, yaw, height, dist, hue, random.nextFloat() * 10);
		ft.pitchDeg = spawnPitch((float) Math.min(1, config.spreadDegrees / 34.0));
		ft.endSongMs = Math.min(next, line.timeMs() + reveal + 4500);
		return ft;
	}

	private FloatingText spawnTitle(Font font, String title, String artist, float viewYaw) {
		List<FloatingText.Word> words = new ArrayList<>();
		int i = 0;
		List<String> titleTokens = new ArrayList<>();
		titleTokens.add("♫");
		for (String t : title.trim().split("\\s+")) titleTokens.add(t);
		for (String t : titleTokens) {
			MutableComponent c = Component.literal(t).withStyle(ChatFormatting.BOLD);
			FormattedCharSequence seq = c.getVisualOrderText();
			FloatingText.Word w = new FloatingText.Word(i, seq, font.width(seq), 1.6f, -1);
			w.appearAt = i * 70L;
			w.row = 0;
			words.add(w);
			i++;
		}
		int titleCount = i;
		if (artist != null && !artist.isBlank()) {
			int j = 0;
			for (String t : artist.trim().split("\\s+")) {
				FormattedCharSequence seq = Component.literal(t).getVisualOrderText();
				FloatingText.Word w = new FloatingText.Word(i, seq, font.width(seq), 0.95f, 0xC9C9D6);
				w.appearAt = titleCount * 70L + 150 + j * 60L;
				w.row = 1;
				words.add(w);
				i++;
				j++;
			}
		}
		float space = font.width(" ");
		float titleWidth = 0;
		for (FloatingText.Word w : words) if (w.row == 0) titleWidth += (w.width + space) * w.scale;
		if (titleWidth > MAX_ROW_PX * 1.6f) {
			List<FloatingText.Word> titleWords = words.subList(0, titleCount);
			wrap(titleWords, space);
			int titleRows = 0;
			for (FloatingText.Word w : titleWords) titleRows = Math.max(titleRows, w.row + 1);
			for (FloatingText.Word w : words.subList(titleCount, words.size())) w.row = titleRows;
		}
		layout(words, space);
		FloatingText ft = new FloatingText(FloatingText.Kind.TITLE, words, viewYaw, 0.55f, config.distance + 1.5, trackHue, random.nextFloat() * 10);
		ft.pitchDeg = viewPitch;
		return ft;
	}

	private FloatingText spawnNotes(Font font, float viewYaw) {
		List<FloatingText.Word> words = new ArrayList<>();
		String[] glyphs = {"♪", "♫", "♪"};
		for (int i = 0; i < glyphs.length; i++) {
			FormattedCharSequence seq = Component.literal(glyphs[i]).getVisualOrderText();
			FloatingText.Word w = new FloatingText.Word(i, seq, font.width(seq), 1.7f, -1);
			w.appearAt = i * 180L;
			words.add(w);
		}
		layout(words, font.width(" ") * 2);
		float yaw = viewYaw + (random.nextFloat() - 0.5f) * 24;
		FloatingText ft = new FloatingText(FloatingText.Kind.NOTES, words, yaw, 0.05f, config.distance + 0.5, trackHue, random.nextFloat() * 10);
		ft.pitchDeg = viewPitch;
		return ft;
	}

	private static void wrap(List<FloatingText.Word> words, float space) {
		float total = 0;
		for (FloatingText.Word w : words) total += w.width * w.scale;
		total += space * Math.max(0, words.size() - 1);
		int rows = Math.max(1, (int) Math.ceil(total / MAX_ROW_PX));
		float target = total / rows;
		int row = 0;
		float rowWidth = 0;
		for (FloatingText.Word w : words) {
			float ww = w.width * w.scale;
			if (rowWidth > 0 && rowWidth + space + ww / 2 > target && row < rows - 1) {
				row++;
				rowWidth = 0;
			}
			w.row = row;
			rowWidth += (rowWidth > 0 ? space : 0) + ww;
		}
	}

	private static void layout(List<FloatingText.Word> words, float space) {
		int rows = 0;
		for (FloatingText.Word w : words) rows = Math.max(rows, w.row + 1);
		float[] rowWidth = new float[rows];
		float[] rowHeight = new float[rows];
		boolean[] started = new boolean[rows];
		for (FloatingText.Word w : words) {
			if (started[w.row]) rowWidth[w.row] += space * w.scale;
			rowWidth[w.row] += w.width * w.scale;
			rowHeight[w.row] = Math.max(rowHeight[w.row], 12 * w.scale);
			started[w.row] = true;
		}
		float totalHeight = 0;
		for (float h : rowHeight) totalHeight += h;

		float[] cursor = new float[rows];
		float[] rowCenterY = new float[rows];
		float y = -totalHeight / 2;
		for (int r = 0; r < rows; r++) {
			cursor[r] = -rowWidth[r] / 2;
			rowCenterY[r] = y + rowHeight[r] / 2;
			y += rowHeight[r];
		}
		for (FloatingText.Word w : words) {
			float ww = w.width * w.scale;
			w.cx = cursor[w.row] + ww / 2;
			w.cy = rowCenterY[w.row];
			cursor[w.row] += ww + space * w.scale;
		}
	}
}
