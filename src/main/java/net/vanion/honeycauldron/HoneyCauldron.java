package net.vanion.honeycauldron;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CauldronBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HoneyCauldron implements ModInitializer {
	private static final String MOD_ID = "honeycauldron";
	private static final IntProperty HONEY_LEVEL = IntProperty.of("honey_level", 0, 3);

	private static final Block HONEY_CAULDRON = new CauldronBlock(FabricBlockSettings.copyOf(Blocks.WATER_CAULDRON)) {
		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(HONEY_LEVEL);
		}
	};

	@Override
	public void onInitialize() {
		// Register the HONEY_CAULDRON block
		Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "honey_cauldron"), HONEY_CAULDRON);

		// Register a server tick event to handle cauldron honey collection
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerWorld world : server.getWorlds()) {
				world.getPlayers().forEach(player -> {
					BlockPos playerPos = player.getBlockPos();
					int radius = 32; // Check in a 32-block radius around players

					BlockPos.iterate(playerPos.add(-radius, -radius, -radius),
							playerPos.add(radius, radius, radius)).forEach(pos -> {
						if (isCauldron(world.getBlockState(pos))) {
							checkAndCollectHoney(world, pos);
						}
					});
				});
			}
		});
	}

	private boolean isCauldron(BlockState state) {
		return state.getBlock() == Blocks.CAULDRON || state.getBlock() == Blocks.WATER_CAULDRON || state.getBlock() == Blocks.LAVA_CAULDRON;
	}

	private void checkAndCollectHoney(World world, BlockPos cauldronPos) {
		for (int i = 1; i <= 3; i++) {
			BlockPos aboveCauldron = cauldronPos.up(i);
			BlockState aboveBlockState = world.getBlockState(aboveCauldron);

			if (aboveBlockState.getBlock() == Blocks.BEEHIVE || aboveBlockState.getBlock() == Blocks.BEE_NEST) {
				System.out.println("Beehive or nest found at: " + aboveCauldron);

				// Gradually fill the cauldron with honey
				BlockState cauldronState = world.getBlockState(cauldronPos);
				if (cauldronState.getBlock() == HONEY_CAULDRON) {
					int currentLevel = cauldronState.get(HONEY_LEVEL);
					if (currentLevel < 3) {
						world.setBlockState(cauldronPos, cauldronState.with(HONEY_LEVEL, currentLevel + 1));
						System.out.println("Honey cauldron at " + cauldronPos + " filled to level " + (currentLevel + 1));
					}
				} else if (cauldronState.getBlock() == Blocks.CAULDRON) {
					// Replace empty cauldron with honey cauldron
					world.setBlockState(cauldronPos, HONEY_CAULDRON.getDefaultState().with(HONEY_LEVEL, 1));
					System.out.println("Cauldron at " + cauldronPos + " converted to honey cauldron with level 0");
				}
				return; // Exit once a valid hive/nest is found
			}
		}

		System.out.println("No beehive or nest found within range of cauldron at: " + cauldronPos);
	}
}
