package com.hearthvale;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Hearthvale.MODID)
public class Hearthvale {
    public static final String MODID = "hearthvale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Hearthvale(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "hearthvale-client.toml");
    }
}
