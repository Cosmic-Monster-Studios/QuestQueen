package dev.aof.questqueen.data.reward;

import com.mojang.serialization.MapCodec;
import dev.aof.questqueen.data.AddonTypes;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reward-type → codec registry: Quest Queen's own types, plus the namespaced ones addons register through
 * {@code QuestQueenApi.registerReward} before loading completes.
 *
 * <p>Deliberately not declared on {@link Reward}: holding it there made {@code Reward.<clinit>} resolve
 * every reward codec while each of those codecs resolves {@code Reward::type} — a class-initialization
 * cycle that left entries null and poisoned the whole reward/task/chapter graph (every codec failed
 * with {@code Could not initialize class dev.aof.questqueen.data.reward.Reward}). The same fix applied to
 * {@code TaskCodecs}.
 */
public final class RewardCodecs {
    static final Map<String, MapCodec<? extends Reward>> CODECS = Map.ofEntries(
            Map.entry("item", ItemReward.CODEC),
            Map.entry("xp", XpReward.CODEC),
            Map.entry("xp_levels", XpLevelReward.CODEC),
            Map.entry("command", CommandReward.CODEC),
            Map.entry("loot", LootTableReward.CODEC),
            Map.entry("toast", ToastReward.CODEC),
            Map.entry("advancement", AdvancementReward.CODEC),
            Map.entry("choice", ChoiceReward.CODEC),
            Map.entry("stage", StageReward.CODEC),
            Map.entry("stagelock", StageReward.CODEC),
            Map.entry("progression", StageReward.CODEC),
            Map.entry("progressivestages", StageReward.CODEC)
    );

    /** Addon types. Mods set up in parallel, so registration can come from several threads at once. */
    private static final Map<String, MapCodec<? extends Reward>> ADDON = new ConcurrentHashMap<>();

    private RewardCodecs() {
    }

    static MapCodec<? extends Reward> forType(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        MapCodec<? extends Reward> codec = CODECS.get(key);
        if (codec == null) {
            codec = ADDON.get(key);
        }
        if (codec == null) {
            throw new IllegalArgumentException("Unknown quest reward type: " + type);
        }
        return codec;
    }

    /** Internal: addons call {@code QuestQueenApi.registerReward}. */
    public static void register(String type, MapCodec<? extends Reward> codec) {
        if (codec == null) {
            throw new IllegalArgumentException("Quest Queen reward type '" + type + "' needs a codec");
        }
        String key = AddonTypes.checkedKey("reward", type);
        if (ADDON.putIfAbsent(key, codec) != null) {
            throw new IllegalStateException("Quest Queen reward type '" + type + "' is already registered");
        }
    }

    /** True for a type a chapter can use: one of Quest Queen's own or a registered addon type. */
    public static boolean known(String type) {
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return CODECS.containsKey(key) || ADDON.containsKey(key);
    }

    /** The registered addon types. */
    public static Set<String> addonTypes() {
        return Set.copyOf(ADDON.keySet());
    }

    /** Tests only: forget an addon type so a test can register it again. */
    static void unregisterForTests(String type) {
        ADDON.remove(type);
    }
}
