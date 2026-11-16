package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * A stage granted on claim. {@code shared} (default true) hands it to every member of the claimer's team, online or
 * not, and to anyone who joins later; {@code "shared": false} keeps it for the claimer alone, as before 1.1.233.
 */
public record StageReward(String stage, boolean shared) implements Reward {
    public static final MapCodec<StageReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("stage", "").forGetter(StageReward::stage),
            Codec.STRING.optionalFieldOf("id", "").forGetter(reward -> ""),
            Codec.BOOL.optionalFieldOf("shared", true).forGetter(StageReward::shared)
    ).apply(instance, (stage, id, shared) -> new StageReward(!stage.isBlank() ? stage : id, shared)));

    public StageReward(String stage) {
        this(stage, true);
    }

    @Override
    public String type() {
        return "stage";
    }

    @Override
    public String describe() {
        return "STAGE: " + stage;
    }

    @Override
    public void grant(ServerPlayer player) {
        if (shared) {
            dev.aof.questqueen.progress.TeamStages.grantShared(player, stage);
        } else {
            dev.aof.questqueen.compat.Stages.grantStage(player, stage);
        }
    }
}
