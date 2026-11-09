package com.hearthvale;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ARRIVAL_INTERVAL = B
            .comment("Ticks between villager arrivals per village (24000 = one in-game day). Lower this while testing.")
            .defineInRange("arrivalIntervalTicks", 6000, 100, 2_400_000);
    public static final ModConfigSpec.IntValue MAX_VOLUME = B
            .comment("Largest room (in open blocks) the Surveyor's Deed will accept.")
            .defineInRange("maxBuildingVolume", 3000, 50, 20000);
    public static final ModConfigSpec.IntValue MAX_RADIUS = B
            .comment("How far (horizontally) from the player a room may extend.")
            .defineInRange("maxBuildingRadius", 24, 4, 64);
    public static final ModConfigSpec.IntValue VILLAGE_RADIUS = B
            .comment("Buildings must be within this many blocks of the Village Hearth.")
            .defineInRange("villageRadius", 96, 16, 512);
    public static final ModConfigSpec.IntValue MIN_VILLAGE_DISTANCE = B
            .comment("Minimum distance between two Village Hearths.")
            .defineInRange("minVillageDistance", 64, 16, 1024);

    public static final ModConfigSpec.BooleanValue AUTO_EVENTS = B
            .comment("Heart events and weddings start by themselves when you walk up to a villager. If false, they only start when you talk to them.")
            .define("autoHeartEvents", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
