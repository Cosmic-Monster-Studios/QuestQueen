package dev.aof.questqueen.api.event;

import dev.aof.questqueen.data.reward.Reward;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/**
 * A quest's rewards were claimed. Posted on {@code NeoForge.EVENT_BUS}, on the server thread, after every reward has
 * been granted to the claiming player. One event per quest: CLAIM ALL posts one for each quest it claims.
 *
 * <p>{@link #getRewards()} is what the claim handed out: the quest's rewards with a pick-one reward replaced by the
 * option taken. A reward whose grant threw is still listed, since the claim is spent either way. A reward meant for
 * the whole team can use {@link #getMembers()}, which includes offline members.
 *
 * <p>{@code /questqueen grant-all} completes quests without claiming them, so it posts no claim event; their rewards
 * stay claimable and post this event when claimed.
 */
public final class RewardsClaimedEvent extends Event {
    /** Which button claimed the rewards. */
    public enum Source {
        /** CLAIM on one quest. */
        TILE,
        /** CLAIM ALL for a chapter. */
        CHAPTER,
        /** Taking an option of a pick-one reward. */
        CHOICE
    }

    private final ServerPlayer player;
    private final String teamId;
    private final List<UUID> members;
    private final ResourceLocation chapterId;
    private final String tileId;
    private final List<Reward> rewards;
    private final Source source;

    public RewardsClaimedEvent(ServerPlayer player, String teamId, List<UUID> members, ResourceLocation chapterId,
                               String tileId, List<Reward> rewards, Source source) {
        this.player = player;
        this.teamId = teamId;
        this.members = List.copyOf(members);
        this.chapterId = chapterId;
        this.tileId = tileId;
        this.rewards = List.copyOf(rewards);
        this.source = source;
    }

    /** The player who claimed, and who received the rewards. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The team the quest belongs to ({@code ftb:<uuid>} with FTB Teams, else {@code solo:<uuid>}). */
    public String getTeamId() {
        return teamId;
    }

    /** Every member of the team, online or not; always includes {@link #getPlayer()}. */
    public List<UUID> getMembers() {
        return members;
    }

    public ResourceLocation getChapterId() {
        return chapterId;
    }

    public String getTileId() {
        return tileId;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public Source getSource() {
        return source;
    }
}
