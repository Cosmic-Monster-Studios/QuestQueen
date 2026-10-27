package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public interface Task {
    Codec<Task> CODEC = Codec.STRING.dispatch("type", Task::type, Task::codecFor);

    String type();

    default int required() {
        return 1;
    }

    default Optional<ResourceLocation> itemId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> tagId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> entityId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> advancementId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> structureId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> blockId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> statId() {
        return Optional.empty();
    }

    default Optional<String> npcId() {
        return Optional.empty();
    }

    default Optional<String> triggerId() {
        return Optional.empty();
    }

    default Optional<String> locationKey() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> dimensionId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> biomeId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> fluidId() {
        return Optional.empty();
    }

    Task withCount(int count);

    /**
     * An item the book shows on this task's row, written as in {@code /give} ({@code minecraft:ender_eye}, or with
     * {@code [components]}). For tasks with no item of their own (advancement, stat, kill, trigger, checkmark,
     * interact, visit, ...); a click on it opens the item's recipe.
     */
    default Optional<String> displayItem() {
        return Optional.empty();
    }

    /** The task itself, without a display item wrapper, for code that needs its concrete type. */
    static Task unwrap(Task task) {
        return task instanceof DisplayedTask displayed ? displayed.task() : task;
    }

    /**
     * The verb the book shows for this task ({@code LEARN}, {@code CHANNEL}). Quest Queen's own types take theirs
     * from {@link TaskVerbs}; an addon type without one reads as its humanized name.
     */
    default Optional<String> verb() {
        return Optional.empty();
    }

    /**
     * The count text on this task's row while it is open, given the stored value. Empty shows the plain
     * {@code value/required}.
     */
    default Optional<String> progressText(int value) {
        return Optional.empty();
    }

    default String describe() {
        return type() + itemId().map(id -> " " + id.getPath()).orElse("")
                + entityId().map(id -> " " + id.getPath()).orElse("")
                + (required() > 1 ? " x" + required() : "");
    }

    private static MapCodec<? extends Task> codecFor(String type) {
        return TaskCodecs.forType(type);
    }
}
