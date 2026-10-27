package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Board reward face — every grant type maps to a real item icon. No bare "+" . */
public final class RewardIcons {
    /** What an addon reward shows when it names no icon of its own. */
    static final ResourceLocation ADDON_FALLBACK = ResourceLocation.parse("minecraft:nether_star");

    private RewardIcons() {
    }

    public static Optional<ResourceLocation> iconId(Reward reward) {
        if (reward == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> item = reward.itemId();
        if (item.isPresent()) {
            return item;
        }
        if (reward instanceof CommandReward command) {
            // The book draws command rewards through the client's RewardFaces; this is the plain-id fallback.
            return Optional.of(command.give().map(GiveCommand::itemId)
                    .orElse(ResourceLocation.parse("minecraft:nether_star")));
        }
        if (reward instanceof ChoiceReward choice && !choice.options().isEmpty()) {
            return Optional.of(choice.options().getFirst().item());
        }
        if (dev.aof.questqueen.data.AddonTypes.namespaced(reward.type())) {
            return Optional.of(reward.iconItem().orElse(ADDON_FALLBACK));
        }
        return Optional.ofNullable(switch (reward.type()) {
            case "xp", "xp_levels" -> ResourceLocation.parse("minecraft:experience_bottle");
            case "advancement" -> ResourceLocation.parse("minecraft:knowledge_book");
            case "stage", "stagelock", "progression", "progressivestages" -> ResourceLocation.parse("minecraft:amethyst_shard");
            case "toast" -> ResourceLocation.parse("minecraft:paper");
            case "loot", "loot_table" -> ResourceLocation.parse("minecraft:chest");
            case "choice" -> ResourceLocation.parse("minecraft:chest");
            default -> null;
        });
    }
}
