package com.hearthvale.entity.ai;

import com.hearthvale.entity.NpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** While a cutscene controls this NPC, suppress schedule walking and random head turns (scripted moves use navigation directly). */
public class CutsceneHoldGoal extends Goal {
    private final NpcEntity npc;

    public CutsceneHoldGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return npc.isCutsceneControlled(); }

    @Override
    public boolean canContinueToUse() { return npc.isCutsceneControlled(); }
}
