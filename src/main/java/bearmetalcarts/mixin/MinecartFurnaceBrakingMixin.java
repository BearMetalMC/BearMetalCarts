package bearmetalcarts.mixin;

import bearmetalcarts.BearMetalCartsData;
import bearmetalcarts.BrakingFurnaceMinecart;
import bearmetalcarts.CustomDataHolderMinecart;

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
 * The furnace-cart half of the brake-rail fix described in {@link BrakingFurnaceMinecart}: while
 * {@link NewMinecartBehaviorBrakeMixin} is bleeding off speed, this keeps the engine from shoving the cart back up
 * to its equilibrium speed, and mirrors the cart's heading into its {@code BearMetalCarts} data so it knows which
 * way to leave once the rail powers up again.
 *
 * <p>Vanilla's {@code push} vector survives the stop on its own — {@code calculateNewPushAlong} declines to
 * re-project it once the cart is below 0.001 blocks/tick, so a parked cart keeps pointing the way it came in.
 * What it does not survive is the tank running dry, since {@code MinecartFurnace.tick} zeroes push whenever fuel
 * hits zero. A cart parked on a brake rail burns fuel the whole time it waits, so the saved heading is what lets
 * it pull away in the right direction after it is refuelled.
 */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceBrakingMixin implements BrakingFurnaceMinecart {

	@Shadow
	public Vec3 push;

	// A tick stamp rather than a boolean: it cannot go stale, so a tick where the halt runs without a matching
	// applyNaturalSlowdown (the behavior only calls the latter on its first track iteration) can't leak into the
	// next tick and suppress a push that should have applied.
	@Unique
	private int bearmetalcarts$brakingTick = -1;

	@Override
	public void bearmetalcarts$markBraking() {
		this.bearmetalcarts$brakingTick = ((MinecartFurnace) (Object) this).tickCount;
	}

	@Override
	public boolean bearmetalcarts$isBraking() {
		return this.bearmetalcarts$brakingTick == ((MinecartFurnace) (Object) this).tickCount;
	}

	@Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
	private void bearmetalcarts$skipPushWhileBraking(
			final Vec3 deltaMovement, final CallbackInfoReturnable<Vec3> cir) {
		if (this.bearmetalcarts$isBraking()) {
			// The braking curve already accounted for this tick's whole speed loss, so pass it through untouched
			// rather than stacking the usual drag on top of it.
			cir.setReturnValue(deltaMovement);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void bearmetalcarts$rememberPushDirection(final CallbackInfo ci) {
		MinecartFurnace self = (MinecartFurnace) (Object) this;
		if (self.level().isClientSide() || this.push.horizontalDistanceSqr() <= 1.0E-7) {
			return;
		}

		CustomDataHolderMinecart holder = (CustomDataHolderMinecart) this;
		BearMetalCartsData.setPushDirection(holder.bearmetalcarts$getCustomData(), this.push.horizontal().normalize());
	}
}
