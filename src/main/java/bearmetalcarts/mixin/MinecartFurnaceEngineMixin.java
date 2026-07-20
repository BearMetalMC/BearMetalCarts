package bearmetalcarts.mixin;

import bearmetalcarts.BearMetalCartsData;
import bearmetalcarts.CustomDataHolderMinecart;
import bearmetalcarts.FurnaceEngineMinecart;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The furnace-cart half of the engine described in {@link FurnaceEngineMinecart}: replaces vanilla's
 * add-the-push-vector-and-be-done slowdown with a wind-up ramp, stands aside while
 * {@link NewMinecartBehaviorBrakeMixin} is bleeding speed off on a brake rail, and stops the furnace burning fuel
 * while the cart is sitting still.
 *
 * <p>It also mirrors the cart's heading into its {@code BearMetalCarts} data. Vanilla's {@code push} vector
 * survives a stop on its own — {@code calculateNewPushAlong} declines to re-project it once the cart is below
 * 0.001 blocks/tick, so a parked cart keeps pointing the way it came in. What it does not survive is the tank
 * running dry, since {@code MinecartFurnace.tick} zeroes push whenever fuel hits zero. The saved heading is what
 * lets a cart pull away in the right direction after being refuelled, and {@code MinecartItemMixin} seeds it from
 * the placing player's facing so a cart that only ever sees hopper-fed fuel still knows which way to go.
 */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceEngineMixin implements FurnaceEngineMinecart {

	@Shadow
	public Vec3 push;

	@Shadow
	private int fuel;

	@Shadow
	protected abstract double getMaxSpeed(ServerLevel level);

	// A tick stamp rather than a boolean: it cannot go stale, so a tick where the halt runs without a matching
	// applyNaturalSlowdown (the behavior only calls the latter on its first track iteration) can't leak into the
	// next tick and suppress a push that should have applied.
	@Unique
	private int bearmetalcarts$brakingTick = -1;

	// Fuel level to hand back at the end of a tick the cart spent stopped; -1 means "let this tick's burn stand".
	@Unique
	private int bearmetalcarts$heldFuel = -1;

	@Override
	public void bearmetalcarts$markBraking() {
		this.bearmetalcarts$brakingTick = ((MinecartFurnace) (Object) this).tickCount;
	}

	@Override
	public boolean bearmetalcarts$isBraking() {
		return this.bearmetalcarts$brakingTick == ((MinecartFurnace) (Object) this).tickCount;
	}

	@Unique
	private boolean bearmetalcarts$isStopped() {
		MinecartFurnace self = (MinecartFurnace) (Object) this;
		return self.getDeltaMovement().horizontalDistanceSqr() < STOP_SPEED * STOP_SPEED;
	}

	@Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
	private void bearmetalcarts$driveEngine(final Vec3 deltaMovement, final CallbackInfoReturnable<Vec3> cir) {
		if (this.bearmetalcarts$isBraking()) {
			// NewMinecartBehaviorBrakeMixin already applied the braking curve to this value earlier in the tick.
			// Pass it straight through — applying the curve a second time here would halve the stopping distance,
			// and stacking the usual drag on top would eat into it too. Flattening Y is all vanilla would do.
			cir.setReturnValue(deltaMovement.horizontal());
			return;
		}

		MinecartFurnace self = (MinecartFurnace) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel) || this.push.horizontalDistanceSqr() <= 1.0E-7) {
			// Unfueled or client-side: no engine to model, so let vanilla coast the cart as usual.
			return;
		}

		Vec3 heading = this.push.horizontal().normalize();
		// Returns the post-drag speed directly: letting vanilla's slowdown factor apply on top would cost more
		// speed per tick than the ramp adds, and the cart would stall short of its cap instead of reaching it.
		cir.setReturnValue(FurnaceEngineMinecart.accelerate(deltaMovement, heading, this.getMaxSpeed(serverLevel)));
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void bearmetalcarts$holdFuelWhileStopped(final CallbackInfo ci) {
		MinecartFurnace self = (MinecartFurnace) (Object) this;
		this.bearmetalcarts$heldFuel = -1;
		if (!self.level().isClientSide() && this.fuel > 0 && this.bearmetalcarts$isStopped()) {
			this.bearmetalcarts$heldFuel = this.fuel;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void bearmetalcarts$refundFuelAndRememberHeading(final CallbackInfo ci) {
		MinecartFurnace self = (MinecartFurnace) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}

		// Hand back the tick's burn: a cart parked at a station shouldn't eat its way through a stack of coal
		// just to sit there. Refunding after the fact rather than skipping the decrement keeps vanilla's own
		// out-of-fuel handling in tick() on its normal path.
		if (this.bearmetalcarts$heldFuel >= 0) {
			this.fuel = this.bearmetalcarts$heldFuel;
		}

		if (this.push.horizontalDistanceSqr() > 1.0E-7) {
			CustomDataHolderMinecart holder = (CustomDataHolderMinecart) this;
			BearMetalCartsData.setPushDirection(
					holder.bearmetalcarts$getCustomData(), this.push.horizontal().normalize());
		}
	}
}
