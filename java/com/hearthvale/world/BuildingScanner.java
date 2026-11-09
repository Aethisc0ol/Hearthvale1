package com.hearthvale.world;

import com.hearthvale.Config;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.*;

/**
 * Flood-fills the open space around a starting position. If the fill escapes (no roof, open wall, too big)
 * the room is rejected; otherwise every block inside the room or touching it is collected as a "furnishing"
 * so building types can look for beds, anvils, bookshelves, etc.
 */
public final class BuildingScanner {
    private BuildingScanner() {}

    public record Scan(String error, List<BlockPos> interior, List<BlockState> furnishings,
                       BlockPos min, BlockPos max, BlockPos seed, List<BlockPos> spots) {
        public boolean ok() { return error == null; }
    }

    private static Scan fail(String msg) {
        return new Scan(msg, List.of(), List.of(), null, null, null, List.of());
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.getCollisionShape(level, p).isEmpty() && st.getFluidState().isEmpty();
    }

    public static Scan scan(ServerLevel level, BlockPos start) {
        int maxVolume = Config.MAX_VOLUME.get();
        int radius = Config.MAX_RADIUS.get();

        BlockPos s = start;
        if (!level.hasChunkAt(s)) return fail("That area isn't loaded.");
        if (!passable(level, s)) s = s.above();
        if (!passable(level, s)) return fail("Stand inside the room, in open space, when using the deed.");

        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> interior = new ArrayList<>();
        queue.add(s);
        seen.add(s.asLong());

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            interior.add(p);
            if (interior.size() > maxVolume) return fail("This space is too large or isn't sealed (more than " + maxVolume + " open blocks).");
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n.asLong())) continue;
                if (!level.hasChunkAt(n)) return fail("Part of this room is in an unloaded chunk.");
                if (!passable(level, n)) continue;
                boolean outside = Math.abs(n.getX() - s.getX()) > radius || Math.abs(n.getZ() - s.getZ()) > radius
                        || n.getY() - s.getY() > 14 || s.getY() - n.getY() > 6;
                if (outside) return fail("The room isn't enclosed - there's an opening near " + n.getX() + ", " + n.getY() + ", " + n.getZ() + ". Check for gaps in the walls or roof.");
                seen.add(n.asLong());
                queue.add(n);
            }
        }
        if (interior.size() < 6) return fail("This room is too small to be a building.");

        // Collect furnishings: interior cells + whatever touches them
        Set<BlockPos> candidates = new HashSet<>(interior);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : interior) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n.asLong())) candidates.add(n);
            }
        }
        List<BlockState> furnishings = new ArrayList<>();
        for (BlockPos p : candidates) {
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;
            // multi-block furniture should only count once
            if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
            furnishings.add(st);
        }

        // Standing spots: floor cells with headroom
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos p : interior) {
            BlockPos below = p.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && passable(level, p.above())) floor.add(p);
        }
        floor.sort(Comparator.comparingLong(BlockPos::asLong));
        List<BlockPos> spots = new ArrayList<>();
        int want = Math.min(12, floor.size());
        for (int i = 0; i < want; i++) spots.add(floor.get(i * floor.size() / want));
        if (spots.isEmpty()) spots.add(s);

        return new Scan(null, interior, furnishings, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), s, spots);
    }
}
