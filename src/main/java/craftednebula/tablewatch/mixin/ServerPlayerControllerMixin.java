package craftednebula.tablewatch.mixin;

import craftednebula.tablewatch.BlockLogEntry;
import craftednebula.tablewatch.TableWatch;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.server.world.ServerPlayerController;
import net.minecraft.server.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerController.class)
public class ServerPlayerControllerMixin {

	@Shadow
	private WorldServer thisWorld;

	@Shadow
	public Player player;

	@Unique private int tablewatch$oldId = 0;
	@Unique private int tablewatch$oldMeta = 0;
	@Unique private int tablewatch$placedX = 0;
	@Unique private int tablewatch$placedY = 0;
	@Unique private int tablewatch$placedZ = 0;

	/**
	 * BLOCK BREAKING
	 * Hook into mineBlock at HEAD to capture block data before it turns to air.
	 */
	@Inject(method = "mineBlock", at = @At("HEAD"))
	private void tablewatch$onMineBlock(int x, int y, int z, Side side, CallbackInfoReturnable<Boolean> cir) {
		if (this.thisWorld == null || this.player == null) return;

		int oldBlockId = this.thisWorld.getBlockId(x, y, z);
		int oldBlockMeta = this.thisWorld.getBlockMetadata(x, y, z);

		// Don't log breaking air
		if (oldBlockId == 0) return;

		TableWatch.logBlockChange(
			this.player.username,
			BlockLogEntry.Action.BREAK,
			"world",
			x, y, z,
			0, 0,                      // new state: AIR (0)
			oldBlockId, oldBlockMeta   // old state: what was broken
		);
	}

	/**
	 * BLOCK PLACING - STEP 1 (HEAD)
	 * Capture whatever block was at the destination before placement (e.g. Air, Tall Grass, Snow).
	 */
	@Inject(method = "useOrPlaceItemStackOnTile", at = @At("HEAD"))
	private void tablewatch$captureOldPlaceState(
		Player player,
		World world,
		ItemStack itemstack,
		int blockX, int blockY, int blockZ,
		Side side,
		double xPlaced, double yPlaced,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (world == null) return;

		this.tablewatch$placedX = blockX;
		this.tablewatch$placedY = blockY;
		this.tablewatch$placedZ = blockZ;

		if (side != null) {
			switch (side) {
				case BOTTOM: this.tablewatch$placedY--; break;
				case TOP:    this.tablewatch$placedY++; break;
				case NORTH:  this.tablewatch$placedZ--; break;
				case SOUTH:  this.tablewatch$placedZ++; break;
				case WEST:   this.tablewatch$placedX--; break;
				case EAST:   this.tablewatch$placedX++; break;
			}
		}

		this.tablewatch$oldId = world.getBlockId(this.tablewatch$placedX, this.tablewatch$placedY, this.tablewatch$placedZ);
		this.tablewatch$oldMeta = world.getBlockMetadata(this.tablewatch$placedX, this.tablewatch$placedY, this.tablewatch$placedZ);
	}

	/**
	 * BLOCK PLACING - STEP 2 (RETURN)
	 * Hook into placeItemStackOnTile at RETURN so we only log when placement succeeds.
	 */
	@Inject(method = "useOrPlaceItemStackOnTile", at = @At("RETURN"))
	private void tablewatch$onPlaceBlock(
		Player player,
		World world,
		ItemStack itemstack,
		int blockX, int blockY, int blockZ,
		Side side,
		double xPlaced, double yPlaced,
		CallbackInfoReturnable<Boolean> cir
	) {

		if (!cir.getReturnValue() || world == null || player == null) return;

		int newBlockId = world.getBlockId(this.tablewatch$placedX, this.tablewatch$placedY, this.tablewatch$placedZ);
		int newBlockMeta = world.getBlockMetadata(this.tablewatch$placedX, this.tablewatch$placedY, this.tablewatch$placedZ);

		if (newBlockId == 0 || newBlockId == this.tablewatch$oldId) return;

		TableWatch.logBlockChange(
			player.username,
			BlockLogEntry.Action.PLACE,
			"world",
			this.tablewatch$placedX, this.tablewatch$placedY, this.tablewatch$placedZ,
			newBlockId, newBlockMeta,                  // new state: placed block
			this.tablewatch$oldId, this.tablewatch$oldMeta // old state: replaced block
		);
	}

	/**
	 * BLOCK INTERACTION
	 * Capture right-clicks on interactive blocks (chests, doors, buttons, levers, etc.).
	 */
	@Inject(method = "useOrPlaceItemStackOnTile", at = @At("HEAD"))
	private void tablewatch$onInteract(
		Player player,
		World world,
		ItemStack itemstack,
		int x, int y, int z,
		Side side,
		double xPlaced, double yPlaced,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (world == null || player == null) return;

		int targetId = world.getBlockId(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);

		if (tablewatch$isInteractiveBlock(targetId)) {
			TableWatch.logBlockChange(
				player.username,
				BlockLogEntry.Action.INTERACT,
				"world",
				x, y, z,
				targetId, targetMeta, // new state
				targetId, targetMeta  // old state
			);
		}
	}

	@Unique
	private boolean tablewatch$isInteractiveBlock(int id) {
		Block<?> block = Blocks.blocksList[id];
		if (block == null) return false;

		return block == Blocks.CHEST_LEGACY ||
			block == Blocks.CHEST_LEGACY_PAINTED ||
			block == Blocks.CHEST_PLANKS_OAK_PAINTED ||
			block == Blocks.CHEST_PLANKS_OAK ||
			block == Blocks.TNT;
	}
}
