package dev.aof.questqueen.api.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing over one tile, right after Quest Queen draws it (and for HIDDEN tiles, over their empty cell).
 * {@code x}, {@code y} and {@code size} are the tile's square on screen; the board is clipped.
 */
public record TileOverlayContext(GuiGraphics graphics, String entryId, ModeTile tile, int x, int y, int size,
                                 boolean hovered, boolean selected, int mouseX, int mouseY, float partialTick,
                                 ThemeSnapshot theme) {
    public int centerX() {
        return x + size / 2;
    }

    public int centerY() {
        return y + size / 2;
    }
}
