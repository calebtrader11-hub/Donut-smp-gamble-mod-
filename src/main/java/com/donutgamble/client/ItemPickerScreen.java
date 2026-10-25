package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.client.ui.ScaledScreen;
import com.donutgamble.client.ui.TextBox;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pick any item as an auction prize: search every item, browse the creative categories, reuse
 * recent picks or starred favorites, or take an exact item from your inventory (enchantments,
 * custom names and other components are kept). Right-click an item to star it.
 */
final class ItemPickerScreen extends ScaledScreen {
	/** What was picked; {@code exact} means keep the stack's components (it came from the inventory). */
	record Picked(ItemStack stack, boolean exact) {
	}

	private record Entry(ItemStack stack, String id, String search) {
	}

	private static final String RECENT = "Recent";
	private static final String FAVORITES = "Favorites";
	private static final String INVENTORY = "Inventory";
	private static final String ALL = "All items";
	private static final int CELL = 20;
	private static final int SIDEBAR = 92;

	/** Every item once, built on first open. */
	private static List<Entry> allItems;
	/** Creative category name → its items. Needs a world (categories use item tags), so it's built once one is loaded. */
	private static Map<String, List<Entry>> categories = new LinkedHashMap<>();
	private static String lastCategory = ALL;

	private final GambleState state;
	private final Screen parent;
	private final Consumer<Picked> onPick;
	private final Regions regions = new Regions();
	private final Anim fade = new Anim(0);
	private String category = lastCategory;
	private String query = "";
	private int scroll;
	private int wx, wy, ww, wh, gx, gy, gw, gh;
	private Entry hovered;

	ItemPickerScreen(GambleState state, Screen parent, Consumer<Picked> onPick) {
		super(Text.literal("Choose an item"), 480, 270);
		this.state = state;
		this.parent = parent;
		this.onPick = onPick;
	}

	// ---- item lists ----

	private static Entry entry(ItemStack stack) {
		String id = ItemIcons.id(stack);
		return new Entry(stack, id, (ItemIcons.name(stack) + " " + id).toLowerCase(Locale.ROOT));
	}

	private static void buildLists() {
		if (allItems == null) {
			allItems = new ArrayList<>();
			Registries.ITEM.stream().filter(i -> i != Items.AIR).forEach(i -> allItems.add(entry(new ItemStack(i))));
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (!categories.isEmpty() || client.world == null) {
			return;
		}
		try {
			FeatureSet features = client.world.getEnabledFeatures();
			ItemGroups.updateDisplayContext(features, false, ItemIcons.lookup());
			for (ItemGroup group : ItemGroups.getGroupsToDisplay()) {
				if (group.getType() != ItemGroup.Type.CATEGORY) {
					continue;
				}
				List<Entry> list = new ArrayList<>();
				for (ItemStack s : group.getDisplayStacks()) {
					list.add(entry(s));
				}
				if (!list.isEmpty()) {
					categories.put(group.getDisplayName().getString(), list);
				}
			}
		} catch (RuntimeException e) {
			DonutGambleClient.LOGGER.warn("Couldn't load item categories; showing all items only", e);
		}
	}

	private List<Entry> source() {
		return switch (category) {
			case RECENT -> fromIds(state.config.recentItems);
			case FAVORITES -> fromIds(state.config.favoriteItems);
			case INVENTORY -> inventory();
			case ALL -> allItems;
			default -> categories.getOrDefault(category, allItems);
		};
	}

	private static List<Entry> fromIds(List<String> ids) {
		List<Entry> out = new ArrayList<>();
		for (String id : ids) {
			ItemStack s = ItemIcons.fromId(id, 1);
			if (!s.isEmpty()) {
				out.add(entry(s));
			}
		}
		return out;
	}

	private static List<Entry> inventory() {
		List<Entry> out = new ArrayList<>();
		var player = MinecraftClient.getInstance().player;
		if (player == null) {
			return out;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty()) {
				out.add(entry(s.copy()));
			}
		}
		return out;
	}

