package com.hearthvale.block;

import com.hearthvale.Config;
import com.hearthvale.world.Village;
import com.hearthvale.world.VillageManager;
import com.hearthvale.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Placing this founds a village. Right-click for a summary. Breaking it dissolves the village. */
public class VillageHearthBlock extends Block {
    public VillageHearthBlock(Properties props) {
        super(props);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl) {
            ServerPlayer player = placer instanceof ServerPlayer sp ? sp : null;
            String err = VillageManager.found(sl, pos, player);
            if (err != null) {
                if (player != null) player.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.RED));
                sl.destroyBlock(pos, true);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) VillageManager.dissolve(sl, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Village v = WorldState.get(sl).nearest(sl.dimension(), pos, 1);
            if (v != null) VillageManager.describe(sp, v);
            else sp.sendSystemMessage(Component.literal("This hearth isn't linked to a village (too close to another?)."));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
