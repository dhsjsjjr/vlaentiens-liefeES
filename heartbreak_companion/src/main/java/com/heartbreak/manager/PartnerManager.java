package com.heartbreak.manager;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.*;

public class PartnerManager {

    private static final Map<UUID, UUID> partners = new HashMap<>();
    private static final Set<UUID> loneWolves = new HashSet<>();
    private static final Map<UUID, UUID> pendingProposals = new HashMap<>();
    private static UUID cupid = null;
    private static UUID heartbreaker = null;
    private static final Map<UUID, String> tasks = new HashMap<>();
    private static boolean gameActive = false;

    private static final String[] TASK_POOL = {
        "Give your partner a gold ingot without them asking",
        "Build something in your base that surprises your partner",
        "Get your partner to say 'love' in chat",
        "Craft a cake and place it near your partner's base",
        "Give your partner a flower",
        "Kill a hostile mob together with your partner",
        "Cook food and give it to your partner",
        "Mine diamonds and share some with your partner",
        "Sleep in a bed next to your partner",
        "Protect your partner from a mob attack",
        "Trade with a villager while your partner watches",
        "Build your partner a small gift structure"
    };

    public static void start() { reset(); gameActive = true; }

    public static void reset() {
        partners.clear(); loneWolves.clear(); pendingProposals.clear();
        tasks.clear(); cupid = null; heartbreaker = null; gameActive = false;
    }

    public static boolean isActive() { return gameActive; }

    public static void sendProposal(ServerPlayerEntity from, ServerPlayerEntity to) {
        if (!gameActive) { from.sendMessage(Text.literal("The game hasn't started yet!").formatted(Formatting.RED), false); return; }
        if (loneWolves.contains(to.getUuid())) { from.sendMessage(Text.literal(to.getName().getString() + " is a Lone Wolf!").formatted(Formatting.RED), false); return; }
        if (hasPartner(to.getUuid())) { from.sendMessage(Text.literal(to.getName().getString() + " already has a Valentine!").formatted(Formatting.RED), false); return; }
        if (hasPartner(from.getUuid())) { from.sendMessage(Text.literal("You already have a Valentine!").formatted(Formatting.RED), false); return; }
        if (loneWolves.contains(from.getUuid())) { from.sendMessage(Text.literal("Lone Wolves can't propose!").formatted(Formatting.RED), false); return; }
        pendingProposals.put(from.getUuid(), to.getUuid());
        to.sendMessage(Text.literal("💐 " + from.getName().getString() + " has proposed to you! Type /valentine accept or /valentine decline").formatted(Formatting.LIGHT_PURPLE), false);
        from.sendMessage(Text.literal("💐 Proposal sent to " + to.getName().getString() + "!").formatted(Formatting.LIGHT_PURPLE), false);
    }

    public static void acceptProposal(ServerPlayerEntity player, MinecraftServer server) {
        UUID proposerUUID = getPendingProposalFor(player.getUuid());
        if (proposerUUID == null) { player.sendMessage(Text.literal("You have no pending proposals!").formatted(Formatting.RED), false); return; }
        pendingProposals.remove(proposerUUID);
        setPartners(proposerUUID, player.getUuid());
        assignTasks(proposerUUID, player.getUuid());
        ServerPlayerEntity proposer = server.getPlayerManager().getPlayer(proposerUUID);
        String proposerName = proposer != null ? proposer.getName().getString() : "Someone";
        server.getPlayerManager().broadcast(Text.literal("💕 " + proposerName + " and " + player.getName().getString() + " are now Valentines!").formatted(Formatting.LIGHT_PURPLE), false);
        if (proposer != null) proposer.sendMessage(Text.literal("💕 Check your secret task: /valentine task").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("💕 Check your secret task: /valentine task").formatted(Formatting.GOLD), false);
    }

    public static void declineProposal(ServerPlayerEntity player, MinecraftServer server) {
        UUID proposerUUID = getPendingProposalFor(player.getUuid());
        if (proposerUUID == null) { player.sendMessage(Text.literal("You have no pending proposals!").formatted(Formatting.RED), false); return; }
        pendingProposals.remove(proposerUUID);
        player.sendMessage(Text.literal("Proposal declined.").formatted(Formatting.GRAY), false);
        ServerPlayerEntity proposer = server.getPlayerManager().getPlayer(proposerUUID);
        if (proposer != null) proposer.sendMessage(Text.literal("💔 " + player.getName().getString() + " declined your proposal.").formatted(Formatting.RED), false);
    }

    public static void declareLoneWolf(ServerPlayerEntity player) {
        if (!gameActive) { player.sendMessage(Text.literal("The game hasn't started yet!").formatted(Formatting.RED), false); return; }
        if (hasPartner(player.getUuid())) { player.sendMessage(Text.literal("You already have a Valentine!").formatted(Formatting.RED), false); return; }
        loneWolves.add(player.getUuid());
        player.getServer().getPlayerManager().broadcast(Text.literal("🐺 " + player.getName().getString() + " declared themselves a Lone Wolf!").formatted(Formatting.GRAY), false);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 0, false, false, true));
    }

    public static void assignRoles(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (players.size() < 2) return;
        Collections.shuffle(players);
        cupid = players.get(0).getUuid();
        heartbreaker = players.get(1).getUuid();
        server.getPlayerManager().broadcast(Text.literal("💘 Roles assigned! Two players hold special power...").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        ServerPlayerEntity c = server.getPlayerManager().getPlayer(cupid);
        ServerPlayerEntity h = server.getPlayerManager().getPlayer(heartbreaker);
        if (c != null) {
            ItemStack arrow = new ItemStack(Items.ARROW);
            arrow.setCustomName(Text.literal("Cupid's Arrow").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            c.getInventory().insertStack(arrow);
            c.sendMessage(Text.literal("💘 You are CUPID! /cupid reveal to glow all players. /cupid buff <player> to bless a couple!").formatted(Formatting.GOLD, Formatting.BOLD), false);
        }
        if (h != null) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setCustomName(Text.literal("Heartbreaker's Dagger").formatted(Formatting.RED, Formatting.BOLD));
            h.getInventory().insertStack(sword);
            h.sendMessage(Text.literal("💔 You are the HEARTBREAKER! /heartbreaker tasks to see everyone's secret tasks!").formatted(Formatting.RED, Formatting.BOLD), false);
        }
    }

    private static void assignTasks(UUID a, UUID b) {
        List<String> pool = new ArrayList<>(Arrays.asList(TASK_POOL));
        Collections.shuffle(pool, new Random());
        tasks.put(a, pool.get(0));
        tasks.put(b, pool.get(1));
    }

    private static void setPartners(UUID a, UUID b) { partners.put(a, b); partners.put(b, a); }
    public static boolean hasPartner(UUID uuid) { return partners.containsKey(uuid); }
    public static UUID getPartner(UUID uuid) { return partners.get(uuid); }
    public static boolean isLoneWolf(UUID uuid) { return loneWolves.contains(uuid); }
    public static boolean isCupid(UUID uuid) { return uuid.equals(cupid); }
    public static boolean isHeartbreaker(UUID uuid) { return uuid.equals(heartbreaker); }
    public static String getTask(UUID uuid) { return tasks.getOrDefault(uuid, "No task assigned yet."); }
    public static Map<UUID, String> getAllTasks() { return Collections.unmodifiableMap(tasks); }
    public static UUID getPendingProposalFor(UUID target) {
        for (Map.Entry<UUID, UUID> e : pendingProposals.entrySet())
            if (e.getValue().equals(target)) return e.getKey();
        return null;
    }
}
