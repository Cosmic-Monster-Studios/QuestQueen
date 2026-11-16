package dev.aof.questqueen.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A picture for a sidebar entry or a tile: an item, a sprite (a whole texture drawn square), or one of Quest Queen's
 * glyphs. {@link #NONE} draws nothing.
 */
public record BookIcon(ItemStack item, ResourceLocation sprite, String glyph) {
    public static final BookIcon NONE = new BookIcon(ItemStack.EMPTY, null, "");

    public BookIcon {
        item = item == null ? ItemStack.EMPTY : item;
        glyph = glyph == null ? "" : glyph;
    }

    public static BookIcon of(ItemStack item) {
        return new BookIcon(item, null, "");
    }

    /** A texture such as {@code mymod:textures/gui/skill/fireball.png}, drawn whole. */
    public static BookIcon sprite(ResourceLocation texture) {
        return new BookIcon(ItemStack.EMPTY, texture, "");
    }

    /** A Quest Queen glyph id, the same names a chapter's {@code "icon": "glyph:<id>"} takes. */
    public static BookIcon glyph(String id) {
        return new BookIcon(ItemStack.EMPTY, null, id);
    }

    public boolean isEmpty() {
        return item.isEmpty() && sprite == null && glyph.isEmpty();
    }
}
