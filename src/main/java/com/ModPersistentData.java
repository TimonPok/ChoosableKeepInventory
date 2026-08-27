package com;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class ModPersistentData extends SavedData {
    private final Map<String, Boolean> playerChoices = new HashMap<>();
    private final Map<String, Boolean> pvpBypassChoices = new HashMap<>();
    private final Map<String, CompoundTag> limboInventories = new HashMap<>();
    private final Set<String> usedLimboDoors = new HashSet<>();

    private final Map<String, CompoundTag> deadCuriosInventories = new HashMap<>();

    public void markDoorAsUsed(BlockPos pos) {
        usedLimboDoors.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        this.setDirty();
    }

    public boolean isDoorUsed(BlockPos pos) {
        return usedLimboDoors.contains(pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    public boolean getChoice(String username) {
        return playerChoices.getOrDefault(username, false);
    }

    public void setChoice(String username, boolean value) {
        playerChoices.put(username, value);
        this.setDirty();
    }

    public boolean getPvpBypass(String username) {
        return pvpBypassChoices.getOrDefault(username, false);
    }

    public void setPvpBypass(String username, boolean value) {
        pvpBypassChoices.put(username, value);
        this.setDirty();
    }

    public void saveLimboInventory(String username, CompoundTag inventoryTag) {
        limboInventories.put(username, inventoryTag);
        this.setDirty();
    }

    public CompoundTag loadAndRemoveLimboInventory(String username) {
        CompoundTag tag = limboInventories.remove(username);
        if (tag != null) {
            this.setDirty();
        }
        return tag;
    }

    public boolean hasLimboInventory(String username) {
        return limboInventories.containsKey(username);
    }

    // НОВЫЕ МЕТОДЫ: Для сохранения и извлечения Curios данных при смерти
    public void saveDeadCurios(String username, CompoundTag curiosTag) {
        deadCuriosInventories.put(username, curiosTag);
        this.setDirty();
    }

    public CompoundTag loadAndRemoveDeadCurios(String username) {
        CompoundTag tag = deadCuriosInventories.remove(username);
        if (tag != null) {
            this.setDirty();
        }
        return tag;
    }

    public boolean hasDeadCurios(String username) {
        return deadCuriosInventories.containsKey(username);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        playerChoices.forEach(playersTag::putBoolean);
        tag.put("playerChoices", playersTag);

        CompoundTag pvpBypassTag = new CompoundTag();
        pvpBypassChoices.forEach(pvpBypassTag::putBoolean);
        tag.put("pvpBypassChoices", pvpBypassTag);

        CompoundTag limboTag = new CompoundTag();
        limboInventories.forEach(limboTag::put);
        tag.put("limboInventories", limboTag);

        ListTag doorsTag = new ListTag();
        usedLimboDoors.forEach(coord -> doorsTag.add(StringTag.valueOf(coord)));
        tag.put("usedLimboDoors", doorsTag);
        
        CompoundTag deadCuriosTag = new CompoundTag();
        deadCuriosInventories.forEach(deadCuriosTag::put);
        tag.put("deadCuriosInventories", deadCuriosTag);

        return tag;
    }

    public static ModPersistentData load(CompoundTag tag, HolderLookup.Provider registries) {
        ModPersistentData data = new ModPersistentData();

        if (tag.contains("playerChoices")) {
            CompoundTag playersTag = tag.getCompound("playerChoices");
            for (String username : playersTag.getAllKeys()) {
                data.playerChoices.put(username, playersTag.getBoolean(username));
            }
        }

        if (tag.contains("pvpBypassChoices")) {
            CompoundTag pvpBypassTag = tag.getCompound("pvpBypassChoices");
            for (String username : pvpBypassTag.getAllKeys()) {
                data.pvpBypassChoices.put(username, pvpBypassTag.getBoolean(username));
            }
        }

        if (tag.contains("limboInventories")) {
            CompoundTag limboTag = tag.getCompound("limboInventories");
            for (String username : limboTag.getAllKeys()) {
                data.limboInventories.put(username, limboTag.getCompound(username));
            }
        }

        if (tag.contains("usedLimboDoors")) {
            ListTag doorsTag = tag.getList("usedLimboDoors", 8);
            for (int i = 0; i < doorsTag.size(); i++) {
                data.usedLimboDoors.add(doorsTag.getString(i));
            }
        }

        // НОВОЕ: Чтение Curios с диска при запуске сервера
        if (tag.contains("deadCuriosInventories")) {
            CompoundTag deadCuriosTag = tag.getCompound("deadCuriosInventories");
            for (String username : deadCuriosTag.getAllKeys()) {
                data.deadCuriosInventories.put(username, deadCuriosTag.getCompound(username));
            }
        }

        return data;
    }

    public static ModPersistentData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModPersistentData::new, ModPersistentData::load, null),
                "custom_keep_inventory"
        );
    }
}
