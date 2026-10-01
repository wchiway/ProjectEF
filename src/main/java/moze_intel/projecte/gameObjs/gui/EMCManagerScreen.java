package moze_intel.projecte.gameObjs.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import moze_intel.projecte.client.EMCManagerClient;
import moze_intel.projecte.network.PENetwork;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT.Status;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EMCManagerScreen extends Screen {

	private final Screen parent;
	private final List<ItemEntry> items = new ArrayList<>();
	private final List<ItemButton> rows = new ArrayList<>();
	private List<ItemEntry> filtered = List.of();
	private ItemStack selected = new ItemStack(Items.DIAMOND);
	private EditBox searchBox, valueBox;
	private Button save, remove, reset, apply, previous, next, held;
	private String searchText = "", valueText = "", serverValueText = "";
	private Component status = text("waiting");
	private boolean error, busy, loaded, pending, tooSmall;
	private int left, top, panelWidth, panelHeight, listWidth, editorX, editorWidth, page, pageSize;
	private int permissions, requestId = -1, waitingTicks;
	private long currentValue, customValue = -1;
	private Action lastAction = Action.QUERY;

	public EMCManagerScreen(@Nullable Screen parent) {
		super(text("title"));
		this.parent = parent;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && !client.player.getMainHandItem().isEmpty()) {
			selected = new ItemStack(client.player.getMainHandItem().getItem());
		}
		BuiltInRegistries.ITEM.forEach(item -> {
			if (item != Items.AIR && client.level != null && item.isEnabled(client.level.enabledFeatures())) {
				ItemStack stack = new ItemStack(item);
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				items.add(new ItemEntry(stack, id, stack.getHoverName().getString().toLowerCase(Locale.ROOT)));
			}
		});
		items.sort(Comparator.comparing(ItemEntry::id));
	}

	private static Component text(String key, Object... args) {
		return Component.translatable("gui.projecte.emc_manager." + key, args);
	}

	@Override
	protected void init() {
		tooSmall = width < 300 || height < 240;
		rows.clear();
		if (tooSmall) {
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 50, height - 30, 100, 20).build());
			return;
		}
		panelWidth = Math.min(620, width - 16);
		panelHeight = Math.min(360, height - 12);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		listWidth = (panelWidth - 30) / 2;
		editorX = left + listWidth + 20;
		editorWidth = panelWidth - listWidth - 30;
		pageSize = Math.max(1, (panelHeight - 112) / 24);

		held = addRenderableWidget(Button.builder(text("held"), button -> selectHeld()).bounds(left + panelWidth - 88, top + 5, 78, 20)
				.tooltip(Tooltip.create(text("base_only"))).build());
		searchBox = addRenderableWidget(new EditBox(font, left + 10, top + 37, listWidth, 18, text("search")));
		searchBox.setMaxLength(128);
		searchBox.setHint(text("search"));
		searchBox.setValue(searchText);
		searchBox.setResponder(value -> {
			searchText = value;
			page = 0;
			filterItems();
		});
		for (int index = 0; index < pageSize; index++) {
			rows.add(addRenderableWidget(new ItemButton(left + 10, top + 60 + index * 24, listWidth)));
		}
		valueBox = addRenderableWidget(new EditBox(font, editorX, top + 112, editorWidth, 18, text("value")));
		valueBox.setMaxLength(19);
		valueBox.setFilter(value -> value.chars().allMatch(character -> character >= '0' && character <= '9'));
		valueBox.setValue(valueText);
		valueBox.setResponder(value -> {
			valueText = value;
			updateButtons();
		});
		save = addRenderableWidget(Button.builder(text("save"), button -> request(Action.SET)).bounds(editorX, top + 134, editorWidth, 20).build());
		int half = (editorWidth - 4) / 2;
		remove = addRenderableWidget(Button.builder(text("remove"), button -> confirm(Action.REMOVE))
				.bounds(editorX, top + 158, half, 20).tooltip(Tooltip.create(text("remove_help"))).build());
		reset = addRenderableWidget(Button.builder(text("reset"), button -> confirm(Action.RESET))
				.bounds(editorX + half + 4, top + 158, editorWidth - half - 4, 20).tooltip(Tooltip.create(text("reset_help"))).build());
		int footer = top + panelHeight - 26;
		previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1)).bounds(left + 10, footer, 22, 20).build());
		next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1)).bounds(left + listWidth - 12, footer, 22, 20).build());
		apply = addRenderableWidget(Button.builder(text("apply"), button -> request(Action.APPLY)).bounds(editorX, footer, half, 20)
				.tooltip(Tooltip.create(text("apply_help"))).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(editorX + half + 4, footer, editorWidth - half - 4, 20).build());
		filterItems();
		setInitialFocus(searchBox);
		if (requestId == -1) {
			request(Action.QUERY);
		}
	}

	private void filterItems() {
		String query = searchText.strip().toLowerCase(Locale.ROOT);
		filtered = items.stream().filter(entry -> entry.id().contains(query) || entry.name().contains(query)).toList();
		page = Math.min(page, Math.max(0, (filtered.size() - 1) / pageSize));
		for (int index = 0; index < rows.size(); index++) {
			int itemIndex = page * pageSize + index;
			ItemButton row = rows.get(index);
			row.entry = itemIndex < filtered.size() ? filtered.get(itemIndex) : null;
			row.visible = row.entry != null;
			if (row.entry != null) {
				row.setMessage(row.entry.stack().getHoverName().copy().append(" (" + row.entry.id() + ")"));
				row.setTooltip(Tooltip.create(Component.literal(row.entry.id())));
			}
		}
		updateButtons();
	}

	private void changePage(int delta) {
		page += delta;
		filterItems();
	}

	private void selectHeld() {
		if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
			select(new ItemStack(minecraft.player.getMainHandItem().getItem()));
		}
	}

	private void select(ItemStack stack) {
		if (!busy) {
			selected = stack.copy();
			loaded = false;
			request(Action.QUERY);
		}
	}

	private void request(Action action) {
		if (busy) {
			return;
		}
		if (!EMCManagerClient.supported()) {
			status = text("unsupported");
			error = true;
			return;
		}
		long value = parsedValue();
		if (action == Action.SET && value <= 0) {
			return;
		}
		requestId = EMCManagerClient.nextRequestId();
		lastAction = action;
		busy = true;
		waitingTicks = 0;
		error = false;
		status = text(action == Action.APPLY ? "applying" : "waiting");
		PENetwork.sendToServer(new EMCManagerRequestPKT(requestId, action, BuiltInRegistries.ITEM.getKey(selected.getItem()), value, customValue));
		updateButtons();
	}

	public void receive(EMCManagerResponsePKT response) {
		if (response.requestId() != requestId || !response.item().equals(BuiltInRegistries.ITEM.getKey(selected.getItem()))) {
			return;
		}
		busy = false;
		permissions = response.permissions();
		pending = response.pending();
		currentValue = response.currentValue();
		customValue = response.customValue();
		status = response.status().message();
		error = response.status().isError();
		loaded = permissions != 0 && response.status() != Status.NOT_READY && response.status() != Status.INVALID_ITEM;
		if (!error || response.status() == Status.CONFLICT || lastAction == Action.QUERY) {
			valueText = customValue > 0 ? Long.toString(customValue) : customValue == 0 ? "" : currentValue > 0 ? Long.toString(currentValue) : "";
			serverValueText = valueText;
			if (valueBox != null) {
				valueBox.setValue(valueText);
			}
		}
		updateButtons();
	}

	private long parsedValue() {
		try {
			return Long.parseLong(valueText);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void updateButtons() {
		if (save == null || tooSmall) {
			return;
		}
		boolean editable = loaded && !busy;
		valueBox.setEditable(editable && (permissions & EMCManagerResponsePKT.CAN_SET) != 0);
		save.active = editable && (permissions & EMCManagerResponsePKT.CAN_SET) != 0 && parsedValue() > 0;
		remove.active = editable && (permissions & EMCManagerResponsePKT.CAN_REMOVE) != 0 && customValue != 0;
		reset.active = editable && (permissions & EMCManagerResponsePKT.CAN_RESET) != 0 && customValue != -1;
		apply.active = editable && permissions == EMCManagerResponsePKT.ALL_PERMISSIONS;
		previous.active = !busy && page > 0;
		next.active = !busy && (page + 1) * pageSize < filtered.size();
		held.active = !busy && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty();
		for (ItemButton row : rows) {
			row.active = !busy;
		}
	}

	private void confirm(Action action) {
		minecraft.setScreen(new ConfirmScreen(confirmed -> {
			minecraft.setScreen(this);
			if (confirmed) {
				request(action);
			}
		}, text(action == Action.REMOVE ? "remove" : "reset"), text(action == Action.REMOVE ? "confirm_remove" : "confirm_reset", selected.getHoverName())));
	}

	@Override
	public void onClose() {
		if (busy) {
			return;
		}
		if (pending || !valueText.equals(serverValueText)) {
			minecraft.setScreen(new ConfirmScreen(confirmed -> minecraft.setScreen(confirmed ? parent : this), text("title"), text("confirm_close")));
		} else {
			minecraft.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (busy && ++waitingTicks > 600) {
			busy = false;
			loaded = false;
			error = true;
			status = text("timeout");
			updateButtons();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (!tooSmall && !busy && mouseX >= left + 10 && mouseX < left + 10 + listWidth && mouseY >= top + 60 && mouseY < top + panelHeight - 46) {
			if (vertical < 0 && next.active) {
				changePage(1);
			} else if (vertical > 0 && previous.active) {
				changePage(-1);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		//Screen.render draws the background before its widgets. Draw our labels only after that pass.
		super.render(graphics, mouseX, mouseY, partialTick);
		if (tooSmall) {
			graphics.drawCenteredString(font, text("too_small"), width / 2, height / 2, 0xFFFFFF);
			return;
		}
		graphics.drawString(font, title, left + 10, top + 8, 0xF4DB98, false);
		drawClipped(graphics, text("base_only"), left + 10, top + 24, panelWidth - 20, 0xACB6C8, mouseX, mouseY);
		graphics.renderItem(selected, editorX, top + 38);
		drawClipped(graphics, selected.getHoverName(), editorX + 22, top + 42, editorWidth - 22, 0xFFFFFF, mouseX, mouseY);
		drawClipped(graphics, Component.literal(BuiltInRegistries.ITEM.getKey(selected.getItem()).toString()), editorX, top + 61, editorWidth, 0xACB6C8, mouseX, mouseY);
		drawClipped(graphics, text("current", loaded ? Long.toString(currentValue) : "—"), editorX, top + 75, editorWidth, 0xF4DB98, mouseX, mouseY);
		drawClipped(graphics, text("override", !loaded ? Component.literal("—") : customValue < 0 ? text("default") : Component.literal(Long.toString(customValue))),
				editorX, top + 89, editorWidth, 0xACB6C8, mouseX, mouseY);
		drawClipped(graphics, text("value"), editorX, top + 101, editorWidth, 0xFFFFFF, mouseX, mouseY);
		boolean invalidValue = !busy && loaded && !valueText.isEmpty() && parsedValue() <= 0;
		Component feedback = invalidValue ? Status.INVALID_VALUE.message() : !error && !busy && pending ? text("pending") : status;
		drawClipped(graphics, feedback, left + 10, top + panelHeight - 40, panelWidth - 20, error || invalidValue ? 0xFF8888 : 0xE0CC93, mouseX, mouseY);
		graphics.drawCenteredString(font, text("page", page + 1, Math.max(1, (filtered.size() + pageSize - 1) / pageSize)),
				left + 10 + listWidth / 2, top + panelHeight - 20, 0xFFFFFF);
		if (filtered.isEmpty()) {
			graphics.drawCenteredString(font, text("no_results"), left + 10 + listWidth / 2, top + 70, 0xACB6C8);
		}
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		if (!tooSmall) {
			graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0181C26);
			graphics.fill(left, top, left + panelWidth, top + 2, 0xFFC7A75B);
			graphics.fill(editorX - 6, top + 36, editorX - 5, top + panelHeight - 48, 0xFF3C4351);
		}
	}

	private void drawClipped(GuiGraphics graphics, Component component, int x, int y, int availableWidth, int color, int mouseX, int mouseY) {
		String value = component.getString();
		boolean clipped = font.width(value) > availableWidth;
		graphics.drawString(font, clipped ? font.plainSubstrByWidth(value, availableWidth - font.width("…")) + "…" : value, x, y, color, false);
		if (clipped && mouseX >= x && mouseX < x + availableWidth && mouseY >= y && mouseY < y + font.lineHeight) {
			setTooltipForNextRenderPass(component);
		}
	}

	private record ItemEntry(ItemStack stack, String id, String name) {
	}

	private class ItemButton extends Button {

		private ItemEntry entry;

		private ItemButton(int x, int y, int width) {
			super(x, y, width, 22, Component.empty(), button -> {}, DEFAULT_NARRATION);
		}

		@Override
		public void onPress() {
			if (entry != null) {
				select(entry.stack());
			}
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			if (entry == null) {
				return;
			}
			boolean chosen = selected.is(entry.stack().getItem());
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), chosen ? 0xFF49432E : isHoveredOrFocused() ? 0xFF354052 : 0xFF242C3A);
			if (chosen || isFocused()) {
				graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFC7A75B);
			}
			graphics.renderItem(entry.stack(), getX() + 3, getY() + 3);
			graphics.drawString(font, font.plainSubstrByWidth(entry.stack().getHoverName().getString(), getWidth() - 26), getX() + 24, getY() + 3,
					active ? 0xFFFFFF : 0x888888, false);
			graphics.drawString(font, font.plainSubstrByWidth(entry.id(), getWidth() - 26), getX() + 24, getY() + 12, 0x96A4BB, false);
		}
	}
}
