package com;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.HashMap;
import java.util.Map;


public class ModPersistentData extends SavedData {
    private final Map<String, Boolean> playerChoices = new HashMap<>();
    private final Map<String, Boolean> pvpBypassChoices = new HashMap<>(); // New map for PvP preference

    public boolean getChoice(String username) {
        return playerChoices.getOrDefault(username, false);
    }

    public void setChoice(String username, boolean value) {
        playerChoices.put(username, value);
        this.setDirty();
    }

    // New getter for PvP bypass choice
    public boolean getPvpBypass(String username) {
        return pvpBypassChoices.getOrDefault(username, false);
    }

    // New setter for PvP bypass choice
    public void setPvpBypass(String username, boolean value) {
        pvpBypassChoices.put(username, value);
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        playerChoices.forEach(playersTag::putBoolean);
        tag.put("playerChoices", playersTag);

        CompoundTag pvpBypassTag = new CompoundTag();
        pvpBypassChoices.forEach(pvpBypassTag::putBoolean);
        tag.put("pvpBypassChoices", pvpBypassTag);

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

        return data;
    }

    public static ModPersistentData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModPersistentData::new, ModPersistentData::load, null),
                "custom_keep_inventory"
        );
    }
}
