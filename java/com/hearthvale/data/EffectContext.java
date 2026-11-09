package com.hearthvale.data;

import com.hearthvale.entity.NpcEntity;
import com.hearthvale.world.PlayerData;
import com.hearthvale.world.Relationship;
import com.hearthvale.world.WorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record EffectContext(ServerPlayer player, NpcEntity npc, NpcDefinition def) {
    public ServerLevel level() { return player.serverLevel(); }
    public PlayerData data() { return WorldState.get(level()).player(player.getUUID()); }
    public Relationship rel() { return data().rel(def.id); }
    public void touch() { WorldState.get(level()).setDirty(); }
}
