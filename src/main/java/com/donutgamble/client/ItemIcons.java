package com.donutgamble.client;

import com.donutgamble.core.Auction;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns saved auction prizes back into real item stacks for drawing, and back again. An item
 * picked from the inventory is stored with all its components (enchantments, custom name, …) as
 * JSON, so it renders exactly as it was, glint included.
 */
final class ItemIcons {
	private static RegistryWrapper.WrapperLookup builtin;
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private ItemIcons() {
	}

	/** Registries for encoding components: the world's when in one, otherwise the built-in set. */
	static RegistryWrapper.WrapperLookup lookup() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			return client.world.getRegistryManager();
		}
		if (builtin == null) {
			builtin = BuiltinRegistries.createWrapperLookup();
		}
		return builtin;
	}

	static String id(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()).toString();
	}

	static String name(ItemStack stack) {
		return stack.getName().getString();
	}

	/** A plain stack of the item with this id, or empty if the id isn't a known item. */
	static ItemStack fromId(String id, int count) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier ident = Identifier.tryParse(id);
		if (ident == null) {
			return ItemStack.EMPTY;
		}
		Optional<Item> item = Registries.ITEM.getOptionalValue(ident);
		return item.filter(i -> i != Items.AIR).map(i -> new ItemStack(i, Math.max(1, count))).orElse(ItemStack.EMPTY);
	}

	/** The exact stack, components included, as JSON; null if it can't be encoded. */
	static String encode(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		try {
			return ItemStack.CODEC.encodeStart(lookup().getOps(JsonOps.INSTANCE), stack).result().map(JsonElement::toString).orElse(null);
		} catch (RuntimeException e) {
			DonutGambleClient.LOGGER.warn("Could not save item {}", id(stack), e);
			return null;
		}
	}

	static ItemStack decode(String json) {
		if (json == null || json.isBlank()) {
			return ItemStack.EMPTY;
		}
		try {
			return ItemStack.CODEC.parse(lookup().getOps(JsonOps.INSTANCE), JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	/** The prize to draw for an auction (empty for a text-only auction). Cached. */
	static ItemStack of(Auction a) {
		if (a == null || (a.itemId == null && a.stackData == null)) {
			return ItemStack.EMPTY;
		}
		String key = a.id + "|" + a.itemId + "|" + a.quantity + "|" + (a.stackData == null ? 0 : a.stackData.hashCode());
		return CACHE.computeIfAbsent(key, k -> {
			ItemStack exact = decode(a.stackData);
			return exact.isEmpty() ? fromId(a.itemId, a.quantity) : exact;
		});
	}
}
