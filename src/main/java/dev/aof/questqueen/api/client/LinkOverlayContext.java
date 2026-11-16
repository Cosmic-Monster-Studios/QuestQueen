package dev.aof.questqueen.api.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing along one link, after every tile is drawn. {@code fromX}/{@code fromY} and {@code toX}/{@code toY} are the
 * top-left corners of the two tiles on screen, each {@code size} square. {@code portX}/{@code portY} is where Quest
 * Queen's path arrow sits on {@code from}'s edge, pointing ({@code dx}, {@code dy}).
 */
public record LinkOverlayContext(GuiGraphics graphics, String entryId, ModeLink link, ModeTile from, ModeTile to,
                                 int fromX, int fromY, int toX, int toY, int size, int portX, int portY, int dx,
                                 int dy, float partialTick, ThemeSnapshot theme) {
    public int fromCenterX() {
        return fromX + size / 2;
    }

    public int fromCenterY() {
        return fromY + size / 2;
    }

    public int toCenterX() {
        return toX + size / 2;
    }

    public int toCenterY() {
        return toY + size / 2;
    }
}
