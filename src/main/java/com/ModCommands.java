package com;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "chosenkeepinv")
public class ModCommands {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Base /keepinv command tree
        dispatcher.register(Commands.literal("keepinv")
                .requires(source -> source.hasPermission(0))
                // Sub-command: /keepinv global [true/false]
                .then(Commands.literal("toggle")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    String username = player.getScoreboardName();

                                    ModPersistentData.get(player.serverLevel()).setChoice(username, enabled);
                                    player.sendSystemMessage(Component.literal("§aKeep Inventory status set to: " + enabled));
                                    return 1;
                                })
                        )
                )
                // Sub-command: /keepinv pvp [true/false]
                .then(Commands.literal("pvp-safe")
                        .then(Commands.argument("bypass", BoolArgumentType.bool())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

                                    // true = unaffected by pvp (bypass combat tagging)
                                    // false = affected by pvp (default behavior)
                                    boolean bypass = BoolArgumentType.getBool(context, "bypass");
                                    String username = player.getScoreboardName();

                                    ModPersistentData.get(player.serverLevel()).setPvpBypass(username, bypass);

                                    if (bypass) {
                                        player.sendSystemMessage(Component.literal("§aYour inventory is now safe from PVP deaths"));
                                    } else {
                                        player.sendSystemMessage(Component.literal("§eYour inventory is now affected by PVP, be carefull"));
                                    }
                                    return 1;
                                })
                        )
                )
        );

        // /duel command tree
        dispatcher.register(Commands.literal("duel")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("deny")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

                            String username = player.getScoreboardName();
                            String senderName = DuelManager.rejectInvite(username);

                            if (senderName != null) {
                                player.sendSystemMessage(Component.literal("§cYou denied duel request."));
                                ServerPlayer sender = receiverLookup(player, senderName);
                                if (sender != null) {
                                    sender.sendSystemMessage(Component.literal("§c" + username + " denied your duel request."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("§cNo duel requests are active."));
                            }
                            return 1;
                        })
                )
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer sender)) return 0;

                            ServerPlayer receiver = EntityArgument.getPlayer(context, "target");
                            String senderName = sender.getScoreboardName();
                            String receiverName = receiver.getScoreboardName();

                            if (senderName.equals(receiverName)) {
                                sender.sendSystemMessage(Component.literal("§cYou cant duel yourself"));
                                return 0;
                            }

                            if (DuelManager.hasInviteFrom(senderName, receiverName)) {
                                DuelManager.startDuel(sender, receiver);
                                sender.sendSystemMessage(Component.literal("§aDuel accepted, keep inventory enabled for this fight."));
                                receiver.sendSystemMessage(Component.literal("§a" + senderName + " accepted your request, battle started."));
                            } else {
                                DuelManager.sendInvite(senderName, receiverName);
                                sender.sendSystemMessage(Component.literal("§eDuel request send to the " + receiverName));

                                MutableComponent message = Component.literal("§e" + senderName + " §send you a duel request!\n");

                                MutableComponent acceptBtn = Component.literal("§a§l[ACCEPT] ")
                                        .withStyle(style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel " + senderName))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aPress to start duel"))));

                                MutableComponent denyBtn = Component.literal("§c§l[DENY]")
                                        .withStyle(style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel deny"))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§cPress to deny request"))));

                                message.append(acceptBtn).append(denyBtn);
                                receiver.sendSystemMessage(message);
                            }
                            return 1;
                        })
                )
        );
    }

    private static ServerPlayer receiverLookup(ServerPlayer base, String name) {
        return base.getServer().getPlayerList().getPlayerByName(name);
    }
}

