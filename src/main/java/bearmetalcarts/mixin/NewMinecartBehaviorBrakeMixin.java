package bearmetalcarts.mixin;

import bearmetalcarts.FurnaceEngineMinecart;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps vanilla's abrupt brake-rail halt for the distance-based curve in {@link FurnaceEngineMinecart}, for
 * furnace carts only — every other cart keeps vanilla's 0.5-per-tick stop.
 *
 * <p>This only mixes into {@link NewMinecartBehavior}, which vanilla installs solely when the
 * {@code minecart_improvements} experiment is on ({@code AbstractMinecart.useExperimentalMovement}). That is the
 * same gate the mod's speed overrides sit behind, so braking distance and max speed are always governed by the
 * same physics.
 */
@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorBrakeMixin extends MinecartBehavior {

	// Extends the target's superclass purely to reach its protected `minecart` field: @Shadow only resolves
	// members declared on the target class itself, and `minecart` is declared one level up on MinecartBehavior.
	protected NewMinecartBehaviorBrakeMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "calculateHaltTrackSpeed", at = @At("HEAD"), cancellable = true)
	private void bearmetalcarts$brakeFurnaceGently(
			final Vec3 deltaMovement, final BlockState state, final CallbackInfoReturnable<Vec3> cir) {
		if (!(this.minecart instanceof FurnaceEngineMinecart furnace)) {
			return;
		}

		if (!state.is(Blocks.POWERED_RAIL) || state.getValue(PoweredRailBlock.POWERED)) {
			return;
		}

		// Tells MinecartFurnaceEngineMixin to hold the engine off for the rest of this tick; without that, the
		// push added in applyNaturalSlowdown (which vanilla runs right after this) cancels out the braking.
		furnace.bearmetalcarts$markBraking();
		cir.setReturnValue(FurnaceEngineMinecart.brake(deltaMovement));
	}
}
