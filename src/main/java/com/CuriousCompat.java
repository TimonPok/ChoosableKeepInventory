package com;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public class CuriousCompat {
    public static CompoundTag saveCuriosInventory(ServerPlayer player) {
        CompoundTag wrapperTag = new CompoundTag();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            ListTag curiosList = handler.saveInventory(false);
            if (curiosList != null) {
                wrapperTag.put("CuriosList", curiosList);
            }
        });
        return wrapperTag;
    }

    public static void loadCuriosInventory(ServerPlayer player, CompoundTag wrapperTag) {
        if (wrapperTag != null && wrapperTag.contains("CuriosList")) {
            ListTag curiosList = wrapperTag.getList("CuriosList", 10);

            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                // Загружаем предметы в память сервера
                handler.loadInventory(curiosList);
            });

            // Синхронизируем меню, когда игрок уже полностью в мире
            if (player.inventoryMenu != null) {
                player.inventoryMenu.broadcastFullState();
                player.inventoryMenu.broadcastChanges();
            }
        }
    }
}
