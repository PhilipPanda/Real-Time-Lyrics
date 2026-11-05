package com.realtimelyrics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;

public final class LyricsConfigScreen extends Screen {
	private static final int W = 150;
	private static final int H = 20;
	private static final int GAP = 4;

	private final Screen parent;
	private final LyricsConfig config;
	private int index;

	public LyricsConfigScreen(Screen parent, LyricsConfig config) {
		super(Component.literal("♫ Real Time Lyrics"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		index = 0;

		toggle("Lyrics", config.enabled, v -> config.enabled = v, "Show lyrics in the world.");
		toggle("Follow Me", config.followPlayer, v -> config.followPlayer = v,
				"ON: lines move along with you as you walk.\nOFF: each line stays where it appeared.");
		toggle("Floating Motion", config.floatingMotion, v -> config.floatingMotion = v, "Gentle bobbing of lines and words.");
		toggle("Glow Outline", config.glowOutline, v -> config.glowOutline = v, "Colored glowing outline around words.");
		toggle("Particles", config.particles, v -> config.particles = v, "Sparkles and music notes when words appear.");
		toggle("Song Title Card", config.showTitleCard, v -> config.showTitleCard = v, "Big title + artist when a new song starts.");
		toggle("See Through Walls", config.seeThroughWalls, v -> config.seeThroughWalls = v,
				"Draw lyrics through blocks. Turns off the glow outline.");
		toggle("Status Messages", config.statusMessages, v -> config.statusMessages = v,
				"Messages above the hotbar (searching, not found, timing).");

		CycleButton<LyricsConfig.ColorStyle> colors = CycleButton.builder(
						(LyricsConfig.ColorStyle s) -> Component.literal(s.label), config.colorStyle)
				.withValues(LyricsConfig.ColorStyle.values())
				.create(x(), y(), W, H, Component.literal("Colors"), (b, v) -> {
					config.colorStyle = v;
					config.markDirty();
				});
		addRenderableWidget(colors);
		index++;

		slider("Text Size", 0.4, 3.0, config.textScale, v -> config.textScale = v, v -> String.format("%.0f%%", v * 100));
		slider("Distance", 2.0, 12.0, config.distance, v -> config.distance = v, v -> String.format("%.1f blocks", v));
		slider("Height", -1.5, 2.5, config.heightOffset, v -> config.heightOffset = v, v -> String.format("%+.1f blocks", v));
		slider("Spread", 0, 40, config.spreadDegrees, v -> config.spreadDegrees = v, v -> String.format("%.0f°", v));
		slider("Timing", -3000, 3000, config.globalOffsetMs, v -> config.globalOffsetMs = (int) (Math.round(v / 50.0) * 50),
				v -> String.format("%+.2fs %s", Math.round(v / 50.0) * 50 / 1000.0, Math.abs(v) < 25 ? "" : v > 0 ? "sooner" : "later"));

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, Math.min(height - 28, y() + (index % 2 == 1 ? H + GAP : 0) + 8), 200, H).build());
	}

	private int top() {
		int rows = 7;
		int block = rows * (H + GAP);
		return Math.max(32, (height - block) / 2 - 10);
	}

	private int x() {
		return width / 2 - W - GAP / 2 + (index % 2) * (W + GAP);
	}

	private int y() {
		return top() + (index / 2) * (H + GAP);
	}

	private void toggle(String label, boolean value, Consumer<Boolean> set, String tooltip) {
		CycleButton<Boolean> b = CycleButton.onOffBuilder(value)
				.create(x(), y(), W, H, Component.literal(label), (btn, v) -> {
					set.accept(v);
					config.markDirty();
				});
		b.setTooltip(Tooltip.create(Component.literal(tooltip)));
		addRenderableWidget(b);
		index++;
	}

	private void slider(String label, double min, double max, double value, Consumer<Double> set, DoubleFunction<String> format) {
		double norm = Math.max(0, Math.min(1, (value - min) / (max - min)));
		addRenderableWidget(new AbstractSliderButton(x(), y(), W, H, Component.empty(), norm) {
			{
				updateMessage();
			}

			private double real() {
				return min + (max - min) * this.value;
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + format.apply(real())));
			}

			@Override
			protected void applyValue() {
				set.accept(real());
				config.markDirty();
			}
		});
		index++;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, top() - 22, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.literal("Keys: K toggle · [ ] nudge timing · \\ song restarted"),
				width / 2, top() - 11, 0xFFA0A0A0);
	}

	@Override
	public void removed() {
		config.save();
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.setScreen(parent);
	}
}
