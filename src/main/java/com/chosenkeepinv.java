package com;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

@Mod(chosenkeepinv.MODID)
public class chosenkeepinv {
    public static final String MODID = "chosenkeepinv";

    public chosenkeepinv(IEventBus eventBus, ModContainer modContainer)
    {
        NeoForge.EVENT_BUS.register(ModCommands.class);
        NeoForge.EVENT_BUS.register(ModEvents.class);

    }
}
