package vg.civcraft.mc.citadel.listener;

import java.util.Arrays;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.material.Comparator;
import org.bukkit.material.Openable;

import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.ReinforcementLogic;
import vg.civcraft.mc.citadel.Utility;
import vg.civcraft.mc.citadel.model.Reinforcement;

public class BlockListener implements Listener {

	public static final List<BlockFace> all_sides = Arrays.asList(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
			BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

	public static final List<BlockFace> planar_sides = Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST,
			BlockFace.EAST);

	private static final Material matfire = Material.FIRE;

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void blockBreakEvent(BlockBreakEvent event) {
		Citadel.getInstance().getStateManager().getState(event.getPlayer()).handleBreakBlock(event);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void blockBurn(BlockBurnEvent bbe) {
		Reinforcement reinforcement = ReinforcementLogic.getReinforcementProtecting(bbe.getBlock());
		if (reinforcement == null) {
			return;
		}
		bbe.setCancelled(true);
		Block block = bbe.getBlock();
		// Basic essential fire protection
		if (block.getRelative(0, 1, 0).getType() == matfire) {
			block.getRelative(0, 1, 0).setType(Material.AIR);
		} // Essential
			// Extended fire protection (recommend)
		if (block.getRelative(1, 0, 0).getType() == matfire) {
			block.getRelative(1, 0, 0).setType(Material.AIR);
		}
		if (block.getRelative(-1, 0, 0).getType() == matfire) {
			block.getRelative(-1, 0, 0).setType(Material.AIR);
		}
		if (block.getRelative(0, -1, 0).getType() == matfire) {
			block.getRelative(0, -1, 0).setType(Material.AIR);
		}
		if (block.getRelative(0, 0, 1).getType() == matfire) {
			block.getRelative(0, 0, 1).setType(Material.AIR);
		}
		if (block.getRelative(0, 0, -1).getType() == matfire) {
			block.getRelative(0, 0, -1).setType(Material.AIR);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void blockPhysEvent(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		if (block.getType().hasGravity()) {
			Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(block);
			if (rein != null) {
				event.setCancelled(true);
			}
		}

	}

	// Stop comparators from being placed unless the reinforcement is insecure
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void comparatorPlaceCheck(BlockPlaceEvent event) {
		// We only care if they are placing a comparator
		if (event.getBlockPlaced().getType() != Material.REDSTONE_COMPARATOR_OFF) {
			return;
		}
		Comparator comparator = (Comparator) event.getBlockPlaced().getState().getData();
		Block block = event.getBlockPlaced().getRelative(comparator.getFacing().getOppositeFace());
		// Check if the comparator is placed against something with an inventory
		if (ReinforcementLogic.isPreventingBlockAccess(event.getPlayer(), block)) {
			event.setCancelled(true);
			Utility.sendAndLog(event.getPlayer(), ChatColor.RED,
					"You can not place this because it'd allow bypassing a nearby reinforcement");
			return;
		}
		// Comparators can also read through a single opaque block
		if (block.getType().isOccluding()) {
			if (ReinforcementLogic.isPreventingBlockAccess(event.getPlayer(),
					block.getRelative(comparator.getFacing().getOppositeFace()))) {
				event.setCancelled(true);
				Utility.sendAndLog(event.getPlayer(), ChatColor.RED,
						"You can not place this because it'd allow bypassing a nearby reinforcement");
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void interact(PlayerInteractEvent pie) {
		if (!pie.hasBlock()) {
			return;
		}
		Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(pie.getClickedBlock());
		if (rein == null) {
			return;
		}
	}

	// prevent placing water inside of reinforced blocks
	@EventHandler(priority = EventPriority.HIGHEST)
	public void liquidDumpEvent(PlayerBucketEmptyEvent event) {
		Block block = event.getBlockClicked().getRelative(event.getBlockFace());
		if (block.getType().equals(Material.AIR) || block.getType().isSolid()) {
			return;
		}
		Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(block);
		if (rein != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onBlockFromToEvent(BlockFromToEvent event) {
		// prevent water/lava from spilling reinforced blocks away
		Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(event.getToBlock());
		if (rein != null) {
			event.setCancelled(true);
		}
	}

	// prevent breaking reinforced blocks through plant growth
	@EventHandler(ignoreCancelled = true)
	public void onStructureGrow(StructureGrowEvent event) {
		for (BlockState block_state : event.getBlocks()) {
			if (ReinforcementLogic.getReinforcementProtecting(block_state.getBlock()) != null) {
				event.setCancelled(true);
				return;
			}
		}
	}

	// prevent opening reinforced things
	@EventHandler(priority = EventPriority.LOWEST)
	public void openContainer(PlayerInteractEvent e) {
		if (!e.hasBlock()) {
			return;
		}
		Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(e.getClickedBlock());
		if (rein == null) {
			return;
		}
		if (e.getClickedBlock().getState() instanceof Container) {
			if (!rein.hasPermission(e.getPlayer(), Citadel.chestPerm)) {
				e.setCancelled(true);
				Utility.sendAndLog(e.getPlayer(), ChatColor.RED,
						e.getClickedBlock().getType().name() + " is locked with " + rein.getType().getName());
			}
			return;
		}
		if (e.getClickedBlock().getState().getData() instanceof Openable) {
			if (!rein.hasPermission(e.getPlayer(), Citadel.doorPerm)) {
				e.setCancelled(true);
				Utility.sendAndLog(e.getPlayer(), ChatColor.RED,
						e.getClickedBlock().getType().name() + " is locked with " + rein.getType().getName());
			}
		}
	}

	// prevent players from upgrading a chest into a double chest to bypass the
	// single chests reinforcement
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void preventBypassChestAccess(BlockPlaceEvent e) {
		Material mat = e.getBlock().getType();
		if (mat != Material.CHEST && mat != Material.TRAPPED_CHEST) {
			return;
		}
		for (BlockFace face : planar_sides) {
			Block rel = e.getBlock().getRelative(face);
			if (rel != null && rel.getType() == mat) {
				if (ReinforcementLogic.isPreventingBlockAccess(e.getPlayer(), rel)) {
					e.setCancelled(true);
					Utility.sendAndLog(e.getPlayer(), ChatColor.RED,
							"You can not place this because it'd allow bypassing a nearby reinforcement");
					break;
				}
			}
		}
	}

	// remove reinforced air
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void removeReinforcedAir(BlockPlaceEvent e) {
		if (e.getBlockReplacedState().getType() != Material.AIR) {
			return;
		}
		Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(e.getBlock());
		rein.setHealth(-1);
	}
}