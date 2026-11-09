package com.hearthvale.util;

import com.hearthvale.Hearthvale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** The mod's pixel font (Game Paused + companion digits), used for NPC name tags and mod messages. */
public final class Fonts {
    public static final Style STYLE = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "fantasy"));

    private Fonts() {}

    public static MutableComponent text(String s) { return Component.literal(s).withStyle(STYLE); }
}
