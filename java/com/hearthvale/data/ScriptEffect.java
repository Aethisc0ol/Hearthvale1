package com.hearthvale.data;

/** Something that happens when a dialogue line is shown or a choice is picked. */
@FunctionalInterface
public interface ScriptEffect {
    void apply(EffectContext ctx);
}
