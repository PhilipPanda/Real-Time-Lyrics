package com.realtimelyrics;

import com.mojang.blaze3d.platform.InputConstants;
import com.realtimelyrics.media.MediaSource;
import com.realtimelyrics.render.LyricsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RealTimeLyricsClient implements ClientModInitializer {
	public static final String MOD_ID = "realtimelyrics";
	public static final Logger LOG = LoggerFactory.getLogger("RealTimeLyrics");

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"));

	private LyricsConfig config;
	private MediaSource source;
	private LyricsController controller;

	private KeyMapping toggleKey;
	private KeyMapping earlierKey;
	private KeyMapping laterKey;
	private KeyMapping restartKey;
	private KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		config = LyricsConfig.load();
		source = MediaSource.forThisOs();
		LyricsRenderer renderer = new LyricsRenderer(config);
		controller = new LyricsController(config, source, renderer);

		toggleKey = register("toggle", GLFW.GLFW_KEY_K);
		earlierKey = register("earlier", GLFW.GLFW_KEY_RIGHT_BRACKET);
		laterKey = register("later", GLFW.GLFW_KEY_LEFT_BRACKET);
		restartKey = register("restart", GLFW.GLFW_KEY_BACKSLASH);
		settingsKey = register("settings", GLFW.GLFW_KEY_UNKNOWN);

		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				Screens.getButtons(screen).add(Button.builder(Component.literal("♫ Lyrics"),
								b -> mc.setScreen(new LyricsConfigScreen(screen, config)))
						.bounds(8, 8, 80, 20)
						.tooltip(Tooltip.create(Component.literal("Real Time Lyrics settings")))
						.build());
			}
		});

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> source.start());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			source.stop();
			config.save();
		});
		Runtime.getRuntime().addShutdownHook(new Thread(source::stop, "RealTimeLyrics-Shutdown"));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			handleKeys(mc);
			controller.tick(mc);
			config.saveIfDirty();
		});
		WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> renderer.render(ctx, controller));

		LOG.info("Real Time Lyrics ready");
	}

	private static KeyMapping register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private void handleKeys(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			config.enabled = !config.enabled;
			config.save();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal(config.enabled ? "♫ Lyrics on" : "Lyrics off")
						.withStyle(config.enabled ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
			}
		}
		while (earlierKey.consumeClick()) controller.nudge(mc, 200);
		while (laterKey.consumeClick()) controller.nudge(mc, -200);
		while (restartKey.consumeClick()) controller.restartTiming(mc);
		while (settingsKey.consumeClick()) mc.setScreen(new LyricsConfigScreen(mc.screen, config));
	}
}
