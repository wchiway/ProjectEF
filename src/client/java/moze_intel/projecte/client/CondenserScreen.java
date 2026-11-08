package moze_intel.projecte.client;

import moze_intel.projecte.CondenserMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

final class CondenserScreen extends AbstractContainerScreen<CondenserMenu> {
    private static final int BAR_X = 33, BAR_Y = 9, BAR_WIDTH = 106, BAR_HEIGHT = 13;
    private static final int LIGHT = 0xfff6f6f6, DARK = 0xff555555, TEXT = 0xff404040;

    CondenserScreen(CondenserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CondenserMenu.WIDTH, CondenserMenu.HEIGHT);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight, 0xffc6c6c6, LIGHT, DARK);
        for (Slot slot : menu.slots) {
            panel(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, 0xff8b8b8b, DARK, LIGHT);
        }

        long emc = menu.storedEmc();
        long cost = menu.targetEmc();
        int barLeft = leftPos + BAR_X;
        int barTop = topPos + BAR_Y;
        panel(graphics, barLeft, barTop, BAR_WIDTH, BAR_HEIGHT, 0xffa09a7d, 0xff777260, LIGHT);
        int filled = cost > 0 ? (int) Math.round((BAR_WIDTH - 4) * Math.min(1, (double) emc / cost)) : 0;
        if (filled > 0) {
            graphics.fillGradient(barLeft + 2, barTop + 2, barLeft + 2 + filled, barTop + BAR_HEIGHT - 2,
                    0xfffff1a5, 0xffdba53e);
        }
        String amount = Long.toString(emc);
        if (font.width(amount) > 61) amount = font.plainSubstrByWidth(amount, 54) + "…";
        graphics.text(font, amount, leftPos + 144, topPos + 11, TEXT, false);
        drawHint(graphics, menu.accountSlot(), "↓");
        drawHint(graphics, menu.starSlot(), "✦");
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        if (overEmpty(menu.targetSlot(), mouseX, mouseY)) {
            tooltip(graphics, "Target item to duplicate", mouseX, mouseY);
        } else if (overEmpty(menu.accountSlot(), mouseX, mouseY)) {
            tooltip(graphics, "Account input: deposits EMC when the target is empty", mouseX, mouseY);
        } else if (overEmpty(menu.starSlot(), mouseX, mouseY)) {
            tooltip(graphics, "Klein Star charging slot", mouseX, mouseY);
        } else if (hit(mouseX, mouseY, BAR_X, BAR_Y, BAR_WIDTH, BAR_HEIGHT)) {
            long cost = menu.targetEmc();
            tooltip(graphics, cost > 0 ? menu.storedEmc() + " / " + cost + " EMC"
                    : menu.storedEmc() + " EMC stored", mouseX, mouseY);
        }
    }

    private void drawHint(GuiGraphicsExtractor graphics, Slot slot, String symbol) {
        if (!slot.hasItem()) {
            graphics.text(font, symbol, leftPos + slot.x + (16 - font.width(symbol)) / 2,
                    topPos + slot.y + 4, TEXT, false);
        }
    }

    private boolean overEmpty(Slot slot, int mouseX, int mouseY) {
        return !slot.hasItem() && hit(mouseX, mouseY, slot.x, slot.y, 16, 16);
    }

    private boolean hit(int mouseX, int mouseY, int x, int y, int width, int height) {
        int relativeX = mouseX - leftPos, relativeY = mouseY - topPos;
        return relativeX >= x && relativeX < x + width && relativeY >= y && relativeY < y + height;
    }

    private void tooltip(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.setTooltipForNextFrame(font, Component.literal(text), x, y);
    }

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int base, int topLeft, int bottomRight) {
        graphics.fill(x, y, x + width, y + height, base);
        graphics.fill(x, y, x + width, y + 1, topLeft);
        graphics.fill(x, y, x + 1, y + height, topLeft);
        graphics.fill(x, y + height - 1, x + width, y + height, bottomRight);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, bottomRight);
    }
}
