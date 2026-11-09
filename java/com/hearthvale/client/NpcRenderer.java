package com.hearthvale.client;

import com.hearthvale.entity.NpcEntity;
import com.hearthvale.util.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders every NPC with the vanilla player model. The skin is found by convention:
 * npc id "mypack:zoe" -> assets/mypack/textures/entity/npc/zoe.png (standard 64x64 skin).
 */
public class NpcRenderer extends MobRenderer<NpcEntity, PlayerModel<NpcEntity>> {
    private final PlayerModel<NpcEntity> wide;
    private final PlayerModel<NpcEntity> slim;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    public static ResourceLocation texture(String npcId) {
        if (npcId == null || npcId.isEmpty()) npcId = "hearthvale:missing";
        return ResourceLocation.fromNamespaceAndPath(Ids.ns(npcId), "textures/entity/npc/" + Ids.path(npcId) + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return texture(entity.getNpcId());
    }

    @Override
    public void render(NpcEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light) {
        this.model = entity.isSlim() ? slim : wide;
        super.render(entity, yaw, partialTicks, pose, buffer, light);
    }
}
