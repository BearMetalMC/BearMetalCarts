package bearmetalcarts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Replaces vanilla's minecart-on-minecart shoving with a pairwise, momentum-conserving collision solve along the
 * rails. Each moving cart, once per tick before it moves, walks its own rail path a few blocks ahead; if a cart it
 * is about to touch sits on that path, both are assigned the shared speed of a perfectly inelastic collision:
 *
 * <pre>s = (m1*s1 + m2*s2) / (m1 + m2)</pre>
 *
 * <p>The solve works on <em>speeds along each cart's own heading</em>, not velocity vectors: on a curve the two
 * carts of a pair point different ways, and averaging their vectors componentwise smears speed across both axes
 * that each cart's rail projection then bleeds off — the source of juttery cornering. Agreeing on a scalar speed
 * while each cart keeps its own direction is what a rigid coupling on curved track actually does (the rails
 * supply the turning force). The shared speed is additionally capped at the front cart's own max speed: driving a
 * cart faster than its cap just makes it re-clamp itself every tick and grind against its pusher — a fast furnace
 * cart behind a slow cart settles at the slow cart's pace instead of endlessly bumping into it.
 *
 * <p>No chain state is ever stored: a "train" of touching carts is just this rule firing again every tick for each
 * adjacent pair, so a push into a line of N carts ripples down it over roughly N ticks, like slack running out of
 * real couplers. Per-cart cost is bounded by {@link #LOOKAHEAD_BLOCKS} regardless of how long the line is.
 * A pair standing within {@link #HOLD_DISTANCE} re-solves every tick it is closing, which is how engine power is
 * respected without modelling it here: a fueled furnace cart's engine re-applies its thrust every tick (see
 * {@code MinecartFurnaceEngineMixin}), the next solve shares that thrust across the pair — divided by the coupled
 * mass, so heavier trains wind up proportionally slower — and nothing in the contact drains it away.
 *
 * <p>The furnace engine's own wind-up curve is fed a speed cap from here too ({@link
 * FurnaceEngineMinecart#bearmetalcarts$markCoupledCap}), set every tick a cart is found ahead regardless of
 * whether a solve fires. A furnace's engine otherwise has no idea it's pushing anything and ramps toward its own
 * tier's max every tick; the instant it overtakes a slower cart ahead, the next solve slaps it back down, and it
 * immediately starts ramping up again — a tug-of-war that shows up as the pushed cart rushing forward and
 * abruptly correcting, worse the bigger the gap between the furnace's own cap and what it's pushing (a heavier
 * load, or a much slower one). Capping the ramp target directly lets the engine converge on the sustainable speed
 * instead of overshooting it every tick. This is more visible with a passenger aboard the pushed cart than
 * without one only because vanilla gives an occupied cart much less of its own drag (0.997 retained per tick vs.
 * 0.975 empty, {@code NewMinecartBehavior.getSlowdownFactor}) — the overshoot itself happens either way, an empty
 * cart's drag was just quietly absorbing most of it.
 *
 * <p>The walk follows the exits of each rail block the way the cart itself would, so a cart on a diverging branch
 * the moving cart would not take never triggers a solve; entering the unconnected back of a curve ends the walk,
 * since the route beyond it is not knowable from rail shapes alone.
 *
 * <p>Masses come from the {@code mass} tag in the cart's {@code BearMetalCarts} data (stamped by tiered items,
 * editable via {@code /bmc <targets> mass}), falling back to per-type defaults in {@link BearMetalCartsData} —
 * furnace carts are the heaviest, then chest, then hopper, then everything else. This is distinct from the
 * manufactured-train mechanic: there is no ownership and no lead cart, only carts repeatedly agreeing on speed.
 *
 */
public final class PushChainSolver {
    /**
     * Rail blocks walked ahead of the cart per tick; bounds the per-cart cost, not a chain length.
     */
    public static final int LOOKAHEAD_BLOCKS = 3;

