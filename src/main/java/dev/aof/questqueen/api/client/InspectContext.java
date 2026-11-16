package dev.aof.questqueen.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The body of the inspect card for the selected tile. Quest Queen draws the card's frame, rail, state word, title and
 * close button; the mode draws inside {@code x}, {@code y}, {@code width}, {@code height}, which is clipped.
 */
public record InspectContext(GuiGraphics graphics, Font font, String entryId, ModeTile tile, int x, int y, int width,
                             int height, int mouseX, int mouseY, float partialTick, ThemeSnapshot theme) {
    public boolean mouseInside() {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
