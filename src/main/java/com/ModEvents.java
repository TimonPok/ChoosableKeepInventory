package com;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = "chosenkeepinv")
public class ModEvents {
    private static final Map<String, Long> pvpCooldowns = new HashMap<>();
    private static final Map<String, String> combatRelations = new HashMap<>();
    private static final long COOLDOWN_MS = 20000; // 20 секунд

    private static final Map<String, List<ItemStack>> savedInventoriesCache = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            String victimName = victim.getScoreboardName();
            String attackerName = attacker.getScoreboardName();

            if (DuelManager.isInDuelWithEachOther(victimName, attackerName)) {
                return;
            }

            ModPersistentData victimData = ModPersistentData.get(victim.serverLevel());
            ModPersistentData attackerData = ModPersistentData.get(attacker.serverLevel());

            if (attackerData.getPvpBypass(attackerName) || victimData.getPvpBypass(victimName)) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (!isCurrentlyInCombat(victimName)) {
                Component text = Component.literal("§cPVP active, keep inventory disabled. Press ")
                        .append(Component.literal("§e§l[HERE]")
                                .withStyle(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/duel " + attackerName))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§ePress on this message to suggest duel to another player")))))
                        .append(Component.literal("§c, to suggest duel!"));
                victim.sendSystemMessage(text);
            }

            pvpCooldowns.put(victimName, currentTime);
            combatRelations.put(victimName, attackerName);

            if (!isCurrentlyInCombat(attackerName)) {
                attacker.sendSystemMessage(Component.literal("§cPVP active, keep inventory disabled"));
            }
            pvpCooldowns.put(attackerName, currentTime);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getScoreboardName();

        if (isCurrentlyInCombat(username)) {
            long lastCombat = pvpCooldowns.getOrDefault(username, 0L);
            long timePassed = System.currentTimeMillis() - lastCombat;
            long secondsLeft = Math.max(0, (COOLDOWN_MS - timePassed) / 1000);

            player.displayClientMessage(
                    Component.literal("§c⚔ Combat Tagged! " + secondsLeft + "s left ⚔"),
                    true
            );
        }

        if (player.tickCount % 10 == 0) {
            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                ModPersistentData data = ModPersistentData.get(player.serverLevel());

                if (data.hasDeadCurios(username)) {
                    CompoundTag savedCurios = data.loadAndRemoveDeadCurios(username);

                    CuriousCompat.loadCuriosInventory(player, savedCurios);
                    //System.out.println("[ChosenKeepInv] Curios post-respawn tick restore triggered for " + username);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        String victimName = victim.getScoreboardName();

        ModPersistentData data = ModPersistentData.get(victim.serverLevel());
        boolean wasInCombatBeforeDeath = isCurrentlyInCombat(victimName);


        pvpCooldowns.remove(victimName);
        String attackerName = combatRelations.remove(victimName);
        if (attackerName != null) {
            pvpCooldowns.remove(attackerName);
            ServerPlayer attacker = victim.getServer().getPlayerList().getPlayerByName(attackerName);
            if (attacker != null) {
                attacker.sendSystemMessage(Component.literal("§aYour target died. You are out of combat!"));
                attacker.displayClientMessage(Component.literal(""), true);
            }
        }

        boolean shouldKeepInventory = false;

        if (DuelManager.isInAnyDuel(victimName)) {
            String opponentName = DuelManager.getOpponent(victimName);
            DuelManager.endDuel(victimName);
            victim.sendSystemMessage(Component.literal("§eThe duel has ended. You lost!"));
            if (opponentName != null) {
                ServerPlayer opponent = victim.getServer().getPlayerList().getPlayerByName(opponentName);
                if (opponent != null) {
                    opponent.sendSystemMessage(Component.literal("§aThe duel has ended. You won!"));
                }
            }
            shouldKeepInventory = true;
        }
        else if (data.getChoice(victimName)) {
            if (data.getPvpBypass(victimName) || !wasInCombatBeforeDeath) {
                shouldKeepInventory = true;
            } else {
                victim.sendSystemMessage(Component.literal("§cYou died in PVP, your items dropped."));
            }
        }

        if (shouldKeepInventory) {
            // Сохранение ванильного инвентаря
            List<ItemStack> copiedInventory = new ArrayList<>();
            for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
                copiedInventory.add(victim.getInventory().getItem(i).copy());
            }
            savedInventoriesCache.put(victimName, copiedInventory);

            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                CompoundTag curiosNbt = CuriousCompat.saveCuriosInventory(victim);

                if (curiosNbt.contains("CuriosList") && !curiosNbt.getList("CuriosList", 10).isEmpty()) {
                    data.saveDeadCurios(victimName, curiosNbt);
                    //System.out.println("[ChosenKeepInv] Curios SUCCESSFULLY backed up via Official API for " + victimName);
                } else {
                    System.out.println("[ChosenKeepInv] WARNING: Curios returned EMPTY inventory for " + victimName + " at death!");
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getScoreboardName();
        if (savedInventoriesCache.containsKey(username)) {
            ModPersistentData data = ModPersistentData.get(player.serverLevel());
            CompoundTag fallbackCuriosTag = new CompoundTag();
            event.getDrops().removeIf(itemEntity -> {
                ItemStack stack = itemEntity.getItem();
                return false;
            });


            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        String username = newPlayer.getScoreboardName();
        ModPersistentData data = ModPersistentData.get(newPlayer.serverLevel());

        if (savedInventoriesCache.containsKey(username)) {
            List<ItemStack> savedItems = savedInventoriesCache.remove(username);
            for (int i = 0; i < savedItems.size(); i++) {
                if (i < newPlayer.getInventory().getContainerSize()) {
                    newPlayer.getInventory().setItem(i, savedItems.get(i));
                }
            }
        }
    }

    public static boolean isCurrentlyInCombat(String username) {
        long lastCombat = pvpCooldowns.getOrDefault(username, 0L);
        return (System.currentTimeMillis() - lastCombat) < COOLDOWN_MS;
    }

    public static void clearCombatTag(ServerPlayer player) {
        String username = player.getScoreboardName();
        pvpCooldowns.remove(username);
        combatRelations.remove(username);
        player.displayClientMessage(Component.literal(""), true);
    }
}
