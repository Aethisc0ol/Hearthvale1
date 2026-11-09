package com.hearthvale.entity;

import com.hearthvale.Config;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.dialogue.DialogueManager;
import com.hearthvale.dialogue.NpcInteractions;
import com.hearthvale.cutscene.CutsceneManager;
import com.hearthvale.entity.ai.CutsceneHoldGoal;
import com.hearthvale.entity.ai.LookAtTalkerGoal;
import com.hearthvale.entity.ai.NpcScheduleGoal;
import com.hearthvale.util.Fonts;
import com.hearthvale.world.Resident;
import com.hearthvale.world.Village;
import com.hearthvale.world.VillageManager;
import com.hearthvale.world.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Every Hearthvale villager is an instance of this one entity. Which character it is comes from its npc id,
 * which points at a JSON definition - so addons never need to ship Java.
 */
public class NpcEntity extends PathfinderMob {
    public static final int STATE_ARRIVING = 0;   // walking in from the road
    public static final int STATE_WAITING = 1;    // standing by the hearth, waiting to be welcomed
    public static final int STATE_RESIDENT = 2;   // settled; follows its schedule

    private static final EntityDataAccessor<String> NPC_ID = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID villageId;
    private UUID talkingTo;
    public int approachCooldown = 200;
    /** true while a cutscene is scripting this NPC */
    private boolean cutsceneControlled;
    /** cutscene extra: a look-alike actor that exists only for the duration of a scene */
    private boolean temp;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NPC_ID, "");
        builder.define(STATE, STATE_ARRIVING);
        builder.define(SLIM, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CutsceneHoldGoal(this));
        goalSelector.addGoal(1, new LookAtTalkerGoal(this));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new NpcScheduleGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ data accessors

    public String getNpcId() { return entityData.get(NPC_ID); }
    public int getNpcState() { return entityData.get(STATE); }
    public void setNpcState(int s) { entityData.set(STATE, s); }
    public boolean isSlim() { return entityData.get(SLIM); }
    public UUID getVillageId() { return villageId; }
    public void setVillageId(UUID id) { villageId = id; }

    public void setNpcId(String id) {
        entityData.set(NPC_ID, id);
        NpcDefinition d = NpcRegistry.get(id);
        entityData.set(SLIM, d != null && d.slim);
        refreshName();
    }

    public NpcDefinition def() { return NpcRegistry.get(getNpcId()); }

    public Village village() {
        if (villageId == null || !(level() instanceof ServerLevel sl)) return null;
        return WorldState.get(sl).village(villageId);
    }

    // ------------------------------------------------------------ cutscene support

    public boolean isCutsceneControlled() { return cutsceneControlled; }
    public void setCutsceneControlled(boolean b) { cutsceneControlled = b; }
    public boolean isTemp() { return temp; }

    /** Turns this entity into a temporary stand-in using the skin of `skinId` (any npc id / skin path) and a display name. */
    public void setTemp(String skinId, String displayName) {
        temp = true;
        entityData.set(NPC_ID, skinId);
        NpcDefinition d = NpcRegistry.get(skinId);
        entityData.set(SLIM, d != null && d.slim);
        setCustomName(Fonts.text(displayName));
        setCustomNameVisible(true);
    }

    @Override
    public boolean shouldBeSaved() { return !temp && super.shouldBeSaved(); }

    private void refreshName() {
        if (temp) return;
        NpcDefinition d = def();
        if (d == null) return;
        if (!hasCustomName() || !d.name.equals(getCustomName().getString())) setCustomName(Fonts.text(d.name));
        setCustomNameVisible(true);
    }

    // ------------------------------------------------------------ talking

    public boolean isTalking() { return talkingTo != null; }
    public UUID getTalkingTo() { return talkingTo; }
    public void setTalkingTo(UUID id) { talkingTo = id; }

    public void endTalk() {
        talkingTo = null;
        approachCooldown = 600;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || temp) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            NpcInteractions.interact(sp, this, player.getItemInHand(hand), player.isShiftKeyDown());
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (temp) {
            // stand-ins vanish if no cutscene owns them (e.g. after a crash)
            if (tickCount > 100 && !CutsceneManager.isActor(this)) discard();
            return;
        }
        if (approachCooldown > 0) approachCooldown--;
        if (talkingTo != null && !DialogueManager.isActive(talkingTo, this)) endTalk();
        if (tickCount % 100 == 0) refreshName();

        if (tickCount % 20 == 0 && getNpcState() == STATE_ARRIVING) {
            Village v = village();
            if (v != null && position().distanceToSqr(Vec3.atCenterOf(v.hearth)) < 64) setNpcState(STATE_WAITING);
        }

        // Orphans (village dissolved while unloaded, /summon'ed, duplicates) leave quietly.
        if (tickCount % 200 == 100) {
            Village v = village();
            Resident r = v == null ? null : v.residents.get(getNpcId());
            if (v == null || r == null || !r.entity.equals(getUUID())) {
                discard();
                return;
            }
        }

        if (tickCount % 20 == 7 && getNpcState() == STATE_RESIDENT && !isTalking() && !cutsceneControlled && approachCooldown <= 0 && Config.AUTO_EVENTS.get()) {
            NpcInteractions.checkApproach(this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) VillageManager.onNpcRemoved(this);
        super.remove(reason);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcId", getNpcId());
        tag.putInt("NpcState", getNpcState());
        if (villageId != null) tag.putUUID("VillageId", villageId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(NPC_ID, tag.getString("NpcId"));
        entityData.set(STATE, tag.getInt("NpcState"));
        if (tag.hasUUID("VillageId")) villageId = tag.getUUID("VillageId");
        NpcDefinition d = def();
        entityData.set(SLIM, d != null && d.slim);
    }
}
