package bearmetalcarts;

import net.minecraft.world.phys.Vec3;

/**
 * Lets a furnace minecart coast to a halt on an unpowered powered rail instead of fighting its own engine there.
 *
 * <p>Vanilla runs the brake-rail halt ({@code NewMinecartBehavior.calculateHaltTrackSpeed}, which scales speed by
 * 0.5) <em>before</em> {@code MinecartFurnace.applyNaturalSlowdown}, which re-adds the engine's push vector every
 * tick. The two settle at an equilibrium rather than a stop, which is why a fueled furnace cart only sags slightly
 * on a brake rail instead of parking on it. BearMetalCarts splits the fix in two: the halt is replaced with the
 * gentler, distance-based curve below, and the push is suppressed for the rest of the tick — which is what
 * {@link #bearmetalcarts$markBraking()} signals across the two mixins involved.
 *
 * <p>Because the curve is geometric, stopping distance falls out proportional to the speed the cart is actually
 * carrying, so it scales with each tier's max speed for free: a vanilla furnace cart (0.2 blocks/tick) parks in
 * about three blocks, a netherite one (0.6 blocks/tick) in about nine.
 */
public interface BrakingFurnaceMinecart {
	/** Blocks of stopping distance per block/tick of speed. See the class javadoc for the resulting distances. */
	double STOPPING_DISTANCE_PER_SPEED = 15.0;

	/** Fraction of speed kept each braking tick; the geometric sum of this is {@link #STOPPING_DISTANCE_PER_SPEED}. */
	double SPEED_RETAINED_PER_TICK = 1.0 - 1.0 / STOPPING_DISTANCE_PER_SPEED;

	/** Below this the cart is parked outright, so it settles instead of crawling down an infinite geometric tail. */
	double STOP_SPEED = 0.01;

	/** Marks the cart as braking for the current tick; {@link #bearmetalcarts$isBraking()} reads it back. */
	void bearmetalcarts$markBraking();

	/** Whether the cart hit a brake rail this tick and should therefore not apply its engine push. */
	boolean bearmetalcarts$isBraking();

	/** Vanilla's halt curve, replaced with one that bleeds speed off over a distance instead of in a few ticks. */
	static Vec3 brake(Vec3 deltaMovement) {
		return deltaMovement.length() < STOP_SPEED ? Vec3.ZERO : deltaMovement.scale(SPEED_RETAINED_PER_TICK);
	}
}
