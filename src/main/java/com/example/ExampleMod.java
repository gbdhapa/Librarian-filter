package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ExampleMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ExampleMod.class);
    private static Set<Block> REPLACEABLE_BLOCKS = new HashSet<>(Set.of(
            Blocks.DIRT_PATH,
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,
            Blocks.COARSE_DIRT,
            Blocks.PODZOL,
            Blocks.ROOTED_DIRT,
            Blocks.SAND,
            Blocks.GRAVEL,
            Blocks.STONE,
            Blocks.SNOW_BLOCK,
            Blocks.PACKED_ICE,
            Blocks.POWDER_SNOW
    ));


    @Override
    public void onInitialize() {
        LOGGER.info("Mod initialized!");
        register();
    }


    private void register() {
        registerCommands();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("hmm")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                createVillagePath(player);
                                return 1;
                            })
            );
        });


        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hmmAdd")
                    .then(argument("blockId", BlockStateArgumentType.blockState(registryAccess))
                            .executes(ctx -> {
                                Block block = BlockStateArgumentType.getBlockState(ctx, "blockId").getBlockState().getBlock();
                                if (block != Blocks.AIR) {
                                    REPLACEABLE_BLOCKS.add(block);
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("Added block " + block.getTranslationKey() + " in replaceable list."), true);

                                } else {
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("Cannot add block " + block.getTranslationKey() + " in replaceable list."), true);
                                }
                                return 1;
                            })));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hmmRemove")
                    .then(argument("blockId", BlockStateArgumentType.blockState(registryAccess))
                            .executes(ctx -> {
                                Block block = BlockStateArgumentType.getBlockState(ctx, "blockId").getBlockState().getBlock();
                                REPLACEABLE_BLOCKS.remove(block);
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal("Removed block " + block.getTranslationKey() + " from replaceable list."), true);
                                return 1;
                            })));
        });
    }

    private void createVillagePath(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos villagePos = findNearestPlainsVillage(world, player.getBlockPos());
        if (villagePos != null) {
            createPathTo(world, player.getBlockPos(), villagePos);
            player.getWorld().setBlockState(villagePos.up(), Blocks.OAK_SIGN.getDefaultState());
            player.sendMessage(Text.of("Path to village created!"), false);
        } else {
            player.sendMessage(Text.of("No village found nearby."), false);
        }
        createPathToAnotherVillager(world, villagePos);
    }

    private void createPathToAnotherVillager(ServerWorld world, BlockPos villagePos) {
        BlockPos secondVillage = find2ndNearestPlainsVillage(world, villagePos);
        createPathTo(world, villagePos, secondVillage);
    }


    private BlockPos find2ndNearestPlainsVillage(ServerWorld world, BlockPos origin) {
        BlockPos blockPos = world.locateStructure(
                StructureTags.VILLAGE,
                origin.offset(Direction.SOUTH, 500),
                1000,
                false// skip unexplored chunks?
        );
        if (blockPos != null && blockPos.getSquaredDistance(origin) > 300) {
            return blockPos;
        }
        return find2ndNearestPlainsVillage(world, blockPos.offset(Direction.NORTH, 500));
    }

    private BlockPos findNearestPlainsVillage(ServerWorld world, BlockPos origin) {
        return world.locateStructure(
                StructureTags.VILLAGE,
                origin,
                1000,
                false// skip unexplored chunks?
        );
    }

    // 3. Create simple path (straight line) by placing blocks
    private void placePathBlock(World world, BlockPos pos) {
        world.setBlockState(pos, Blocks.DIRT_PATH.getDefaultState());
    }

    private void createPathTo(World world, BlockPos start, BlockPos end) {
        int length = (int) start.getSquaredDistance(end);
        if (length == 0) return;

        // Direction vector (normalized on XZ)
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double nx = dx / dist;
        double nz = dz / dist;

        // Perpendicular vector on XZ (left/right)
        double px = -nz;
        double pz = nx;

        for (int i = 0; i <= length; i++) {
            double t = (double) i / length;
            int x = (int) Math.round(lerp(start.getX(), end.getX(), t));
            int y = start.getY(); // or find ground height for each x,z if you want
            int z = (int) Math.round(lerp(start.getZ(), end.getZ(), t));

            BlockPos center = new BlockPos(x, y, z);
            BlockPos topSolidBlock = findTopSolidBlock(world, center);
            // center path block
            placePathBlock(world, topSolidBlock);
            // left side
            placePathBlock(world, topSolidBlock.add((int) Math.round(px), 0, (int) Math.round(pz)));
            // right side
            placePathBlock(world, topSolidBlock.add((int) Math.round(-px), 0, (int) Math.round(-pz)));
        }
    }

    // Helper lerp method
    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private BlockPos findTopSolidBlock(World world, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos); // start from top of the world

        for (; y > world.getBottomY(); y--) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(checkPos);

            if (REPLACEABLE_BLOCKS.contains(state.getBlock())) {
                return checkPos;
            }
        }
        return pos; // fallback
    }

}
