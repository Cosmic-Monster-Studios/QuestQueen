package dev.aof.questqueen.api;

import com.mojang.serialization.MapCodec;
import dev.aof.questqueen.data.AddonTypes;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.RewardCodecs;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.data.task.TaskCodecs;
import dev.aof.questqueen.progress.TeamService;
import dev.aof.questqueen.task.TaskHooks;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Quest Queen's public API for addons: new reward and task types, task progress, and team info.
 *
 * <p>Reward and task types are namespaced with the addon's mod id ({@code mymod:mana}); Quest Queen's own stay
 * unprefixed. Register them in the mod constructor or in {@code FMLCommonSetupEvent}, on both the client and the
 * server (the book decodes the same chapters). Registration closes when loading completes; a later call throws
 * {@link IllegalStateException}.
 *
 * <p>Completion and claims are announced on {@code NeoForge.EVENT_BUS} as
 * {@link dev.aof.questqueen.api.event.QuestCompletedEvent} and {@link dev.aof.questqueen.api.event.RewardsClaimedEvent}.
 * The client book's API is {@code dev.aof.questqueen.api.client.QuestQueenClientApi}.
 */
public final class QuestQueenApi {
    private QuestQueenApi() {
    }

    /**
     * Add a reward type. {@code codec} reads the reward's fields (everything but {@code "type"}), and the reward's
     * {@link Reward#type()} must return {@code type}. {@link Reward#grant} runs on the server thread when the reward
     * is claimed. For the book, override {@link Reward#iconItem()} and {@link Reward#describe()}.
     *
     * @throws IllegalArgumentException when {@code type} is not namespaced or uses Quest Queen's or Minecraft's namespace
     * @throws IllegalStateException    when registration has closed or the type is taken
     */
    public static void registerReward(String type, MapCodec<? extends Reward> codec) {
        RewardCodecs.register(type, codec);
    }

    /**
     * Add a task type. {@code codec} reads the task's fields; every task also reads Quest Queen's optional
     * {@code "icon"}. The task's {@link Task#type()} must return {@code type}. Progress comes from
     * {@link #progressTasks} or {@link #completeTasks}. For the book, override {@link Task#verb()},
     * {@link Task#progressText(int)}, {@link Task#itemId()} or {@link Task#displayItem()}.
     *
     * @throws IllegalArgumentException when {@code type} is not namespaced or uses Quest Queen's or Minecraft's namespace
     * @throws IllegalStateException    when registration has closed or the type is taken
     */
    public static void registerTask(String type, MapCodec<? extends Task> codec) {
        TaskCodecs.register(type, codec);
    }

    /** True while reward and task types can still be registered. */
    public static boolean registrationOpen() {
        return !AddonTypes.frozen();
    }

    /**
     * Advance {@code player}'s team on every open, unlocked task of {@code type} that {@code match} accepts. Progress
     * is capped at each task's {@link Task#required()}, and a task that reaches it completes, and its quest with it
     * when that was the last task. Call on the server thread.
     *
     * @return how many tasks moved
     */
    public static int progressTasks(ServerPlayer player, String type, Predicate<Task> match, long amount) {
        requireServerThread(player);
        int step = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
        return TaskHooks.progressMatching(player, type, match, step);
    }

    /**
     * Complete every open, unlocked task of {@code type} that {@code match} accepts for {@code player}'s team. Call
     * on the server thread.
     *
     * @return how many tasks were completed
     */
    public static int completeTasks(ServerPlayer player, String type, Predicate<Task> match) {
        requireServerThread(player);
        return TaskHooks.completeMatching(player, type, match);
    }

    /** Complete {@code trigger} tasks with this id, and set the team's {@code trigger:<id>} flag. Any thread. */
    public static void fireTrigger(ServerPlayer player, String triggerId) {
        QuestNpcApi.fireTrigger(player, triggerId);
    }

    /**
     * Everyone who shares {@code player}'s quest progress, online or not, with {@code player} first. Just the player
     * without a team (or without FTB Teams).
     */
    public static List<UUID> teamMembers(ServerPlayer player) {
        return TeamService.membersOf(player);
    }

    /** The id of the team {@code player}'s progress belongs to: {@code ftb:<uuid>} or {@code solo:<uuid>}. */
    public static String teamId(ServerPlayer player) {
        return TeamService.current(player);
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Quest Queen task progress must be reported on the server thread");
        }
    }
}
