package dev.aof.questqueen.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/**
 * A quest (tile) was completed for a team. Posted on {@code NeoForge.EVENT_BUS}, on the server thread, right after
 * the completion is stored. Rewards are not granted yet: they wait for a claim, which posts
 * {@link RewardsClaimedEvent}.
 *
 * <p>Progress belongs to the team, so the event carries every member, online or not, not only the player whose
 * action finished the quest.
 */
public final class QuestCompletedEvent extends Event {
    /** How the quest came to be completed. */
    public enum Source {
        /** Its last task was done in play. */
        PROGRESS,
        /** {@code /questqueen grant-all} forced it complete, ignoring locks. */
        GRANT_ALL
    }

    private final ServerPlayer player;
    private final String teamId;
    private final List<UUID> members;
    private final ResourceLocation chapterId;
    private final String tileId;
    private final Source source;

    public QuestCompletedEvent(ServerPlayer player, String teamId, List<UUID> members, ResourceLocation chapterId,
                               String tileId, Source source) {
        this.player = player;
        this.teamId = teamId;
        this.members = List.copyOf(members);
        this.chapterId = chapterId;
        this.tileId = tileId;
        this.source = source;
    }

    /** The player whose action completed the quest. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The team the progress belongs to ({@code ftb:<uuid>} with FTB Teams, else {@code solo:<uuid>}). */
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

    public Source getSource() {
        return source;
    }
}
