package com;


import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.world.level.Level;

@Mod(chosenkeepinv.MODID)
public class chosenkeepinv {
    public static final String MODID = "chosenkeepinv";

    public chosenkeepinv(IEventBus eventBus, ModContainer modContainer)
    {
        NeoForge.EVENT_BUS.register(ModCommands.class);
        NeoForge.EVENT_BUS.register(ModEvents.class);
        NeoForge.EVENT_BUS.register(voidHandler.class);
        ResourceKey<Level> limboKeyCheck = voidHandler.LIMBO_KEY;


    }
}
