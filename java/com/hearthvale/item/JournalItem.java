package com.hearthvale.item;

import com.hearthvale.book.BookDataBuilder;
import com.hearthvale.network.BookDataPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Opens the Hearthvale book (the J key does the same without the item). */
public class JournalItem extends Item {
    public JournalItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new BookDataPayload(true, BookDataBuilder.build(sp)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
