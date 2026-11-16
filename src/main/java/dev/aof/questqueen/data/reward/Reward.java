package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public interface Reward {
    Codec<Reward> CODEC = Codec.STRING.dispatch("type", Reward::type, Reward::codecFor);

    String type();

    void grant(ServerPlayer player);

    /** Chat, action-bar, title, or command side-effects. Bulk grant skips these. */
    default boolean noisy() {
        return false;
    }

    default Optional<ResourceLocation> itemId() {
        return Optional.empty();
    }

    default int count() {
        return 1;
    }

    /**
     * The item the book shows for a reward type Quest Queen does not draw itself. Addon rewards override this;
     * without it an addon reward shows a nether star.
     */
    default Optional<ResourceLocation> iconItem() {
        return Optional.empty();
    }

    /** One line naming the reward. An addon type without its own reads as its humanized name. */
    default String describe() {
        if (dev.aof.questqueen.data.AddonTypes.namespaced(type())) {
            return dev.aof.questqueen.data.task.TaskVerbs.humanize(type());
        }
        return type().toUpperCase(Locale.ROOT);
    }

    private static MapCodec<? extends Reward> codecFor(String type) {
        return RewardCodecs.forType(type);
    }
}