	private List<Entry> visible() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		// Searching looks through every item unless a personal list is open.
		List<Entry> base = !q.isEmpty() && !category.equals(RECENT) && !category.equals(FAVORITES) && !category.equals(INVENTORY)
			? allItems : source();
		if (q.isEmpty()) {
			return base;
		}
		List<Entry> out = new ArrayList<>();
		for (Entry e : base) {
			if (e.search().contains(q)) {
				out.add(e);
			}
		}
		return out;
	}

	private List<String> categoryNames() {
		List<String> names = new ArrayList<>(List.of(RECENT, FAVORITES, INVENTORY, ALL));
		names.addAll(categories.keySet());
		return names;
	}

	// ---- layout ----

	@Override
	protected void layout() {
		buildLists();
		ww = Math.min(vw - 16, 500);
		wh = Math.min(vh - 12, 296);
		wx = (vw - ww) / 2;
		wy = (vh - wh) / 2;
		gx = wx + SIDEBAR + 10;
		gy = wy + 50;
		gw = wx + ww - 10 - gx;
		gh = wy + wh - 32 - gy;

		TextBox search = new TextBox(textRenderer, gx, wy + 28, gw, "Search any item, e.g. spawner, totem, netherite");
		search.setText(query);
		search.setChangedListener(s -> {
			query = s;
			scroll = 0;
		});
		addDrawableChild(search);
		setInitialFocus(search);

		Button cancel = Button.fit(0, wy + wh - 24, "Cancel", Button.Variant.GHOST, this::close);
		cancel.setX(wx + ww - 10 - cancel.getWidth());
		addDrawableChild(cancel);
	}

	private void pick(Entry e) {
		boolean exact = category.equals(INVENTORY);
		state.config.rememberItem(e.id());
		state.save();
		lastCategory = category;
		MinecraftClient.getInstance().setScreen(parent);
		onPick.accept(new Picked(e.stack().copy(), exact));
	}

	@Override
	public void close() {
		lastCategory = category;
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	protected boolean onMouseClicked(Click click, boolean doubled) {
		return super.onMouseClicked(click, doubled) || regions.click(click.x(), click.y(), click.button());
	}

	@Override
	protected boolean onMouseScrolled(double mx, double my, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical));
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---- drawing ----

	@Override
	protected void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		regions.clear();
		hovered = null;
		float t = Anim.ease(fade.to(1f, 14f));
		ctx.fill(0, 0, vw, vh, Colors.alpha(Colors.SCRIM, Math.round(0x8C * t)));
		Paint.window(ctx, wx, wy, ww, wh);
		Paint.text(ctx, textRenderer, "Choose an item", wx + 12, wy + 10, Colors.INK);
		Paint.text(ctx, textRenderer, "Right-click to star", wx + 12 + textRenderer.getWidth("Choose an item") + 8, wy + 10, Colors.INK_FAINT);
		Paint.hline(ctx, wx + 1, wx + ww - 1, wy + 22, Colors.LINE);

		// Categories
		ctx.fill(wx + 1, wy + 23, wx + SIDEBAR, wy + wh - 4, Colors.alpha(Colors.INPUT, 0x90));
		ctx.fill(wx + SIDEBAR, wy + 23, wx + SIDEBAR + 1, wy + wh - 1, Colors.LINE);
		int cy = wy + 30;
		for (String name : categoryNames()) {
			if (cy > wy + wh - 16) {
				break;
			}
			boolean on = name.equals(category) && query.isBlank() || name.equals(category) && isPersonal(name);
			boolean hover = regions.hovered(mouseX, mouseY, wx + 4, cy - 3, SIDEBAR - 8, 13);
			if (name.equals(category)) {
				Paint.round(ctx, wx + 4, cy - 3, SIDEBAR - 8, 13, 3, Colors.alpha(Colors.accent(), on ? 0x30 : 0x18));
			} else if (hover) {
				Paint.round(ctx, wx + 4, cy - 3, SIDEBAR - 8, 13, 3, Colors.alpha(Colors.CARD_HI, 0xC0));
			}
			Paint.text(ctx, textRenderer, Paint.fit(textRenderer, name, SIDEBAR - 16), wx + 10, cy,
				name.equals(category) || hover ? Colors.INK : Colors.INK_DIM);
			regions.add(wx + 4, cy - 3, SIDEBAR - 8, 13, () -> {
				category = name;
				scroll = 0;
			});
			cy += 13;
			if (name.equals(ALL)) {
				Paint.hline(ctx, wx + 8, wx + SIDEBAR - 8, cy - 2, Colors.LINE);
				cy += 3;
			}
		}

		// Grid
		Paint.card(ctx, gx, gy, gw, gh, 4, Colors.CARD, Colors.LINE);
		List<Entry> items = visible();
		int cols = Math.max(1, (gw - 8) / CELL);
		int rows = Math.max(1, (gh - 8) / CELL);
		int totalRows = (items.size() + cols - 1) / cols;
		scroll = Math.min(scroll, Math.max(0, totalRows - rows));
		int ox = gx + (gw - cols * CELL) / 2;
		int oy = gy + 4;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				int i = (scroll + r) * cols + c;
				if (i >= items.size()) {
					break;
				}
				Entry e = items.get(i);
				int x = ox + c * CELL;
				int y = oy + r * CELL;
				boolean hover = regions.hovered(mouseX, mouseY, x, y, CELL, CELL);
				if (hover) {
					Paint.round(ctx, x, y, CELL, CELL, 3, Colors.alpha(Colors.accent(), 0x40));
					hovered = e;
				}
				ctx.drawItem(e.stack(), x + 2, y + 2);
				if (category.equals(INVENTORY) && e.stack().getCount() > 1) {
					ctx.drawStackOverlay(textRenderer, e.stack(), x + 2, y + 2);
				}
				if (state.config.favoriteItems.contains(e.id())) {
					Paint.round(ctx, x + CELL - 5, y + 1, 4, 4, 2, Colors.GOLD);
				}
				regions.add(x, y, CELL, CELL, () -> pick(e), () -> {
					state.config.toggleFavorite(e.id());
					state.save();
				});
			}
		}
		if (totalRows > rows) {
			int trackH = gh - 8;
			int thumbH = Math.max(10, trackH * rows / totalRows);
			int thumbY = gy + 4 + (trackH - thumbH) * scroll / Math.max(1, totalRows - rows);
			Paint.round(ctx, gx + gw - 4, thumbY, 2, thumbH, 1, Colors.LINE_HI);
		}
		if (items.isEmpty()) {
			String msg = switch (category) {
				case RECENT -> "Items you auction show up here";
				case FAVORITES -> "Right-click any item to star it";
				case INVENTORY -> MinecraftClient.getInstance().player == null ? "Join a world to pick from your inventory" : "Your inventory is empty";
				default -> "No items match \"" + query.trim() + "\"";
			};
			Paint.center(ctx, textRenderer, msg, gx + gw / 2, gy + gh / 2 - 4, Colors.INK_FAINT);
		}

		// Footer: what's under the mouse
		int fy = wy + wh - 20;
		if (hovered != null) {
			Paint.text(ctx, textRenderer, Paint.fit(textRenderer, ItemIcons.name(hovered.stack()), gw / 2), gx, fy, Colors.INK);
			Paint.text(ctx, textRenderer, hovered.id(), gx + Math.min(gw / 2, textRenderer.getWidth(ItemIcons.name(hovered.stack()))) + 8, fy, Colors.INK_FAINT);
		} else {
			String count = items.size() + (items.size() == 1 ? " item" : " items");
			Paint.text(ctx, textRenderer, count + (category.equals(INVENTORY) ? " · keeps enchantments and names" : ""), gx, fy, Colors.INK_FAINT);
		}
	}

	private static boolean isPersonal(String name) {
		return name.equals(RECENT) || name.equals(FAVORITES) || name.equals(INVENTORY);
	}
}
