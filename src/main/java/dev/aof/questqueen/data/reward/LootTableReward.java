package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.Optional;

/** {@code name} is what the book calls the roll ("Mekanism T1"); without one it shows the table's path. */
public record LootTableReward(ResourceLocation table, Optional<String> name) implements Reward {
    public static final MapCodec<LootTableReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("table").forGetter(LootTableReward::table),
            Codec.STRING.optionalFieldOf("name").forGetter(LootTableReward::name)
    ).apply(instance, LootTableReward::new));

    public LootTableReward(ResourceLocation table) {
        this(table, Optional.empty());
    }

    @Override
    public String type() {
        return "loot";
    }

    @Override
    public String describe() {
        return name.filter(n -> !n.isBlank()).orElse("loot " + table.getPath());
    }

    @Override
    public void grant(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LootTable loot = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .create(LootContextParamSets.ADVANCEMENT_REWARD);
        for (ItemStack stack : loot.getRandomItems(params)) {
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
