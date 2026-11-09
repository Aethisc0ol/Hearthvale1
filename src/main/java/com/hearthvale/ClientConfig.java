package com.hearthvale;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side settings (hearthvale-client.toml). Also editable from the book's settings tab. */
public final class ClientConfig {
    /** Size = how many real screen pixels each pixel of book art covers. AUTO picks the biggest that fits. */
    public enum UiSize {
        AUTO(0), SMALL(2), MEDIUM(3), LARGE(4), HUGE(6);

        public final int pixels;
        UiSize(int pixels) { this.pixels = pixels; }
        public UiSize next() { return values()[(ordinal() + 1) % values().length]; }
        public UiSize prev() { return values()[(ordinal() + values().length - 1) % values().length]; }
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(); }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<UiSize> UI_SIZE = B
            .comment("Size of the Hearthvale book. AUTO picks the biggest size that fits your screen and GUI scale.")
            .defineEnum("uiSize", UiSize.AUTO);
    public static final ModConfigSpec.BooleanValue PAGE_TURN = B
            .comment("Animate page turns in the Hearthvale book.")
            .define("pageTurnAnimation", true);

    public static final ModConfigSpec SPEC = B.build();

    private ClientConfig() {}
}
