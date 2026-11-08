package moze_intel.projecte.client;

import moze_intel.projecte.EmcMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

final class EmcMachineScreen extends AbstractContainerScreen<EmcMachineMenu> {
    private static final int TEXT = 0xff404040;

    EmcMachineScreen(EmcMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, EmcMachineMenu.WIDTH, EmcMachineMenu.HEIGHT);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CondenserScreen.panel(graphics, leftPos, topPos, imageWidth, imageHeight, 0xffc6c6c6, 0xfff6f6f6, 0xff555555);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            CondenserScreen.panel(graphics, leftPos + slot.x - 1, topPos + slot.y - 1,
                    18, 18, 0xff8b8b8b, 0xff555555, 0xfff6f6f6);
        }
        centered(graphics, title, 88, 7);
        centered(graphics, Component.literal(menu.storedEmc() + " / " + menu.capacity() + " EMC"), 88, 20);
        bar(graphics, 8, 30, 160, menu.storedEmc(), menu.capacity(), 0xffe7b44b);
        if (menu.isCollector()) {
            centered(graphics, Component.literal("Light " + menu.lightLevel() + " / 15"), 88, 42);
            bar(graphics, 49, 52, 78, menu.lightLevel(), 15, 0xffffe6a4);
            centered(graphics, Component.literal("Fuel"), 24, 84);
            centered(graphics, Component.literal("Klein Star"), 88, 84);
            centered(graphics, Component.literal("Output"), 152, 84);
        } else {
            centered(graphics, Component.literal("Burn input"), 56, 84);
            centered(graphics, Component.literal("Klein Star"), 120, 84);
        }
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }

    private void centered(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(font, text, leftPos + x - font.width(text) / 2, topPos + y, TEXT, false);
    }

    private void bar(GuiGraphicsExtractor graphics, int x, int y, int width, long value, long max, int color) {
        int left = leftPos + x, top = topPos + y;
        CondenserScreen.panel(graphics, left, top, width, 7, 0xff777260, 0xff555555, 0xfff6f6f6);
        int filled = max > 0 ? (int) Math.round((width - 2) * Math.min(1, (double) value / max)) : 0;
        if (filled > 0) graphics.fill(left + 1, top + 1, left + 1 + filled, top + 6, color);
    }
}
