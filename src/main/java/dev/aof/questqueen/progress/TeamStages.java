package dev.aof.questqueen.progress;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.compat.Stages;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.StageReward;
import net.minecraft.server.level.ServerPlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stage rewards reach the whole team. Quest progress and claims are already per team, but a stage is stored per
 * player by the stage mod, so before 1.1.233 only the player who pressed CLAIM got it and their teammates stayed
 * locked out of whatever the stage gates.
 *
 * <p>A shared stage reward ({@link StageReward#shared()}, the default) goes to every team member when claimed:
 * online members directly, offline ones through StageLock's offline grant. {@link #catchUp} covers the rest (a
 * player who joins later, an offline member on a backend that needs them online, a grant a listener refused) by
 * granting a player every shared stage on the tiles their current team has claimed, at login and on a team change.
 * Nothing is ever revoked: a player who leaves a team keeps the stages they got from it.
 */
public final class TeamStages {
    private TeamStages() {
    }

    /** The claim-time grant: the claimer exactly as before, then every other member of the claimer's team. */
    public static void grantShared(ServerPlayer claimer, String stage) {
        Stages.grantStage(claimer, stage);
        for (UUID member : TeamService.members(TeamService.current(claimer))) {
            if (member.equals(claimer.getUUID())) {
                continue;
            }
            ServerPlayer online = claimer.server.getPlayerList().getPlayer(member);
            if (online != null && Stages.hasStage(online, stage)) {
                continue;
            }
            Stages.grantStage(claimer.server, member, stage);
        }
    }

    /**
     * Grant {@code player} every shared stage reward on the tiles their current team has claimed that they lack.
     * Reads the loaded quest definitions, so a stage a pack added to an already-claimed tile is handed out too.
     * Returns how many grants were attempted.
     */
    public static int catchUp(ServerPlayer player) {
        if (!Stages.present()) {
            return 0;
        }
        Set<String> owed = owedStages(TeamService.current(player));
        int attempted = 0;
        for (String stage : owed) {
            if (!Stages.hasStage(player, stage)) {
                attempted++;
                if (!Stages.grantStage(player, stage)) {
                    QuestQueen.LOGGER.debug("Team stage {} not granted to {} yet", stage,
                            player.getGameProfile().getName());
                }
            }
        }
        return attempted;
    }

    /** Shared stage rewards on the tiles the team has claimed. */
    static Set<String> owedStages(String teamId) {
        Set<String> claimed = new HashSet<>();
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT quest_id FROM progress WHERE team_id = ? AND task_id = 'claimed' AND completed != 0")) {
            statement.setString(1, teamId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claimed.add(result.getString("quest_id"));
                }
            }
        } catch (SQLException exception) {
            QuestQueen.LOGGER.warn("Could not read claims for team stages of {}", teamId, exception);
            return Set.of();
        }
        Set<String> owed = new LinkedHashSet<>();
        if (claimed.isEmpty()) {
            return owed;
        }
        for (Chapter chapter : QuestDefinitions.chapters()) {
            for (Tile tile : chapter.tiles()) {
                if (!claimed.contains(ProgressSnapshot.questKey(chapter.id(), tile.id()))) {
                    continue;
                }
                for (Reward reward : tile.rewards()) {
                    if (reward instanceof StageReward stage && stage.shared() && !stage.stage().isBlank()) {
                        owed.add(stage.stage());
                    }
                }
            }
        }
        return owed;
    }
}
