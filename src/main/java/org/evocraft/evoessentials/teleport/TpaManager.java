package org.evocraft.evoessentials.teleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.evocraft.evocombat.combat.CombatLogManager;
import org.evocraft.evocore.data.PlayerStatsManager;
import org.evocraft.evoessentials.EvoEssentials;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TpaManager {

    private static class TpaRequest {
        final ServerPlayer sender;
        final boolean isTpaHere;
        final long expireTime;

        TpaRequest(ServerPlayer sender, boolean isTpaHere) {
            this.sender = sender;
            this.isTpaHere = isTpaHere;
            this.expireTime = System.currentTimeMillis() + 60000L; // Expiră în 60 de secunde
        }
    }

    private static final Map<UUID, TpaRequest> activeRequests = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Comenzi pentru accept / deny
        dispatcher.register(Commands.literal("tpaccept").executes(context -> acceptTpaRequest(context.getSource())));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> denyTpaRequest(context.getSource())));

        // Comanda /tpa <jucator> - Reintrodusă pentru utilizare directă din chat
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            sendTpaRequest(context.getSource().getPlayerOrException(), target.getName().getString(), false);
                            return 1;
                        })
                )
        );

        // Comanda /tpahere <jucator> - Reintrodusă pentru utilizare directă din chat
        dispatcher.register(Commands.literal("tpahere")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            sendTpaRequest(context.getSource().getPlayerOrException(), target.getName().getString(), true);
                            return 1;
                        })
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        activeRequests.remove(uuid);
        activeRequests.entrySet().removeIf(entry -> entry.getValue().sender.getUUID().equals(uuid));
    }

    public static void sendTpaRequest(ServerPlayer sender, String targetName, boolean isTpaHere) {
        if (sender == null || targetName == null || targetName.isBlank()) return;
        if (CombatLogManager.isInCombat(sender)) {
            sender.sendSystemMessage(Component.literal("§c✖ You cannot send teleport requests while in combat!"));
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null || target.hasDisconnected()) {
            sender.sendSystemMessage(Component.literal("§c[!] That player is not online!"));
            return;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§c[!] You cannot send a request to yourself!"));
            return;
        }

        // VERIFICARE BLOCK LIST ȘI SETĂRI (Din Hub Tab 7)
        PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(target.getUUID());
        if (stats != null) {
            boolean allowTpa;
            boolean blocked;
            synchronized (stats) {
                allowTpa = stats.allowTPA;
                blocked = stats.blockedPlayers.contains(sender.getUUID().toString());
            }
            if (!allowTpa) {
                sender.sendSystemMessage(Component.literal("§c[!] This player has teleport requests disabled."));
                return;
            }
            if (blocked) {
                sender.sendSystemMessage(Component.literal("§c[!] This player has blocked you. You cannot send them requests."));
                return;
            }
        }

        if (activeRequests.containsKey(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§c[!] This player already has a pending request. Please wait!"));
            return;
        }

        activeRequests.put(target.getUUID(), new TpaRequest(sender, isTpaHere));

        String type = isTpaHere ? "§eTPA Here" : "§eTPA";
        sender.sendSystemMessage(Component.literal("§a[✔] Sent a " + type + " §arequest to §e" + target.getName().getString() + "§a."));

        target.sendSystemMessage(Component.literal("§e[!] §6" + sender.getName().getString() + " §awants " + (isTpaHere ? "you to teleport to them" : "to teleport to you") + "."));
        target.sendSystemMessage(Component.literal("§aUse §e/tpaccept §aor §c/tpdeny§a."));

        // Mesajul ascuns pentru notificarea Slide-In de pe ecran
        String hiddenMsg = "§0~EVOTPA~" + sender.getUUID().toString() + ":" + sender.getName().getString() + ":" + (isTpaHere ? "TPA_HERE" : "TPA");
        target.sendSystemMessage(Component.literal(hiddenMsg));
    }

    private static int acceptTpaRequest(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;
        UUID targetId = target.getUUID();

        TpaRequest req = activeRequests.get(targetId);
        if (req == null) {
            target.sendSystemMessage(Component.literal("§c[!] You do not have any pending teleport requests."));
            return 0;
        }

        if (req.sender.hasDisconnected() || !req.sender.isAlive()) {
            activeRequests.remove(targetId);
            target.sendSystemMessage(Component.literal("§c[!] The player who sent the request is no longer online."));
            return 0;
        }

        ServerPlayer teleportingPlayer = req.isTpaHere ? target : req.sender;
        if (CombatLogManager.isInCombat(teleportingPlayer)) {
            target.sendSystemMessage(Component.literal("§c✖ Teleport cannot proceed while in combat!"));
            return 0;
        }

        activeRequests.remove(targetId);

        target.sendSystemMessage(Component.literal("§a[✔] Accepted the teleport request from §e" + req.sender.getName().getString() + "§a."));
        req.sender.sendSystemMessage(Component.literal("§a[✔] §e" + target.getName().getString() + " §aaccepted your request!"));

        if (req.isTpaHere) {
            TeleportManager.queueTeleport(target, req.sender.getName().getString(), () -> {
                if (!req.sender.hasDisconnected() && req.sender.isAlive() && !target.hasDisconnected() && target.isAlive()) {
                    target.teleportTo(req.sender.serverLevel(), req.sender.getX(), req.sender.getY(), req.sender.getZ(), req.sender.getYRot(), req.sender.getXRot());
                    target.sendSystemMessage(Component.literal("§aTeleported to " + req.sender.getName().getString() + "!"));
                } else {
                    target.sendSystemMessage(Component.literal("§c[!] Teleport cancelled: target player went offline."));
                }
            });
        } else {
            TeleportManager.queueTeleport(req.sender, target.getName().getString(), () -> {
                if (!target.hasDisconnected() && target.isAlive() && !req.sender.hasDisconnected() && req.sender.isAlive()) {
                    req.sender.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                    req.sender.sendSystemMessage(Component.literal("§aTeleported to " + target.getName().getString() + "!"));
                } else {
                    req.sender.sendSystemMessage(Component.literal("§c[!] Teleport cancelled: target player went offline."));
                }
            });
        }
        return 1;
    }

    private static int denyTpaRequest(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;
        UUID targetId = target.getUUID();

        TpaRequest req = activeRequests.remove(targetId);
        if (req == null) {
            target.sendSystemMessage(Component.literal("§c[!] You do not have any pending requests."));
            return 0;
        }

        target.sendSystemMessage(Component.literal("§c[!] Denied the teleport request from §e" + req.sender.getName().getString() + "§c."));

        if (!req.sender.hasDisconnected()) {
            req.sender.sendSystemMessage(Component.literal("§c[!] §e" + target.getName().getString() + " §cdenied your request."));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !activeRequests.isEmpty()) {
            long now = System.currentTimeMillis();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Iterator<Map.Entry<UUID, TpaRequest>> it = activeRequests.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, TpaRequest> entry = it.next();
                if (now > entry.getValue().expireTime) {
                    if (server != null) {
                        ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
                        if (target != null && !target.hasDisconnected()) {
                            target.sendSystemMessage(Component.literal("§c[!] The teleport request from §e" + entry.getValue().sender.getName().getString() + " §chas expired."));
                        }
                    }
                    if (!entry.getValue().sender.hasDisconnected()) {
                        entry.getValue().sender.sendSystemMessage(Component.literal("§c[!] Your request has expired."));
                    }
                    it.remove();
                }
            }
        }
    }
}
