package com;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.loading.FMLLoader;

import java.util.List;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

public class voidHandler {
    public static final ResourceKey<Level> LIMBO_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("chosenkeepinv", "limbo")
    );

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerVoidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension().equals(LIMBO_KEY)) return;

        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            String username = player.getScoreboardName();
            ModPersistentData data = ModPersistentData.get(player.serverLevel());

            if (data.getChoice(username)) {
                float chance = !FMLLoader.isProduction() ? 1.0f : 0.33f;

                if (player.getRandom().nextFloat() < chance) {
                    ServerLevel limbo = player.server.getLevel(LIMBO_KEY);
                    if (limbo != null) {
                        event.setCanceled(true);
                        sendToLimbo(player, limbo, data, username);
                    }
                }
            }
        }
    }

    private static void sendToLimbo(ServerPlayer player, ServerLevel limbo, ModPersistentData data, String username) {
        System.out.println("[Limbo Mod] Player " + username + " sent to Limbo!");
        limbo.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, limbo.getServer());

        player.setHealth(player.getMaxHealth());
        player.getCombatTracker().recheckStatus();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));

        CompoundTag wrapperTag = new CompoundTag();
        ListTag inventoryList = new ListTag();
        player.getInventory().save(inventoryList);
        wrapperTag.put("Items", inventoryList);
        data.saveLimboInventory(username, wrapperTag);

        player.getInventory().clearContent();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        String page1Text = "...and the hero fell into the emptiness of under the world..\n\n"
                + "He expected pain, death, just return to the begining...\n\n"
                + "No, this time our hero found himself in the endless desert...";
        String page2Text = "where moon shines brightly and the unseen forces keep him from touching it, \n\n"
                + "he called it.. \n\n"
                + "Limbo..";

        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal(page1Text)),
                Filterable.passThrough(Component.literal(page2Text))
        );
        Filterable<String> title = Filterable.passThrough("Dusted old Book");
        WrittenBookContent content = new WrittenBookContent(title, "Forgotten Author", 0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        player.getInventory().add(book);

        int playerX = player.getRandom().nextInt(1000) - 500;
        int playerZ = player.getRandom().nextInt(1000) - 500;
        int surfaceY = 4;

        player.teleportTo(limbo, playerX + 0.5, surfaceY + 1, playerZ + 0.5, player.getYRot(), player.getXRot());
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(2.0f);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(LIMBO_KEY)) return;

        ChunkAccess chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();

        int targetX = Math.round((float) centerX / 150) * 150;
        int targetZ = Math.round((float) centerZ / 150) * 150;

        if (chunkPos.getMinBlockX() <= targetX && chunkPos.getMaxBlockX() >= targetX &&
                chunkPos.getMinBlockZ() <= targetZ && chunkPos.getMaxBlockZ() >= targetZ) {

            BlockPos doorPos = new BlockPos(targetX, 4, targetZ);
            ModPersistentData data = ModPersistentData.get(level);

            if (!data.isDoorUsed(doorPos)) {

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        chunk.setBlockState(doorPos.offset(x, -1, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), false);
                    }
                }
                chunk.setBlockState(doorPos, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), false);
                chunk.setBlockState(doorPos.above(), Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), false);
            }
        }
    }

    @SubscribeEvent
    public static void onDoorInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (player.level().dimension().equals(LIMBO_KEY)) {
            BlockPos clickedPos = event.getPos();
            var blockState = event.getLevel().getBlockState(clickedPos);

            if (blockState.getBlock() instanceof DoorBlock) {
                event.setCanceled(true);

                BlockPos doorBasePos = blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? clickedPos.below() : clickedPos;

                ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    ModPersistentData data = ModPersistentData.get(overworld);

                    ServerLevel limbo = player.serverLevel();
                    limbo.setBlockAndUpdate(doorBasePos.above(), Blocks.AIR.defaultBlockState());
                    limbo.setBlockAndUpdate(doorBasePos, Blocks.AIR.defaultBlockState());

                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            limbo.setBlockAndUpdate(doorBasePos.offset(x, -1, z), Blocks.SAND.defaultBlockState());
                        }
                    }

                    data.markDoorAsUsed(doorBasePos);

                    player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                    player.setGameMode(GameType.SURVIVAL);

                    String username = player.getScoreboardName();
                    player.getInventory().clearContent();

                    if (data.hasLimboInventory(username)) {
                        CompoundTag savedInv = data.loadAndRemoveLimboInventory(username);
                        if (savedInv != null) {

                            if (savedInv.contains("Items")) {
                                ListTag inventoryList = savedInv.getList("Items", 10);
                                player.getInventory().load(inventoryList);
                            }

                            if (savedInv.contains("CuriosRawData")) {
                                CompoundTag playerFullNbt = new CompoundTag();
                                player.saveWithoutId(playerFullNbt);

                                if (!playerFullNbt.contains("ForgeCaps")) {
                                    playerFullNbt.put("ForgeCaps", new CompoundTag());
                                }

                                playerFullNbt.getCompound("ForgeCaps").put("curios:inventory", savedInv.getCompound("CuriosRawData"));
                                player.load(playerFullNbt);
                            }
                        }
                    }
                }
            }
        }
    }
}

