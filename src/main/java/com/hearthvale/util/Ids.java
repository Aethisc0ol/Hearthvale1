package com.hearthvale.util;

import com.hearthvale.Hearthvale;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Helpers for "namespace:path" ids. Ids without a namespace default to "hearthvale". */
public final class Ids {
    private Ids() {}

    public static String norm(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        return s.indexOf(':') >= 0 ? s : Hearthvale.MODID + ":" + s;
    }

    public static String ns(String id) {
        int i = id.indexOf(':');
        return i < 0 ? Hearthvale.MODID : id.substring(0, i);
    }

    public static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.parse(norm(id));
    }
}
