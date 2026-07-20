package bearmetalcarts;

import net.minecraft.world.phys.Vec3;

/**
 * Gives a furnace minecart an engine with mass behind it: it winds up to speed over a distance and coasts to a
 * halt over one, instead of vanilla's near-instant jump to max speed and its refusal to stop on a brake rail.
 *
 * <p>Both curves are expressed as blocks travelled per block/tick of speed, so stopping and starting distance
 * scale with each tier's max speed for free — a vanilla furnace cart (0.2 blocks/tick) and a netherite one (0.6)
 * take the same <em>time</em> to wind up, but the netherite cart covers three times the ground doing it.
 *
 * <p><b>Braking.</b> Vanilla runs the brake-rail halt ({@code NewMinecartBehavior.calculateHaltTrackSpeed}, which
 * scales speed by 0.5) <em>before</em> {@code MinecartFurnace.applyNaturalSlowdown}, which re-adds the engine's
 * push vector every tick. The two settle at an equilibrium rather than a stop, which is why a fueled furnace cart
 * only sagged slightly on a brake rail instead of parking on it. The halt is replaced with the geometric curve
 * below, and the push is suppressed for the rest of the tick — which is what {@link #bearmetalcarts$markBraking()}
 * signals across the two mixins involved.
 *
 * <p><b>Acceleration.</b> Vanilla's push branch adds the push vector outright each tick, reaching an equilibrium
 * far above any tier's cap, so the cart pinned itself to max speed within a tick or two. {@link #accelerate} ramps
 * speed linearly instead. Note this governs the cart's own engine only: powered rails still boost a furnace cart
 * at vanilla's 0.06/tick, so a cart sitting on powered rail reaches speed much faster than its own engine would.
 */
public interface FurnaceEngineMinecart {
	/** Blocks of stopping distance per block/tick of speed: a 0.2 blocks/tick cart parks in about three blocks. */
	double STOPPING_DISTANCE_PER_SPEED = 15.0;

	/** Fraction of speed kept each braking tick; the geometric sum of this is {@link #STOPPING_DISTANCE_PER_SPEED}. */
	double SPEED_RETAINED_PER_TICK = 1.0 - 1.0 / STOPPING_DISTANCE_PER_SPEED;

	/** Blocks of wind-up per block/tick of speed. Deliberately twice the braking figure: a cart needs more room to
	 * get going than to pull up, so a stretch of track long enough to stop in is never long enough to launch in. */
	double ACCELERATION_DISTANCE_PER_SPEED = 2.0 * STOPPING_DISTANCE_PER_SPEED;

	/** Ticks from a standstill to max speed. Constant across tiers, since accelerating at {@code max / this} over
	 * this many ticks covers {@code max * ACCELERATION_DISTANCE_PER_SPEED} blocks whatever the tier's max is. */
	double ACCELERATION_TICKS = 2.0 * ACCELERATION_DISTANCE_PER_SPEED;

	/** Below this the cart is parked outright, so it settles instead of crawling down an infinite geometric tail. */
	double STOP_SPEED = 0.01;

	/** Marks the cart as braking for the current tick; {@link #bearmetalcarts$isBraking()} reads it back. */
	void bearmetalcarts$markBraking();

	/** Whether the cart hit a brake rail this tick and should therefore not apply its engine push. */
	boolean bearmetalcarts$isBraking();

	/**
	 * Caps this tick's {@link #accelerate} target below the cart's own max speed — set by {@code PushChainSolver}
	 * whenever it finds another cart ahead on the rail path, to whatever that cart can currently be driven to.
	 * Without this, a furnace's engine ramps toward its own max speed every tick regardless of what it's pushing,
	 * gets slapped back down by the next collision solve the instant it overtakes the cart ahead, and immediately
	 * starts ramping up again — a tug-of-war that reads as the pushed cart rushing forward and abruptly
	 * correcting. Capping the ramp target here instead means the engine converges on the sustainable speed
	 * smoothly, the same way it already does for its own tier's cap.
	 */
	void bearmetalcarts$markCoupledCap(double maxSpeed);

	/** This tick's engine cap from {@link #bearmetalcarts$markCoupledCap}, or {@link Double#MAX_VALUE} if nothing
	 * marked one — i.e. nothing is currently ahead on the path to push against. */
	double bearmetalcarts$getCoupledCap();

	/** Vanilla's halt curve, replaced with one that bleeds speed off over a distance instead of in a few ticks. */
	static Vec3 brake(Vec3 deltaMovement) {
		// Vanilla's applyNaturalSlowdown always flattens Y; match it so braking doesn't leak vertical movement.
		Vec3 horizontal = deltaMovement.horizontal();
		return horizontal.length() < STOP_SPEED ? Vec3.ZERO : horizontal.scale(SPEED_RETAINED_PER_TICK);
	}

	/**
	 * Winds the cart up towards {@code maxSpeed} along its current heading, falling back to {@code heading} (the
	 * engine's push direction) once it is too slow for its own movement to say which way it is pointing.
	 *
	 * <p>A cart already over the cap — boosted by a powered rail, or running downhill — is left alone rather than
	 * yanked back down; vanilla clamps it right after this anyway.
	 */
	static Vec3 accelerate(Vec3 deltaMovement, Vec3 heading, double maxSpeed) {
		Vec3 horizontal = deltaMovement.horizontal();
		double speed = horizontal.length();
		Vec3 direction = speed > 1.0E-6 ? horizontal.normalize() : heading;
		double newSpeed = speed >= maxSpeed ? speed : Math.min(maxSpeed, speed + maxSpeed / ACCELERATION_TICKS);
		return direction.scale(newSpeed);
	}
}
