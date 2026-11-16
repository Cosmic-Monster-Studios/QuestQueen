package dev.aof.questqueen.api.client;

import net.minecraft.network.chat.Component;

/**
 * One tile on a book-mode grid, at a column and row like a quest. {@code tag} is a short line under the title
 * (a rank such as {@code 2/5}) in {@code tagColor} (ARGB; 0 uses the theme's muted text).
 */
public record ModeTile(String id, int col, int row, TileState state, BookIcon icon, Component label, String tag,
                       int tagColor) {
    public ModeTile {
        state = state == null ? TileState.LOCKED : state;
        icon = icon == null ? BookIcon.NONE : icon;
        label = label == null ? Component.empty() : label;
        tag = tag == null ? "" : tag;
    }

    public ModeTile(String id, int col, int row, TileState state, BookIcon icon, Component label) {
        this(id, col, row, state, icon, label, "", 0);
    }
}
