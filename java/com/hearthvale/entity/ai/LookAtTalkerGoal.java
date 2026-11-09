package com.hearthvale.entity.ai;

import com.hearthvale.entity.NpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** While in a conversation the NPC stops walking and faces the player. */
public class LookAtTalkerGoal extends Goal {
    private final NpcEntity npc;

    public LookAtTalkerGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return npc.isTalking(); }

    @Override
    public void start() { npc.getNavigation().stop(); }

    @Override
    public void tick() {
        Player p = npc.level().getPlayerByUUID(npc.getTalkingTo());
        if (p != null) npc.getLookControl().setLookAt(p, 30.0F, 30.0F);
    }
}
