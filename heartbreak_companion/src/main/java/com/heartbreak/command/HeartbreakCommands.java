package com.heartbreak.command;

import com.heartbreak.manager.PartnerManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.border.WorldBorder;

import java.util.Map;
import java.util.UUID;

public class HeartbreakCommands {

    private static int roleAssignTick = -1;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerValentineCommands(dispatcher);
            registerCupidCommands(dispatcher);
            registerHeartbreakerCommands(dispatcher);
            registerAdminCommands(dispatcher);
        });

        // Role assignment timer
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (roleAssignTick > 0) {
                roleAssignTick--;
                if (roleAssignTick == 0) {
                    roleAssignTick = -1;
                    PartnerManager.assignRoles(server);
                }
            }
        });
    }

    private static void registerValentineCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("valentine")
            .then(CommandManager.literal("accept").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                PartnerManager.acceptProposal(player, ctx.getSource().getServer());
                return 1;
            }))
            .then(CommandManager.literal("decline").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                PartnerManager.declineProposal(player, ctx.getSource().getServer());
                return 1;
            }))
            .then(CommandManager.literal("lonewolf").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                PartnerManager.declareLoneWolf(player);
                return 1;
            }))
            .then(CommandManager.literal("task").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                String task = PartnerManager.getTask(player.getUuid());
                player.sendMessage(Text.literal("💌 Your secret task: " + task).formatted(Formatting.GOLD), false);
                return 1;
            }))
            .then(CommandManager.literal("partner").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                UUID partnerUUID = PartnerManager.getPartner(player.getUuid());
                if (partnerUUID == null) {
                    player.sendMessage(Text.literal("You don't have a Valentine.").formatted(Formatting.RED), false);
                    return 0;
                }
                ServerPlayerEntity partner = ctx.getSource().getServer().getPlayerManager().getPlayer(partnerUUID);
                if (partner == null) {
                    player.sendMessage(Text.literal("Your partner is offline.").formatted(Formatting.GRAY), false);
                    return 0;
                }
                // Heart Locket info
                double x = partner.getX(), y = partner.getY(), z = partner.getZ();
                float health = partner.getHealth();
                player.sendMessage(Text.literal(
                    "💗 " + partner.getName().getString() +
                    " | HP: " + String.format("%.1f", health / 2) + "❤" +
                    " | XYZ: " + (int)x + " " + (int)y + " " + (int)z
                ).formatted(Formatting.LIGHT_PURPLE), false);
                return 1;
            }))
            .then(CommandManager.literal("propose")
                .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity from = ctx.getSource().getPlayerOrException();
                    ServerPlayerEntity to = EntityArgumentType.getPlayer(ctx, "player");
                    PartnerManager.sendProposal(from, to);
                    return 1;
                })))
        );
    }

    private static void registerCupidCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cupid")
            .then(CommandManager.literal("reveal").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                if (!PartnerManager.isCupid(player.getUuid())) {
                    player.sendMessage(Text.literal("You are not the Cupid!").formatted(Formatting.RED), false);
                    return 0;
                }
                MinecraftServer server = ctx.getSource().getServer();
                server.getPlayerManager().getPlayerList().forEach(p -> p.setGlowing(true));
                server.getPlayerManager().broadcast(Text.literal("💘 Cupid reveals all! Players glow for 10 seconds!").formatted(Formatting.LIGHT_PURPLE), false);
                new Thread(() -> {
                    try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
                    server.getPlayerManager().getPlayerList().forEach(p -> p.setGlowing(false));
                }).start();
                return 1;
            }))
            .then(CommandManager.literal("buff")
                .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity cupidPlayer = ctx.getSource().getPlayerOrException();
                    if (!PartnerManager.isCupid(cupidPlayer.getUuid())) {
                        cupidPlayer.sendMessage(Text.literal("You are not the Cupid!").formatted(Formatting.RED), false);
                        return 0;
                    }
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1200, 1, false, true));
                    UUID partnerUUID = PartnerManager.getPartner(target.getUuid());
                    if (partnerUUID != null) {
                        ServerPlayerEntity partner = ctx.getSource().getServer().getPlayerManager().getPlayer(partnerUUID);
                        if (partner != null) partner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1200, 1, false, true));
                    }
                    ctx.getSource().getServer().getPlayerManager().broadcast(
                        Text.literal("💘 Cupid blessed " + target.getName().getString() + " and their partner with Regeneration!").formatted(Formatting.LIGHT_PURPLE), false);
                    return 1;
                })))
        );
    }

    private static void registerHeartbreakerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("heartbreaker")
            .then(CommandManager.literal("tasks").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                if (!PartnerManager.isHeartbreaker(player.getUuid())) {
                    player.sendMessage(Text.literal("You are not the Heartbreaker!").formatted(Formatting.RED), false);
                    return 0;
                }
                Map<UUID, String> allTasks = PartnerManager.getAllTasks();
                if (allTasks.isEmpty()) {
                    player.sendMessage(Text.literal("No tasks assigned yet.").formatted(Formatting.GRAY), false);
                    return 0;
                }
                player.sendMessage(Text.literal("💔 All Secret Tasks:").formatted(Formatting.RED, Formatting.BOLD), false);
                allTasks.forEach((uuid, task) -> {
                    ServerPlayerEntity t = ctx.getSource().getServer().getPlayerManager().getPlayer(uuid);
                    String name = t != null ? t.getName().getString() : uuid.toString().substring(0, 8);
                    player.sendMessage(Text.literal("  " + name + ": " + task).formatted(Formatting.RED), false);
                });
                return 1;
            }))
        );
    }

    private static void registerAdminCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("heartbreak")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.literal("start").executes(ctx -> {
                MinecraftServer server = ctx.getSource().getServer();
                PartnerManager.start();
                // Give everyone a rose
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    net.minecraft.item.ItemStack rose = new net.minecraft.item.ItemStack(net.minecraft.item.Items.POPPY);
                    rose.setCustomName(Text.literal("Valentine's Rose").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
                    p.getInventory().insertStack(rose);
                }
                // Set world border
                WorldBorder border = server.getOverworld().getWorldBorder();
                border.setCenter(0, 0);
                border.setSize(500);
                // Schedule roles in 30 minutes
                roleAssignTick = 30 * 60 * 20;
                server.getPlayerManager().broadcast(
                    Text.literal("💝 Heartbreak SMP has begun! You have 5 minutes to find your Valentine! /valentine propose <player> to propose, /valentine lonewolf to go solo!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
                return 1;
            }))
            .then(CommandManager.literal("stop").executes(ctx -> {
                PartnerManager.reset();
                roleAssignTick = -1;
                ctx.getSource().sendFeedback(() -> Text.literal("Heartbreak SMP stopped.").formatted(Formatting.YELLOW), true);
                return 1;
            }))
            .then(CommandManager.literal("assignroles").executes(ctx -> {
                PartnerManager.assignRoles(ctx.getSource().getServer());
                return 1;
            }))
        );
    }
}
