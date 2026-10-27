package dev.aof.questqueen.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** The mode's own slot in the book's top bar, beside the mode toggle, {@code width} wide as the mode asked. */
public record TopBarContext(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX,
                            int mouseY, ThemeSnapshot theme) {
}