    /**
     * Distance at which two carts count as touching: a little over the 0.98-block minecart bounding box. Like
     * every distance in the solver this is measured <em>along the walked rail path</em>, never center-to-center —
     * around a hairpin, carts on the parallel legs stand a block apart in space while being several blocks apart
     * on the track, and coupling them across the gap shoved the leading cart the wrong way. The solve fires when
     * the gap left after one more tick of approach would be inside this, which keeps fast carts from tunnelling
     * into (and then permanently overlapping) the cart ahead.
     */
    public static final double CONTACT_DISTANCE = 1.0;

    /**
     * The gap the solver maintains between coupled carts, just over {@link #CONTACT_DISTANCE}. A pair standing
     * inside this both re-solves every closing tick (continuous coupling, see the class doc) and gets its spacing
     * restored <em>geometrically</em>: the front cart is nudged forward a little along its own heading. Spacing
     * must never be maintained through the velocities — an earlier version transferred momentum to reopen the
     * gap, and against a pressing furnace cart that both drained the engine's thrust (trains crawled) and flung
     * the front cart ahead in bump-and-coast cycles.
     */
    public static final double HOLD_DISTANCE = 1.12;

    /**
     * Ceiling on the per-tick standoff correction — a backstop against teleporting carts that somehow ended up
     * deeply overlapped (spawned inside each other, moved by a piston), not a budget for ordinary corrections,
     * which are a hundredth of this once the engine is held to the coupled speed.
     */
    private static final double MAX_SEPARATION_PER_TICK = 0.2;

    private static final double MIN_SPEED = 1.0E-5;

    private PushChainSolver() {
    }

    /**
     * One block of the walked path: where it is, and the (horizontal, unit) direction the path travels through
     * it. All pair geometry is expressed against this direction rather than the rear cart's heading or the
     * center-to-center line — around a hairpin those point somewhere else entirely (even backwards), while the
     * path direction is by construction the way a cart on that block would be carried.
     */
    private record PathStep(BlockPos pos, Vec3 travel) {
    }

    /**
     * A cart found on the walked path, with the step it stands on and its distance measured along the path.
     */
    private record Target(AbstractMinecart cart, PathStep step, double travelDistance) {
    }

    /** A pair that coupled this tick, awaiting its post-movement standoff correction in {@link #enforceSpacing}. */
    private record PendingSpacing(AbstractMinecart rear, AbstractMinecart front, Vec3 frontDir) {
    }

    /** Pairs queued by {@link #solve} during this tick's movement, drained by {@link #enforceSpacing} after it.
     * Server-tick scratch only — never persisted, and cleared every tick, so no chain state outlives a tick. */
    private static final List<PendingSpacing> PENDING_SPACING = new ArrayList<>();

    /**
     * Runs the pairwise solve for one cart; called each server tick before the cart moves along the track.
     */
    public static void tick(AbstractMinecart cart, ServerLevel level) {
        Vec3 velocity = cart.getDeltaMovement().horizontal();
        if (velocity.lengthSqr() < MIN_SPEED * MIN_SPEED) {
            // Stationary carts never initiate; a moving cart behind them will find them in its own lookahead.
            return;
        }

        BlockPos railPos = cart.getCurrentBlockPosOrRailBelow();
        BlockState railState = level.getBlockState(railPos);
        if (!BaseRailBlock.isRail(railState)) {
            return;
        }

        List<PathStep> path = walkPath(level, railPos, railState, velocity);
        Target target = findCartAhead(cart, level, path);
        if (target == null) {
            return;
        }

        solve(cart, target, level);
    }

