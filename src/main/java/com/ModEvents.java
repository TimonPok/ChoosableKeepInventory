package com;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "chosenkeepinv")
public class ModEvents {
    private static final Map<String, Long> pvpCooldowns = new HashMap<>();
    private static final Map<String, String> combatRelations = new HashMap<>();
    private static final long COOLDOWN_MS = 20000; // 30 seconds

    @SubscribeEvent
    public static void onPlayerAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            String victimName = victim.getScoreboardName();
            String attackerName = attacker.getScoreboardName();

            // 1. Duels bypass combat tracking rules natively
            if (DuelManager.isInDuelWithEachOther(victimName, attackerName)) {
                return;
            }

            ModPersistentData victimData = ModPersistentData.get(victim.serverLevel());
            ModPersistentData attackerData = ModPersistentData.get(attacker.serverLevel());

            // 2. NEW RULE: If EITHER the attacker OR the victim has PvP safety turned ON,
            // we bypass the tag system completely for both. No one gets combat tagged!
            if (attackerData.getPvpBypass(attackerName) || victimData.getPvpBypass(victimName)) {
                return; // Allow the hit to land naturally, but skip the combat tag logic entirely
            }

            // 3. Normal tagging sequence if BOTH players have pvp safety turned OFF
            long currentTime = System.currentTimeMillis();

            if (!isCurrentlyInCombat(victimName)) {
                MutableComponent text = Component.literal("§cPVP active, keep inventory disabled. Press ")
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
    }

    @SubscribeEvent
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
            victim.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, victim.getServer());
            return;
        }

        if (!data.getChoice(victimName)) return;

        if (data.getPvpBypass(victimName) || !wasInCombatBeforeDeath) {
            victim.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, victim.getServer());
        } else {
            victim.sendSystemMessage(Component.literal("§cYou dropped your items because you were in PvP combat!"));
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        player.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, player.getServer());
    }

    public static boolean isCurrentlyInCombat(String username) {
        long lastCombat = pvpCooldowns.getOrDefault(username, 0L);
        return (System.currentTimeMillis() - lastCombat) < COOLDOWN_MS;
    }
    public static void clearCombatTag(ServerPlayer player) {
        String username = player.getScoreboardName();
        pvpCooldowns.remove(username);
        combatRelations.remove(username);

        // Sends an empty action bar update to instantly wipe the "⚔ Combat Tagged! ⚔" overlay text
        player.displayClientMessage(Component.literal(""), true);
    }
}
