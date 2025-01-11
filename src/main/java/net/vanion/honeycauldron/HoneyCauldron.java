package net.vanion.honeycauldron;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HoneyCauldron implements ModInitializer {
	private static final String MOD_ID = "honeycauldron";
	private static final IntProperty HONEY_LEVEL = IntProperty.of("honey_level", 0, 3);
	private static final int TICKS_PER_HONEY_FILL = 600; // 30 seconds (600 ticks)

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
						if (world.getBlockState(pos).getBlock() == HONEY_CAULDRON || isCauldron(world.getBlockState(pos))) {
							checkAndCollectHoney(world, pos);
						}
					});
				});
			}
		});

		// Register a right-click interaction for the honey cauldron
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient) {
				BlockPos pos = hitResult.getBlockPos();
				BlockState state = world.getBlockState(pos);
				ItemStack heldItem = player.getStackInHand(hand);

				if (state.getBlock() == HONEY_CAULDRON) {
					if (heldItem.getItem() == Items.GLASS_BOTTLE) {
						int currentLevel = state.get(HONEY_LEVEL);

						if (currentLevel > 0) {
							// Decrease honey level and give the player a honey bottle
							world.setBlockState(pos, state.with(HONEY_LEVEL, currentLevel - 1));
							if (currentLevel - 1 == 0) {
								// Convert to normal cauldron if level reaches 0
								world.setBlockState(pos, Blocks.CAULDRON.getDefaultState());
								System.out.println("Honey cauldron at " + pos + " converted to normal cauldron as level reached 0");
							}
							if (!player.isCreative()) {
								heldItem.decrement(1); // Consume one glass bottle
								player.giveItemStack(new ItemStack(Items.HONEY_BOTTLE));
							}
							return ActionResult.SUCCESS;
						}
					} else if (heldItem.getItem() == Items.HONEY_BOTTLE) {
						int currentLevel = state.get(HONEY_LEVEL);

						if (currentLevel < 3) {
							// Increase honey level and consume the honey bottle
							world.setBlockState(pos, state.with(HONEY_LEVEL, currentLevel + 1));
							if (!player.isCreative()) {
								heldItem.decrement(1); // Consume one honey bottle
								player.giveItemStack(new ItemStack(Items.GLASS_BOTTLE));
							}
							return ActionResult.SUCCESS;
						}
					}
				} else if (state.getBlock() == Blocks.CAULDRON) {
					if (heldItem.getItem() == Items.HONEY_BOTTLE) {
						// Convert normal cauldron to honey cauldron
						world.setBlockState(pos, HONEY_CAULDRON.getDefaultState().with(HONEY_LEVEL, 1));
						if (!player.isCreative()) {
							heldItem.decrement(1); // Consume one honey bottle
							player.giveItemStack(new ItemStack(Items.GLASS_BOTTLE));
						}
						System.out.println("Cauldron at " + pos + " converted to honey cauldron with level 1");
						return ActionResult.SUCCESS;
					}
				}
			}
			return ActionResult.PASS;
		});
	}

	private boolean isCauldron(BlockState state) {
		return state.getBlock() == Blocks.CAULDRON || state.getBlock() == Blocks.WATER_CAULDRON || state.getBlock() == Blocks.LAVA_CAULDRON;
	}

	private void checkAndCollectHoney(World world, BlockPos cauldronPos) {
		BlockState cauldronState = world.getBlockState(cauldronPos);
		if (cauldronState.getBlock() == HONEY_CAULDRON && cauldronState.get(HONEY_LEVEL) == 3) {
			// Stop checking if the cauldron is full
			return;
		}

		for (int i = 1; i <= 3; i++) {
			BlockPos aboveCauldron = cauldronPos.up(i);
			BlockState aboveBlockState = world.getBlockState(aboveCauldron);

			if (aboveBlockState.getBlock() == Blocks.BEEHIVE || aboveBlockState.getBlock() == Blocks.BEE_NEST) {
				System.out.println("Beehive or nest found at: " + aboveCauldron);

				if (world.getBlockEntity(aboveCauldron) instanceof BeehiveBlockEntity beehive) {
					if (beehive.getCachedState().get(BeehiveBlock.HONEY_LEVEL) >= 5) { // Check if hive is full
						if (cauldronState.getBlock() == HONEY_CAULDRON) {
							int currentLevel = cauldronState.get(HONEY_LEVEL);
							if (currentLevel < 3 && world.getTime() % TICKS_PER_HONEY_FILL == 0) {
								world.setBlockState(cauldronPos, cauldronState.with(HONEY_LEVEL, currentLevel + 1));

								// Decrease honey level in the beehive
								int hiveHoneyLevel = beehive.getCachedState().get(BeehiveBlock.HONEY_LEVEL);
								BlockState updatedHiveState = beehive.getCachedState().with(BeehiveBlock.HONEY_LEVEL, hiveHoneyLevel - 1);
								world.setBlockState(aboveCauldron, updatedHiveState);
								System.out.println("Beehive at " + aboveCauldron + " reduced to honey level " + (hiveHoneyLevel - 1));

								System.out.println("Honey cauldron at " + cauldronPos + " filled to level " + (currentLevel + 1));
								return;
							}
						} else if (cauldronState.getBlock() == Blocks.CAULDRON) {
							if (world.getTime() % TICKS_PER_HONEY_FILL == 0) {
								// Replace empty cauldron with honey cauldron
								world.setBlockState(cauldronPos, HONEY_CAULDRON.getDefaultState().with(HONEY_LEVEL, 1));

								// Decrease honey level in the beehive
								int hiveHoneyLevel = beehive.getCachedState().get(BeehiveBlock.HONEY_LEVEL);
								BlockState updatedHiveState = beehive.getCachedState().with(BeehiveBlock.HONEY_LEVEL, hiveHoneyLevel - 1);
								world.setBlockState(aboveCauldron, updatedHiveState);
								System.out.println("Beehive at " + aboveCauldron + " reduced to honey level " + (hiveHoneyLevel - 1));

								System.out.println("Cauldron at " + cauldronPos + " converted to honey cauldron with level 1");
								return;
							}
						}
					}
				}
			}
		}

		System.out.println("No beehive or nest found within range of cauldron at: " + cauldronPos);
	}
}