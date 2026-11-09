package com.hearthvale.item;

import com.hearthvale.world.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Use while standing inside a finished room to detect and register the building. */
public class SurveyorDeedItem extends Item {
    public SurveyorDeedItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            player.getCooldowns().addCooldown(this, 20);
            VillageManager.survey(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