    /**
     * The rail blocks the cart is about to travel, starting with the one it occupies: at each block, leave through
     * the exit not entered by, stopping at missing rails or at a rail whose exits don't connect back (the "back"
     * of a turn at a junction — carts sitting on the diverging branch are none of our business).
     */
    private static List<PathStep> walkPath(ServerLevel level, BlockPos start, BlockState startState, Vec3 velocity) {
        List<PathStep> path = new ArrayList<>(LOOKAHEAD_BLOCKS + 1);

        RailShape shape = shapeOf(startState);
        Vec3i exit = exitTowards(shape, velocity);
        if (exit == null) {
            path.add(new PathStep(start, velocity.normalize()));
            return path;
        }

        path.add(new PathStep(start, horizontalUnit(exit)));
        BlockPos pos = start;
        for (int i = 0; i < LOOKAHEAD_BLOCKS; i++) {
            BlockPos next = nextRail(level, pos, shape, exit);
            if (next == null) {
                break;
            }

            RailShape nextShape = shapeOf(level.getBlockState(next));
            Vec3i out = exitOnwards(nextShape, exit);
            // A block's travel direction is the way the path leaves it (a curve block's direction is past its
            // apex); at the walk's end — connectivity broken — the entry direction is the best that's known.
            path.add(new PathStep(next, horizontalUnit(out != null ? out : exit)));
            if (out == null) {
                break;
            }

            pos = next;
            shape = nextShape;
            exit = out;
        }

        return path;
    }

    /**
     * An exit offset as a horizontal unit direction (exits are always axis-aligned, never diagonal).
     */
    private static Vec3 horizontalUnit(Vec3i exit) {
        return new Vec3(exit.getX(), 0.0, exit.getZ());
    }

    /**
     * The exit of {@code shape} most aligned with the cart's movement, or null if neither is (e.g. no rail axis
     * component to the velocity, which adjustToRails hasn't cleaned up yet).
     */
    private static @Nullable Vec3i exitTowards(RailShape shape, Vec3 velocity) {
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Vec3i first = exits.getFirst();
        Vec3i second = exits.getSecond();
        double alongFirst = velocity.x * first.getX() + velocity.z * first.getZ();
        double alongSecond = velocity.x * second.getX() + velocity.z * second.getZ();
        if (Math.max(alongFirst, alongSecond) < 1.0E-7) {
            return null;
        }

        return alongFirst >= alongSecond ? first : second;
    }

    /**
     * Having entered a rail travelling along {@code travel}, the exit it leads out through — or null when neither
     * of the rail's exits points back the way we came, i.e. the path ran into the unconnected back of a turn.
     */
    private static @Nullable Vec3i exitOnwards(RailShape shape, Vec3i travel) {
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Vec3i first = exits.getFirst();
        Vec3i second = exits.getSecond();
        if (first.getX() == -travel.getX() && first.getZ() == -travel.getZ()) {
            return second;
        }
        if (second.getX() == -travel.getX() && second.getZ() == -travel.getZ()) {
            return first;
        }

        return null;
    }

    /**
     * The rail block reached by leaving {@code pos} through {@code exit}. In {@link AbstractMinecart#exits} a
     * slope's {@code y == -1} exit marks its bottom end — which sits at the slope's own base level — so the climb
     * to the block above only happens off a slope's top ({@code y == 0}) exit; every other exit connects on the
     * level or, rolling off onto a downslope, one below.
     */
    private static @Nullable BlockPos nextRail(ServerLevel level, BlockPos pos, RailShape shape, Vec3i exit) {
        BlockPos ahead = pos.offset(exit.getX(), 0, exit.getZ());
        boolean climbing = shape.isSlope() && exit.getY() == 0;
        BlockPos[] candidates = climbing
                ? new BlockPos[]{ahead.above(), ahead}
                : new BlockPos[]{ahead, ahead.below()};
        for (BlockPos candidate : candidates) {
            if (BaseRailBlock.isRail(level.getBlockState(candidate))) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * The nearest other cart ahead of this one whose rail block lies on the walked path, nearest measured along
     * the path — a cart two bends away around a hairpin is far, whatever the center-to-center line says.
     */
    private static @Nullable Target findCartAhead(AbstractMinecart cart, ServerLevel level, List<PathStep> path) {
        AABB searchBox = cart.getBoundingBox().inflate(LOOKAHEAD_BLOCKS + 1);

        Target nearest = null;
        for (AbstractMinecart other : level.getEntitiesOfClass(
                AbstractMinecart.class, searchBox, other -> other != cart && other.isAlive())) {
            BlockPos otherRail = other.getCurrentBlockPosOrRailBelow();
            int index = -1;
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i).pos().equals(otherRail)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                continue;
            }

            Vec3 toOther = other.position().subtract(cart.position()).horizontal();
            if (index == 0 && toOther.length() > 1.0E-4 && toOther.dot(path.get(0).travel()) <= 0.0) {
                // Sharing our rail block but behind us; the geometry only ever looks forward.
                continue;
            }

            double distance = travelDistance(cart.position(), other.position(), path, index);
            if (nearest == null || distance < nearest.travelDistance()) {
                nearest = new Target(other, path.get(index), distance);
            }
        }

        return nearest;
    }

