package org.evocraft.evoessentials.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.evocraft.evocore.database.DatabaseManager;
import org.evocraft.evoessentials.EvoEssentials;
import org.evocraft.evoessentials.network.EssentialsPacketHandler;
import org.evocraft.evoessentials.network.S2C_SyncHomesPacket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HomeManager {
    private static volatile HomeManager INSTANCE;
    private static volatile ExecutorService dbExecutor = createExecutor();

    private final Map<UUID, Map<String, HomeLocation>> playerHomes = new ConcurrentHashMap<>();
    private final Set<UUID> loadedPlayers = ConcurrentHashMap.newKeySet();

    public static final PermissionNode<Boolean> PERM_HOME_16 = new PermissionNode<>(new ResourceLocation("evocore:homes.16"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_14 = new PermissionNode<>(new ResourceLocation("evocore:homes.14"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_12 = new PermissionNode<>(new ResourceLocation("evocore:homes.12"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_10 = new PermissionNode<>(new ResourceLocation("evocore:homes.10"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_8 = new PermissionNode<>(new ResourceLocation("evocore:homes.8"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_5 = new PermissionNode<>(new ResourceLocation("evocore:homes.5"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_3 = new PermissionNode<>(new ResourceLocation("evocore:homes.3"), PermissionTypes.BOOLEAN, (p, u, c) -> false);

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EvoEssentials-Homes-DB");
            t.setDaemon(true);
            return t;
        });
    }

    private static synchronized ExecutorService getExecutor() {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            dbExecutor = createExecutor();
        }
        return dbExecutor;
    }

    public static synchronized void shutdown() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dbExecutor.shutdownNow();
            }
        }
    }

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(PERM_HOME_16, PERM_HOME_14, PERM_HOME_12, PERM_HOME_10, PERM_HOME_8, PERM_HOME_5, PERM_HOME_3);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            MinecraftServer server = player.getServer();
            getExecutor().execute(() -> {
                if (get().loadPlayerHomes(uuid) && server != null) {
                    server.execute(() -> {
                        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
                        if (onlinePlayer != null && !onlinePlayer.hasDisconnected()) {
                            get().syncHomes(onlinePlayer);
                        }
                    });
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        HomeManager mgr = INSTANCE;
        if (mgr != null) {
            mgr.playerHomes.remove(uuid);
            mgr.loadedPlayers.remove(uuid);
        }
    }

    public HomeManager() {}
    public static synchronized void initialize() { if (INSTANCE == null) INSTANCE = new HomeManager(); }
    public static synchronized HomeManager get() { if (INSTANCE == null) initialize(); return INSTANCE; }

    public static class HomeLocation {
        public String dimension; public double x, y, z; public float yaw, pitch;
        public HomeLocation(String dim, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dim; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
    }

    public synchronized boolean loadPlayerHomes(UUID uuid) {
        if (uuid == null) return false;
        if (loadedPlayers.contains(uuid)) {
            return true;
        }

        Map<String, HomeLocation> homes = new ConcurrentHashMap<>();
        String query = "SELECT * FROM player_homes WHERE uuid = ?";
        try {
            synchronized (DatabaseManager.get()) {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null) return false;
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String homeName = rs.getString("home_name");
                            String dim = rs.getString("dimension");
                            double x = rs.getDouble("x");
                            double y = rs.getDouble("y");
                            double z = rs.getDouble("z");
                            if (homeName == null || homeName.isBlank() || dim == null || dim.isBlank()
                                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                                continue;
                            }
                            homes.put(homeName.toLowerCase(Locale.ROOT), new HomeLocation(
                                    dim, x, y, z, rs.getFloat("yaw"), rs.getFloat("pitch")
                            ));
                        }
                    }
                }
            }
            playerHomes.put(uuid, homes);
            loadedPlayers.add(uuid);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getMaxHomes(ServerPlayer player) {
        if (player.hasPermissions(4) || PermissionAPI.getPermission(player, PERM_HOME_16)) return 16;
        if (PermissionAPI.getPermission(player, PERM_HOME_14)) return 14;
        if (PermissionAPI.getPermission(player, PERM_HOME_12)) return 12;
        if (PermissionAPI.getPermission(player, PERM_HOME_10)) return 10;
        if (PermissionAPI.getPermission(player, PERM_HOME_8)) return 8;
        if (PermissionAPI.getPermission(player, PERM_HOME_5)) return 5;
        return 3;
    }

    public Map<String, HomeLocation> getHomes(UUID uuid) {
        return playerHomes.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    public void syncHomes(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return;
        UUID uuid = player.getUUID();
        if (!loadedPlayers.contains(uuid)) {
            loadPlayerHomes(uuid);
        }

        Map<String, HomeLocation> homes = playerHomes.getOrDefault(uuid, Collections.emptyMap());
        List<String> homeNames = new ArrayList<>(homes.keySet());
        EssentialsPacketHandler.sendToPlayer(new S2C_SyncHomesPacket(homes.size(), getMaxHomes(player), homeNames), player);
    }

    public void setHome(ServerPlayer player, String homeName) {
        if (player == null) return;
        if (homeName == null || homeName.trim().isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Home name cannot be empty!"));
            return;
        }
        String cleanHomeName = homeName.trim();
        if (cleanHomeName.length() > 50) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Home name cannot exceed 50 characters!"));
            return;
        }

        UUID uuid = player.getUUID();
        if (!loadedPlayers.contains(uuid)) {
            loadPlayerHomes(uuid);
        }

        Map<String, HomeLocation> homes = getHomes(uuid);
        String name = cleanHomeName.toLowerCase(Locale.ROOT);

        if (!homes.containsKey(name) && homes.size() >= getMaxHomes(player)) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] You reached the maximum home limit (" + getMaxHomes(player) + ")!"));
            return;
        }

        String dim = player.level().dimension().location().toString();
        HomeLocation loc = new HomeLocation(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        homes.put(name, loc);
        String uuidString = uuid.toString();

        getExecutor().execute(() -> {
            try {
                synchronized (DatabaseManager.get()) {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null) return;
                    boolean mySql = DatabaseManager.get().isMySql();
                    String upsert = mySql
                            ? "INSERT INTO player_homes (uuid, home_name, dimension, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                              "ON DUPLICATE KEY UPDATE dimension=VALUES(dimension), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)"
                            : "INSERT INTO player_homes (uuid, home_name, dimension, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                              "ON CONFLICT(uuid, home_name) DO UPDATE SET dimension=excluded.dimension, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch";
                    try (PreparedStatement ins = conn.prepareStatement(upsert)) {
                        ins.setString(1, uuidString);
                        ins.setString(2, name);
                        ins.setString(3, loc.dimension);
                        ins.setDouble(4, loc.x);
                        ins.setDouble(5, loc.y);
                        ins.setDouble(6, loc.z);
                        ins.setFloat(7, loc.yaw);
                        ins.setFloat(8, loc.pitch);
                        ins.executeUpdate();
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        player.sendSystemMessage(Component.literal("§a[EvoCore] Home '§e" + cleanHomeName + "§a' has been set!"));
        syncHomes(player);
    }

    public void delHome(ServerPlayer player, String homeName) {
        if (player == null) return;
        if (homeName == null || homeName.trim().isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Home name cannot be empty!"));
            return;
        }
        String cleanHomeName = homeName.trim();

        UUID uuid = player.getUUID();
        if (!loadedPlayers.contains(uuid)) {
            loadPlayerHomes(uuid);
        }

        Map<String, HomeLocation> homes = getHomes(uuid);
        String name = cleanHomeName.toLowerCase(Locale.ROOT);
        String uuidString = uuid.toString();

        if (homes.remove(name) != null) {
            getExecutor().execute(() -> {
                try {
                    synchronized (DatabaseManager.get()) {
                        Connection conn = DatabaseManager.get().getConnection();
                        if (conn == null) return;
                        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_homes WHERE uuid = ? AND home_name = ?")) {
                            stmt.setString(1, uuidString); stmt.setString(2, name); stmt.executeUpdate();
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });

            player.sendSystemMessage(Component.literal("§a[EvoCore] Home '§e" + cleanHomeName + "§a' has been deleted!"));
            syncHomes(player);
        } else {
            player.sendSystemMessage(Component.literal("§c[EvoCore] You do not have a home named '" + cleanHomeName + "'!"));
        }
    }

    public void teleportHome(ServerPlayer player, String homeName) {
        if (player == null || homeName == null || homeName.trim().isEmpty()) return;
        String cleanHomeName = homeName.trim();
        UUID uuid = player.getUUID();
        if (!loadedPlayers.contains(uuid)) {
            loadPlayerHomes(uuid);
        }

        Map<String, HomeLocation> homes = getHomes(uuid);
        String name = cleanHomeName.toLowerCase(Locale.ROOT);

        HomeLocation loc = homes.get(name);
        if (loc == null) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] You do not have a home named '" + cleanHomeName + "'!"));
            return;
        }

        ResourceLocation dimLoc = ResourceLocation.tryParse(loc.dimension);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = (dimLoc != null && server != null) ? server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc)) : null;

        if (level != null && Double.isFinite(loc.x) && Double.isFinite(loc.y) && Double.isFinite(loc.z)) {
            TeleportManager.queueTeleport(player, "Home: " + cleanHomeName, () -> {
                if (!player.hasDisconnected() && player.isAlive()) {
                    player.teleportTo(level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
                    player.sendSystemMessage(Component.literal("§a[EvoCore] Teleported to '§e" + cleanHomeName + "§a'!"));
                }
            });
        } else {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Error! The home dimension no longer exists."));
        }
    }
}
