package com.hearthvale.client.book;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** The "open the book" key (J by default, rebindable in Options > Controls > Hearthvale). */
public final class BookKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.hearthvale.open_book", InputConstants.KEY_J, "key.categories.hearthvale");

    private BookKeys() {}
}