    /**
     * Distance from the rear cart to the front cart measured along the walked path: rear position, through the
     * centers of the intermediate path blocks, to the front position. Exact on straight track (the points are
     * collinear), and a slight overcount through curves — which errs on the safe side, coupling late rather than
     * coupling carts that merely stand near each other across a bend.
     */
    private static double travelDistance(Vec3 rearPos, Vec3 frontPos, List<PathStep> path, int frontIndex) {
        Vec3 front = frontPos.horizontal();
        Vec3 previous = rearPos.horizontal();
        double distance = 0.0;
        for (int i = 1; i < frontIndex; i++) {
            BlockPos pos = path.get(i).pos();
            Vec3 center = new Vec3(pos.getX() + 0.5, 0.0, pos.getZ() + 0.5);
            distance += center.subtract(previous).length();
            previous = center;
        }

        return distance + front.subtract(previous).length();
    }

    /**
     * The inelastic solve, in speeds along each cart's own heading (see the class doc for why not velocity
     * vectors): while the carts are closing and within reaction range (already coupled, inside {@link
     * #HOLD_DISTANCE}, or within {@link #REACTION_TICKS} of contact at the current closing rate), both speeds are
     * blended toward the momentum-weighted shared speed — capped at the front cart's own max speed — each along
     * its own direction. Never elastic: carts don't spring apart. Only horizontal motion is pooled; each cart
     * keeps its own vertical motion for its rail logic to settle.
     *
     * <p>The blend is graduated by how many ticks remain before actual contact, not snapped to the shared speed
     * outright: far out, only a small fraction of the gap between a cart's current speed and the shared speed is
     * closed this tick (deferred — the correction compounds gradually over the ticks still available); as
     * contact nears, the fraction rises towards 1 so the pair is fully matched by the time they'd actually touch
     * (aggressive — no runway left to defer into). An already-touching pair (within {@link #HOLD_DISTANCE})
     * always gets the full correction outright, same as before. Snapping outright regardless of how far out the
     * target was is what made a fast cart's correction read as a sudden slam: at high speed the fixed lookahead
     * left barely a tick or two of warning, so the entire momentum-and-cap correction landed in one tick instead
     * of being spread across the many ticks a fast cart's wider {@link #lookaheadBlocks} scan now affords it.
     *
     * <p>All geometry is path-relative. Distance is {@link Target#travelDistance()}, not the center-to-center
     * line. The front cart's speed is signed against the path's direction at its block (with the path, positive;
     * oncoming, negative — a head-on collision resolves to the majority momentum's direction), and both the
     * direction a parked cart is launched in and the spacing nudge follow that same path direction. The rear
     * cart's own heading chose the path, so its speed is positive along it by construction.
     *
     * <p>The spacing nudge runs as a second, independent step, whether or not the velocity blend above ran this
     * tick. A pair that has just matched speeds stops satisfying {@code closing > 0} and would otherwise never
     * get its spacing corrected again — silently sitting compressed (or drifting more compressed still, e.g. from
     * rounding in the momentum-weighted average) instead of settling back out to {@link #HOLD_DISTANCE}. Never
     * through the velocities: an earlier version transferred momentum to reopen the gap, which both drained a
     * pressing furnace cart's thrust and flung the front cart ahead in bump-and-coast cycles.
     */
    private static void solve(AbstractMinecart cart, Target target, ServerLevel level) {
        AbstractMinecart other = target.cart();
        Vec3 pathDir = target.step().travel();
        Vec3 v1h = cart.getDeltaMovement().horizontal();
        Vec3 v2h = other.getDeltaMovement().horizontal();
        double distance = target.travelDistance();

        Vec3 rearHeading = v1h.normalize();
        // The front cart moves along its own heading only sign-corrected to agree with the path, so a parked or
        // oncoming cart being absorbed is sent onwards around the curve, never backwards along the rear cart's
        // line of sight. A moving cart exactly perpendicular to the block's travel axis is mid-corner, not
        // oncoming, so the boundary counts as travelling with the path.
        double frontSign;
        Vec3 frontDir;
        if (v2h.length() > MIN_SPEED) {
            frontSign = v2h.dot(pathDir) >= 0.0 ? 1.0 : -1.0;
            frontDir = v2h.normalize().scale(frontSign);
        } else {
            frontSign = 0.0;
            frontDir = pathDir;
        }

        double s1 = v1h.length();
        double s2 = v2h.length() * frontSign;
        double closing = s1 - s2;
        // Predict contact using the rear cart's own speed, not the pair's closing rate. This runs at the head of
        // the rear cart's tick, so the rear is guaranteed not to have moved yet — but the front cart may already
        // have moved this tick, since entity tick order is arbitrary. When it has, `distance` is inflated by
        // exactly one tick of the front cart's travel and a closing-rate prediction reads the pair as further
        // apart than it will be: measured, a coupled pair sitting at a true 1.12 reported 1.329 (1.12 + the front
        // cart's 0.209) and never once fired, so the velocities never coupled at all and the train was being
        // dragged along purely by the spacing correction. `distance - s1` is the honest worst case — the gap that
        // remains once the rear cart makes the move it is about to make.
        boolean approaching = distance - s1 <= CONTACT_DISTANCE || distance <= HOLD_DISTANCE;
        if (closing > 0.0 && approaching) {
            double m1 = mass(cart);
            double m2 = mass(other);
            double shared = (m1 * s1 + m2 * s2) / (m1 + m2);
            // Impedance cap: a cart can't be driven past its own rail speed cap — it would just re-clamp itself
            // next tick and its pusher would perpetually slam into it. The rear cart clamps itself as it moves,
            // as always.
            shared = Math.min(shared, other.getMaxSpeed(level));

            Vec3 v1 = cart.getDeltaMovement();
            Vec3 v2 = other.getDeltaMovement();
            cart.setDeltaMovement(new Vec3(rearHeading.x * shared, v1.y, rearHeading.z * shared));
            other.setDeltaMovement(new Vec3(frontDir.x * shared, v2.y, frontDir.z * shared));
            cart.needsSync = true;
            other.needsSync = true;

            if (cart instanceof FurnaceEngineMinecart furnace) {
                // Hold the engine to the speed this solve just agreed on, plus one tick of wind-up shared across
                // the coupled mass, so it can't undo the solve later in the same tick by ramping back toward its
                // own tier cap.
                //
                // Capping at the speed actually assigned — rather than at the cart-ahead's *max* speed — is the
                // point: a blocked cart travels far below its max (a netherite cart crawling at 0.39 can still
                // legally do 1.2), so a max-based cap let the engine drive 0.14/tick faster than the cart it was
                // pushing, indefinitely. That sustained overdrive is what buried the furnace inside the cart
                // ahead, with the positional correction saturated fighting it every tick. A max-based cap only
                // ever worked when max happened to equal actual — i.e. all-vanilla carts.
                //
                // The wind-up term has to be here: `shared` is always below the rear cart's own speed while it is
                // closing, so capping at `shared` flat is a ratchet that walks a train's speed monotonically down
                // (measured: a free-track train decaying 0.13 -> 0.07 instead of climbing to 0.6). Scaling the
                // engine's per-tick ramp by its share of the coupled mass is the physical statement — one engine
                // hauling more mass accelerates proportionally slower — and it lets a free train wind up normally
                // while still collapsing onto the front cart's pace when that cart genuinely cannot go faster.
                double ramp = cart.getMaxSpeed(level) / FurnaceEngineMinecart.ACCELERATION_TICKS;
                furnace.bearmetalcarts$markCoupledCap(shared + ramp * (m1 / (m1 + m2)));
            }
        }

        // The standoff itself is not restored here — it is queued for {@link #enforceSpacing}, which runs once
        // every cart has finished moving. See that method for why doing it from inside a cart's own tick cannot
        // work no matter which cart of the pair is moved.
        PENDING_SPACING.add(new PendingSpacing(cart, other, frontDir));
    }

