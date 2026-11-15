package org.evocraft.evoessentials.teleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.evocraft.evocombat.combat.CombatLogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RTPCommand {

    // Salvăm momentul în care jucătorul a folosit comanda
    private static final Map<UUID, Long> rtpCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 30 * 60 * 1000L; // 30 minute în milisecunde

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (CombatLogManager.isInCombat(player)) {
                        player.sendSystemMessage(Component.literal("§c✖ You cannot use RTP while in combat!"));
                        return 0;
                    }

                    UUID uuid = player.getUUID();
                    long currentTime = System.currentTimeMillis();

                    // Verificăm Cooldown-ul (Dacă nu e admin)
                    Long lastUse = rtpCooldowns.get(uuid);
                    if (!player.hasPermissions(2) && lastUse != null) {
                        long timePassed = currentTime - lastUse;
                        if (timePassed < COOLDOWN_MS) {
                            long secondsLeft = (COOLDOWN_MS - timePassed) / 1000;
                            long minutes = secondsLeft / 60;
                            long seconds = secondsLeft % 60;
                            player.sendSystemMessage(Component.literal("§c⏳ You must wait " + minutes + "m " + seconds + "s before using RTP again!"));
                            return 0;
                        }
                    }

                    // Pornește teleportarea asincronă cu 3 secunde așteptare
                    TeleportManager.queueTeleport(player, "Random Teleport (RTP)", () -> {
                        if (player.hasDisconnected() || !player.isAlive()) return;
                        ServerLevel level = player.serverLevel();
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        WorldBorder border = level.getWorldBorder();
                        int maxRadius = (int) Math.min(5000.0, Math.max(64.0, (border.getSize() / 2.0) - 16.0));
                        int centerX = (int) Math.round(border.getCenterX());
                        int centerZ = (int) Math.round(border.getCenterZ());
                        boolean hasCeiling = level.dimensionType().hasCeiling();

                        BlockPos finalPos = null;
                        player.sendSystemMessage(Component.literal("§e[EvoCore] Searching for a safe location..."));

                        // Încercăm de maxim 12 ori să găsim pământ uscat și sigur
                        for (int i = 0; i < 12; i++) {
                            int targetX = centerX + random.nextInt(-maxRadius, maxRadius + 1);
                            int targetZ = centerZ + random.nextInt(-maxRadius, maxRadius + 1);
                            if (!border.isWithinBounds(targetX, targetZ)) {
                                continue;
                            }

                            // Forțăm serverul să asigure stadiul FULL al chunk-ului
                            level.getChunk(targetX >> 4, targetZ >> 4, ChunkStatus.FULL, true);

                            int targetY;
                            if (hasCeiling) {
                                targetY = findCeilingSafeY(level, targetX, targetZ);
                            } else {
                                targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
                            }

                            if (targetY <= level.getMinBuildHeight() + 5 || targetY >= level.getMaxBuildHeight() - 2) {
                                continue;
                            }

                            BlockPos belowPos = new BlockPos(targetX, targetY - 1, targetZ);
                            BlockPos atPos = new BlockPos(targetX, targetY, targetZ);
                            BlockPos abovePos = new BlockPos(targetX, targetY + 1, targetZ);
                            BlockState blockBelow = level.getBlockState(belowPos);
                            BlockState blockAt = level.getBlockState(atPos);
                            BlockState blockAbove = level.getBlockState(abovePos);

                            if (!blockBelow.isSolid() || !blockBelow.getFluidState().isEmpty() || blockBelow.liquid()) {
                                continue;
                            }
                            if (!blockAt.isAir() || !blockAt.getFluidState().isEmpty()
                                    || !blockAbove.isAir() || !blockAbove.getFluidState().isEmpty()) {
                                continue;
                            }

                            finalPos = atPos;
                            break;
                        }

                        if (finalPos != null) {
                            player.teleportTo(level, finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                            player.sendSystemMessage(Component.literal("§a✔ You were teleported to a random area!"));
                            rtpCooldowns.put(uuid, System.currentTimeMillis());
                        } else {
                            player.sendSystemMessage(Component.literal("§c✖ Could not find a safe location (only oceans/invalid areas). Try again!"));
                        }
                    });

                    return 1;
                })
        );
    }

    private static int findCeilingSafeY(ServerLevel level, int x, int z) {
        int top = Math.min(level.getMaxBuildHeight() - 3, 120);
        int bottom = level.getMinBuildHeight() + 6;
        for (int y = top; y >= bottom; y--) {
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            BlockState at = level.getBlockState(new BlockPos(x, y, z));
            BlockState above = level.getBlockState(new BlockPos(x, y + 1, z));
            if (below.isSolid() && below.getFluidState().isEmpty() && !below.liquid()
                    && at.isAir() && at.getFluidState().isEmpty()
                    && above.isAir() && above.getFluidState().isEmpty()) {
                return y;
            }
        }
        return -1;
    }
}
