package org.evocraft.evoessentials.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocombat.combat.CombatLogManager;
import org.evocraft.evoessentials.EvoEssentials;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnManager {

    // Am adăugat propriul LOGGER pentru această clasă
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SPAWN_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocore_spawn.json");
    private static final Object SPAWN_LOCK = new Object();

    // Clasă internă pentru structura fișierului JSON
    private static class SpawnData {
        String dimension = "minecraft:overworld";
        double x = 0, y = 80, z = 0;
        float yaw = 0, pitch = 0;
    }

    private static SpawnData currentSpawn = new SpawnData();

    static {
        loadSpawn();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Comanda /spawn - disponibilă pentru toți jucătorii
        dispatcher.register(Commands.literal("spawn")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    teleportToSpawn(player);
                    return 1;
                })
        );

        // Comanda /setspawn - restricționată la STAFF (nivel permisiune 2)
        dispatcher.register(Commands.literal("setspawn")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    setSpawn(player);
                    return 1;
                })
        );
    }

    public static void setSpawn(ServerPlayer player) {
        synchronized (SPAWN_LOCK) {
            SpawnData next = new SpawnData();
            next.dimension = player.serverLevel().dimension().location().toString();
            next.x = player.getX();
            next.y = player.getY();
            next.z = player.getZ();
            next.yaw = player.getYRot();
            next.pitch = player.getXRot();
            currentSpawn = next;
            saveSpawn();
        }
        player.sendSystemMessage(Component.literal("§a[✔] Server spawn was set at your current location!"));
    }

    public static void teleportToSpawn(ServerPlayer player) {
        if (player == null) return;
        if (CombatLogManager.isInCombat(player)) {
            player.sendSystemMessage(Component.literal("§c✖ You cannot teleport to spawn while in combat!"));
            return;
        }

        String dimStr;
        double sx, sy, sz;
        float syaw, spitch;
        synchronized (SPAWN_LOCK) {
            dimStr = currentSpawn.dimension;
            sx = currentSpawn.x;
            sy = currentSpawn.y;
            sz = currentSpawn.z;
            syaw = currentSpawn.yaw;
            spitch = currentSpawn.pitch;
        }

        MinecraftServer server = player.getServer();
        ResourceLocation dimLoc = dimStr != null ? ResourceLocation.tryParse(dimStr) : null;
        if (dimLoc == null) {
            dimLoc = Level.OVERWORLD.location();
        }
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel targetLevel = server != null ? server.getLevel(dimKey) : null;

        if (targetLevel == null || !Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)) {
            player.sendSystemMessage(Component.literal("§c[!] Error: spawn dimension was not found!"));
            return;
        }

        // Folosim TeleportManager pentru delay-ul de 3 secunde
        TeleportManager.queueTeleport(player, "Spawn", () -> {
            if (!player.hasDisconnected() && player.isAlive()) {
                player.teleportTo(targetLevel, sx, sy, sz, syaw, spitch);
                player.sendSystemMessage(Component.literal("§a[✔] Welcome back to Spawn!"));
            }
        });
    }

    private static void saveSpawn() {
        synchronized (SPAWN_LOCK) {
            File parent = SPAWN_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(SPAWN_FILE)) {
                GSON.toJson(currentSpawn, writer);
            } catch (IOException e) {
                LOGGER.error("Could not save spawn!", e);
            }
        }
    }

    private static void loadSpawn() {
        synchronized (SPAWN_LOCK) {
            if (SPAWN_FILE.exists()) {
                try (FileReader reader = new FileReader(SPAWN_FILE)) {
                    SpawnData loaded = GSON.fromJson(reader, SpawnData.class);
                    if (loaded != null) {
                        if (loaded.dimension == null || loaded.dimension.isBlank()) {
                            loaded.dimension = "minecraft:overworld";
                        }
                        if (!Double.isFinite(loaded.x) || !Double.isFinite(loaded.y) || !Double.isFinite(loaded.z)) {
                            loaded.x = 0;
                            loaded.y = 80;
                            loaded.z = 0;
                        }
                        currentSpawn = loaded;
                    }
                } catch (Exception e) {
                    LOGGER.error("Could not load spawn!", e);
                }
            }
        }
    }
}
