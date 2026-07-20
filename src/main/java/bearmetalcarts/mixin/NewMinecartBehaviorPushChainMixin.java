package bearmetalcarts.mixin;

import bearmetalcarts.PushChainSolver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the {@link PushChainSolver} for each cart just before it moves for the tick, so the velocity it moves with
 * already reflects any cart it was about to run into. {@code moveAlongTrack} is called exactly once per server
 * tick from {@code NewMinecartBehavior.tick} (its internal track iteration lives below this entry point), making
 * its head the one clean per-tick, server-only hook.
 *
 * <p>Like the other physics mixins, this targets only {@link NewMinecartBehavior}, which vanilla installs solely
 * when the {@code minecart_improvements} experiment is enabled — the same gate as the mod's speed and braking
 * overrides, so all the custom physics come and go together.
 */
@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorPushChainMixin extends MinecartBehavior {

	// Extends the target's superclass purely to reach its protected `minecart` field: @Shadow only resolves
	// members declared on the target class itself, and `minecart` is declared one level up on MinecartBehavior.
	protected NewMinecartBehaviorPushChainMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "moveAlongTrack", at = @At("HEAD"))
	private void bearmetalcarts$solvePushChain(final ServerLevel level, final CallbackInfo ci) {
		PushChainSolver.tick(this.minecart, level);
	}
}
