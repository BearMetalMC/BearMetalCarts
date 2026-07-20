package bearmetalcarts.mixin;

import bearmetalcarts.PushChainSolver;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns off vanilla's cart-on-cart contact handling wherever the {@link PushChainSolver} is responsible instead —
 * i.e. under the {@code minecart_improvements} experiment with both carts on rails. Off-rails carts (either one)
 * keep vanilla behavior, since the rail-path solver has no geometry to work with there.
 *
 * <p>Two vanilla mechanisms are disabled. {@code pushOtherMinecart} (via {@code push(Entity)}) crushes both
 * velocities to 20%, special-cases furnace carts, and flings in the contact direction. The hard entity collision
 * ({@code canCollideWith}) is worse: a cart whose move is fully blocked by the cart ahead's collision box gets its
 * velocity zeroed by {@code stepAlongTrack}'s pinned-against-a-wall rule — and since entity tick order is
 * arbitrary, the rear cart of a settling chain randomly hit that and stopped dead on the track. The solver keeps
 * coupled carts spaced apart instead (see {@code PushChainSolver.HOLD_DISTANCE}), so the hard box has no role
 * left between carts on rails.
 *
 * <p>The same block applies to a coupled cart's <em>passenger</em>, not just the cart entity itself.
 * {@code AbstractMinecart.canCollideWith} delegates to {@code AbstractBoat.canVehicleCollide}, which treats any
 * {@code isPushable()} entity as solid — and a rider (a real player or a mannequin behaving like one) is pushable
 * by default, riding or not. Without this, an approaching cart's own collision check saw a solid body sitting
 * exactly where the cart ahead was heading, got physically blocked the instant its box reached the rider, and hit
 * the exact same {@code stepAlongTrack} zero-velocity clamp as the cart-cart case above — independent of anything
 * this mod's solver does, and only visible with a passenger aboard because an empty cart has no rider hitbox to
 * hit. This is what actually caused the "rushes forward then abruptly corrects" bunching reported against
 * occupied carts: the solver's own coupling was never at fault, vanilla's rider collision was firing underneath it.
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartPushMixin {

	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void bearmetalcarts$skipVanillaCartShoving(final Entity entity, final CallbackInfo ci) {
		if (bearmetalcarts$solverOwnsInteraction(entity)) {
			ci.cancel();
		}
	}

	@Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
	private void bearmetalcarts$noHardCollisionOnRails(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
		if (bearmetalcarts$solverOwnsInteraction(entity) || bearmetalcarts$isRidingCoupledCart(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Unique
	private boolean bearmetalcarts$solverOwnsInteraction(final Entity entity) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		return entity instanceof AbstractMinecart other
				&& AbstractMinecart.useExperimentalMovement(self.level())
				&& self.isOnRails()
				&& other.isOnRails();
	}

	@Unique
	private boolean bearmetalcarts$isRidingCoupledCart(final Entity entity) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		return entity.getVehicle() instanceof AbstractMinecart mount
				&& mount != self
				&& AbstractMinecart.useExperimentalMovement(self.level())
				&& self.isOnRails()
				&& mount.isOnRails();
	}
}
