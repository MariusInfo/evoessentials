package org.evocraft.evoessentials.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WarpManager {
    private static volatile WarpManager INSTANCE;
    public static synchronized WarpManager get() { if (INSTANCE == null) INSTANCE = new WarpManager(); return INSTANCE; }

    public static class Warp {
        public String name;
        public ItemStack icon;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
    }

    public final Map<String, Warp> warps = new ConcurrentHashMap<>();
    private final File file = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocraft_warps.nbt");

    public WarpManager() { load(); }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            CompoundTag root = new CompoundTag();
            ListTag warpList = new ListTag();
            for (Warp w : warps.values()) {
                if (w == null || w.name == null || w.name.isBlank()) continue;
                CompoundTag wTag = new CompoundTag();
                wTag.putString("Name", w.name);
                wTag.put("Icon", (w.icon != null ? w.icon : ItemStack.EMPTY).save(new CompoundTag()));
                wTag.putString("Dimension", w.dimension != null ? w.dimension : "minecraft:overworld");
                wTag.putDouble("X", w.x);
                wTag.putDouble("Y", w.y);
                wTag.putDouble("Z", w.z);
                wTag.putFloat("Yaw", w.yaw);
                wTag.putFloat("Pitch", w.pitch);
                warpList.add(wTag);
            }
            root.put("Warps", warpList);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized void load() {
        warps.clear();
        if (!file.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null) return;
            ListTag warpList = root.getList("Warps", 10);
            for (int i = 0; i < warpList.size(); i++) {
                CompoundTag wTag = warpList.getCompound(i);
                String name = wTag.getString("Name");
                if (name == null || name.isBlank()) continue;
                double x = wTag.getDouble("X");
                double y = wTag.getDouble("Y");
                double z = wTag.getDouble("Z");
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
                Warp w = new Warp();
                w.name = name;
                w.icon = ItemStack.of(wTag.getCompound("Icon"));
                w.dimension = wTag.getString("Dimension");
                w.x = x;
                w.y = y;
                w.z = z;
                w.yaw = Float.isFinite(wTag.getFloat("Yaw")) ? wTag.getFloat("Yaw") : 0f;
                w.pitch = Float.isFinite(wTag.getFloat("Pitch")) ? wTag.getFloat("Pitch") : 0f;
                warps.put(name.toLowerCase(Locale.ROOT), w);
            }
            System.out.println("[EvoCore] Loaded " + warps.size() + " warp(s) from config.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized void createWarp(String name, ServerPlayer player, ItemStack icon) {
        if (name == null || name.isBlank() || player == null) return;
        Warp w = new Warp();
        w.name = name.trim();
        w.icon = (icon != null ? icon.copy() : ItemStack.EMPTY);
        w.dimension = player.level().dimension().location().toString();
        w.x = player.getX();
        w.y = player.getY();
        w.z = player.getZ();
        w.yaw = player.getYRot();
        w.pitch = player.getXRot();
        warps.put(w.name.toLowerCase(Locale.ROOT), w);
        save();
    }

    public synchronized boolean deleteWarp(String name) {
        if (name == null || name.isBlank()) return false;
        Warp removed = warps.remove(name.trim().toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public synchronized Warp getWarp(String name) {
        if (name == null || name.isBlank()) return null;
        return warps.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized Map<String, Warp> getWarpsSnapshot() {
        return new LinkedHashMap<>(warps);
    }
}
