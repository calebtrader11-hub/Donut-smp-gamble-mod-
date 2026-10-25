package com.donutgamble.client;

import com.donutgamble.core.NotificationSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvents;

/** Quiet UI sounds for notifications. Never more than one per 150 ms so bursts don't stack up. */
final class Sounds {
	private static final long GAP_MS = 150;
	private static long last;

	private Sounds() {
	}

	static void play(NotificationSettings.Kind kind, NotificationSettings settings) {
		if (settings == null || !settings.sounds) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - last < GAP_MS) {
			return;
		}
		SoundInstance sound = switch (kind) {
			case PAYMENT_RECEIVED -> PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f, 0.25f);
			case PAYMENT_SENT -> PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 0.2f);
			case AUCTION_BID -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.6f, 0.25f);
			case AUCTION_LEADER -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f, 0.3f);
			case AUCTION_ENDING -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.2f, 0.3f);
			case AUCTION_COMPLETED, GAME_COMPLETED -> PositionedSoundInstance.ui(SoundEvents.UI_TOAST_IN, 1f, 0.6f);
			case PAYOUT_DUE -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1f, 0.3f);
			case GAME_STARTED -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.9f, 0.2f);
			case SYSTEM -> null;
		};
		MinecraftClient client = MinecraftClient.getInstance();
		if (sound == null || client == null || client.getSoundManager() == null) {
			return;
		}
		last = now;
		client.getSoundManager().play(sound);
	}
}
