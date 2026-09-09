package com;

import net.neoforged.neoforge.common.ModConfigSpec;

public class KeepinvConfig {
    public static ModConfigSpec SPEC;

    public static ModConfigSpec.BooleanValue PvpSafeDisable;

    static
    {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Main Settings for the SafetyChoiceMod").push("general");

        PvpSafeDisable=builder
                .comment("Disables PvPSafe Command on server Default: false")
                .define("PvPDisable",false);
        builder.pop();
        SPEC= builder.build();
    }
}
