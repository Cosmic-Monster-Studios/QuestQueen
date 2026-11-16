package dev.aof.questqueen.api.client;

import net.minecraft.network.chat.Component;

/**
 * A row in a book mode's sidebar, where quest mode lists chapters. {@code badge} is short text on the right of the
 * row (a count, say). A locked entry shows a padlock and does not open; a hidden one is not listed.
 */
public record SidebarEntry(String id, Component label, BookIcon icon, String badge, boolean locked, boolean hidden) {
    public SidebarEntry {
        label = label == null ? Component.empty() : label;
        icon = icon == null ? BookIcon.NONE : icon;
        badge = badge == null ? "" : badge;
    }

    public SidebarEntry(String id, Component label, BookIcon icon) {
        this(id, label, icon, "", false, false);
    }
}
