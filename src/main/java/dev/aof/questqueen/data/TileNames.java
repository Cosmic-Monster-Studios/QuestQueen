package dev.aof.questqueen.data;

import dev.aof.questqueen.data.task.Task;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * What the book calls a quest. An untitled quest is named after its first task's item, the way FTB Quests names
 * one, so a converted pack reads "Steel Ingot" rather than a raw id. With no item either, the id.
 */
public final class TileNames {
    private TileNames() {
    }

    public static Component title(Tile tile) {
        if (!tile.title().isBlank()) {
            return Component.literal(tile.title());
        }
        for (Task task : tile.tasks()) {
            Optional<Item> item = itemOf(task);
            if (item.isPresent()) {
                return item.get().getDescription();
            }
        }
        return Component.literal(tile.id());
    }

    /** {@link #title} as text in the current language. */
    public static String text(Tile tile) {
        return title(tile).getString();
    }

    private static Optional<Item> itemOf(Task task) {
        Optional<ResourceLocation> id = task.displayItem().map(ResourceLocation::tryParse);
        if (id.isEmpty()) {
            id = task.itemId();
        }
        if (id.isEmpty()) {
            id = task.blockId();
        }
        return id.flatMap(BuiltInRegistries.ITEM::getOptional).filter(item -> item != Items.AIR);
    }
}