    /**
     * Restores the standoff between every pair that coupled this tick, after all carts have moved.
     *
     * <p>This has to run post-movement. The solve above executes at the <em>head</em> of a cart's
     * {@code moveAlongTrack}, so any position correction made there is immediately followed by that cart's whole
     * tick of travel: setting the gap to {@link #HOLD_DISTANCE} and then letting the rear cart advance {@code s1}
     * leaves the gap the player actually sees at {@code HOLD_DISTANCE - s1}. That was measurable to the digit — a
     * furnace running at 0.4068 sat at a rock-steady 0.7132 end-of-tick gap, i.e. 1.12 - 0.4068, permanently
     * buried a quarter-block into the cart ahead. Swapping which cart of the pair got moved changed nothing,
     * because the problem was never the direction: it was correcting against pre-movement positions.
     *
     * <p>Because it runs after movement, the gap measured here is final for the tick and nothing can eat into it.
     * Corrections are applied to the front cart in queue order, so on a train each correction is computed from
     * the already-corrected position of the cart behind it and the whole chain settles in a single pass.
     */
    public static void enforceSpacing() {
        // Back of the train first. A correction moves the pair's *front* cart, and that cart is the *rear* of the
        // next pair up the train — so settling back-to-front means each correction is computed from a rear cart
        // that is already final, and one pass settles the whole chain. Going front-first instead leaves every
        // pair but the last one broken, because each correction shifts the rear of a pair that was already
        // "finished" (measured: a 3-cart train where only the last-corrected pair read 1.12 and the middle ones
        // sagged to ~1.0).
        PENDING_SPACING.sort(Comparator.comparingDouble(
                p -> p.front().position().horizontal().dot(p.frontDir())));

        for (PendingSpacing pending : PENDING_SPACING) {
            AbstractMinecart rear = pending.rear();
            AbstractMinecart front = pending.front();
            if (!rear.isAlive() || !front.isAlive()) {
                continue;
            }

            // Gap along the direction of travel rather than raw separation, so the standoff stays meaningful
            // through a curve (the same reason the solve works in path-relative geometry).
            double gap = front.position().subtract(rear.position()).horizontal().dot(pending.frontDir());
            if (gap <= 1.0E-4 || gap >= HOLD_DISTANCE) {
                continue;
            }

            double correction = Math.min(HOLD_DISTANCE - gap, MAX_SEPARATION_PER_TICK);
            Vec3 nudged = front.position().add(pending.frontDir().scale(correction));
            front.setPos(nudged.x, nudged.y, nudged.z);
            front.needsSync = true;
        }

        PENDING_SPACING.clear();
    }

    /**
     * The cart's mass: its {@code mass} tag if set (tiered items stamp one), else the per-type default.
     */
    public static double mass(AbstractMinecart cart) {
        return ((CustomDataHolderMinecart) cart).bearmetalcarts$getCustomData()
                .getDouble(BearMetalCartsData.TAG_MASS)
                .orElseGet(() -> BearMetalCartsData.defaultMass(cart));
    }

    private static RailShape shapeOf(BlockState state) {
        return state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty());
    }
}
