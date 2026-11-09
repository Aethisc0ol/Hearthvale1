package com.hearthvale.client.book;

import com.hearthvale.Hearthvale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Fonts, text colours and texture names for the book. */
public final class BookStyle {
    private BookStyle() {}

    /** Game Paused (letters) + a companion pixel font for digits and punctuation (see font/fantasy.json). */
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "fantasy");
    public static final Style STYLE = Style.EMPTY.withFont(FONT);

    public static Component text(String s) { return Component.literal(s).withStyle(STYLE); }

    // text colours (all from the reference palette)
    public static final int INK = Pix.BROWN_D;      // main text
    public static final int SOFT = Pix.BROWN;       // secondary text
    public static final int FAINT = 0xFFB99478;     // hints

    static ResourceLocation tex(String n) { return Pix.tex(n); }

    static final ResourceLocation BOOK = tex("book");
    static final ResourceLocation FLAG_HOME = tex("flag_home");
    static final ResourceLocation FLAG_SETTINGS = tex("flag_settings");
    static final ResourceLocation FLAG_CLOSE = tex("flag_close");
    static final ResourceLocation TREE = tex("tree_128");
}
