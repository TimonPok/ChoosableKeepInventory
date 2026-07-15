package com;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public class DuelManager {
    private static final Map<String, String> pendingInvites = new HashMap<>();
    private static final Map<String, String> activeDuels = new HashMap<>();

    public static void sendInvite(String sender, String receiver) {
        pendingInvites.put(receiver, sender);
    }

    public static boolean hasInviteFrom(String receiver, String sender) {
        return sender.equalsIgnoreCase(pendingInvites.get(receiver));
    }

    public static String rejectInvite(String receiver) {
        return pendingInvites.remove(receiver);
    }

    // UPDATED METHOD: Clears active PvP tags when the duel is officially started
    public static void startDuel(ServerPlayer playerA, ServerPlayer playerB) {
        String nameA = playerA.getScoreboardName();
        String nameB = playerB.getScoreboardName();

        pendingInvites.remove(nameA);
        pendingInvites.remove(nameB);

        activeDuels.put(nameA, nameB);
        activeDuels.put(nameB, nameA);

        // CRITICAL FIX: Instantly remove their active combat statuses
        ModEvents.clearCombatTag(playerA);
        ModEvents.clearCombatTag(playerB);
    }

    public static boolean isInDuelWithEachOther(String playerA, String playerB) {
        return playerB.equalsIgnoreCase(activeDuels.get(playerA));
    }

    public static boolean isInAnyDuel(String player) {
        return activeDuels.containsKey(player);
    }

    public static String getOpponent(String player) {
        return activeDuels.get(player);
    }

    public static void endDuel(String player) {
        String opponent = activeDuels.remove(player);
        if (opponent != null) {
            activeDuels.remove(opponent);
        }
    }
}
