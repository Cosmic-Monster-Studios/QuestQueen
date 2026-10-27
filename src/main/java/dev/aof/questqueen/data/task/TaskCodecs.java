package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import dev.aof.questqueen.data.AddonTypes;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The task-type → codec registry and the authoring aliases: Quest Queen's own types, plus the namespaced ones
 * addons register through {@code QuestQueenApi.registerTask} before loading completes.
 *
 * <p>Deliberately not declared on {@link Task}. Holding these constants in the interface made
 * {@code Task.<clinit>} resolve all nineteen task codecs, while each of those codecs resolves
 * {@code Task::type} — a class-initialization cycle that left {@code CODECS} entries null and poisoned the
 * whole task/reward class graph (every codec failed with {@code Could not initialize class
 * dev.aof.questqueen.data.task.Task}).
 */
public final class TaskCodecs {
    static final Map<String, String> ALIASES = Map.of(
            "item", "obtain",
            "kill_entity", "kill",
            "dimension", "visit_dimension",
            "biome", "visit_biome",
            "structure", "visit_structure"
    );

    static final Map<String, MapCodec<? extends Task>> CODECS = Map.ofEntries(
            Map.entry("obtain", ObtainTask.CODEC),
            Map.entry("item_tag", ItemTagTask.CODEC),
            Map.entry("submit", SubmitTask.CODEC),
            Map.entry("kill", KillTask.CODEC),
            Map.entry("raid", RaidTask.CODEC),
            Map.entry("advancement", AdvancementTask.CODEC),
            Map.entry("location", LocationTask.CODEC),
            Map.entry("visit_structure", VisitStructureTask.CODEC),
            Map.entry("visit_dimension", DimensionTask.CODEC),
            Map.entry("visit_biome", BiomeTask.CODEC),
            Map.entry("observation", ObservationTask.CODEC),
            Map.entry("checkmark", CheckmarkTask.CODEC),
            Map.entry("stat", StatTask.CODEC),
            Map.entry("interact_block", InteractBlockTask.CODEC),
            Map.entry("interact_entity", InteractEntityTask.CODEC),
            Map.entry("fluid", FluidTask.CODEC),
            Map.entry("xp_levels", XpLevelTask.CODEC),
            Map.entry("npc_dialog", NpcDialogTask.CODEC),
            Map.entry("trigger", TriggerTask.CODEC)
    );

    /** Addon types. Mods set up in parallel, so registration can come from several threads at once. */
    private static final Map<String, MapCodec<? extends Task>> ADDON = new ConcurrentHashMap<>();

    private TaskCodecs() {
    }

    private static final Map<String, MapCodec<Task>> WITH_ICON = new java.util.concurrent.ConcurrentHashMap<>();

    static MapCodec<? extends Task> forType(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        String canonical = ALIASES.getOrDefault(key, key);
        MapCodec<? extends Task> builtIn = CODECS.get(canonical);
        MapCodec<? extends Task> codec = builtIn != null ? builtIn : ADDON.get(canonical);
        if (codec == null) {
            throw new IllegalArgumentException("Unknown quest task type: " + type);
        }
        return WITH_ICON.computeIfAbsent(canonical, ignored -> withIcon(codec));
    }

    /** Internal: addons call {@code QuestQueenApi.registerTask}. */
    public static void register(String type, MapCodec<? extends Task> codec) {
        if (codec == null) {
            throw new IllegalArgumentException("Quest Queen task type '" + type + "' needs a codec");
        }
        String key = AddonTypes.checkedKey("task", type);
        if (ADDON.putIfAbsent(key, codec) != null) {
            throw new IllegalStateException("Quest Queen task type '" + type + "' is already registered");
        }
    }

    /** True for a type a chapter can use: one of Quest Queen's own (or an alias) or a registered addon type. */
    public static boolean known(String type) {
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String canonical = ALIASES.getOrDefault(key, key);
        return CODECS.containsKey(canonical) || ADDON.containsKey(canonical);
    }

    /** The registered addon types. */
    public static Set<String> addonTypes() {
        return Set.copyOf(ADDON.keySet());
    }

    /** Tests only: forget an addon type so a test can register it again. */
    static void unregisterForTests(String type) {
        ADDON.remove(type);
        WITH_ICON.remove(type);
    }

    /**
     * Every task type also reads an optional {@code "icon"}: the item shown on its row. A task that sets one comes
     * back as a {@link DisplayedTask} around the plain task; one that does not is the plain task, as before.
     */
    @SuppressWarnings("unchecked")
    private static MapCodec<Task> withIcon(MapCodec<? extends Task> codec) {
        MapCodec<Task> plain = (MapCodec<Task>) codec;
        return com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                plain.forGetter(Task::unwrap),
                com.mojang.serialization.Codec.STRING.optionalFieldOf("icon").forGetter(Task::displayItem)
        ).apply(instance, (task, icon) -> icon.filter(s -> !s.isBlank())
                .<Task>map(s -> new DisplayedTask(task, s)).orElse(task)));
    }
}
