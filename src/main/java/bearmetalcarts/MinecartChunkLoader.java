package bearmetalcarts;

import java.util.Objects;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Per-cart chunk loading state, modeled on how thrown ender pearls keep their
 * chunks loaded ({@code ThrownEnderpearl}/{@code ServerPlayer.placeEnderPearlTicket}),
 * but with two ticket tiers:
 *
 * <ul>
 * <li>While the cart is moving, its current chunk (and, when it nears a chunk edge,
 * the next chunk along the track) get {@link ModTicketTypes#MOBILE_CART} tickets at
 * entity-ticking level so the cart keeps moving through them. Each ticket lasts
 * {@code bearmetalcarts:mobile_loading_duration} ticks past the last refresh, so
 * chunks behind the cart unload shortly after it leaves.</li>
 * <li>When the cart stops moving, a single {@link ModTicketTypes#STATION_CART}
 * ticket loads the 3x3 chunk area around it for
 * {@code bearmetalcarts:station_loading_duration} ticks. It is emitted exactly once
 * per stop.</li>
 * </ul>
 *
 * <p>Both tiers are gated by {@code bearmetalcarts:allow_chunkloading}.
 * Ticket durations come from the gamerules at submit time, which is why tickets are
 * built through {@link Ticket}'s duration constructor (opened via access widener)
 * instead of {@code addTicketWithRadius}; vanilla's ticket storage then counts them
 * down and expires them like any other timed ticket.
 */
public final class MinecartChunkLoader {
	/** Horizontal movement per tick below which the cart counts as stopped. */
	private static final double MOVEMENT_EPSILON_SQR = 1.0E-4;
	/** Entity-ticking ticket (level 31) so the cart still moves in the chunk. */
	private static final int MOBILE_TICKET_RADIUS = 2;
	/** Loads the 3x3 chunk area centered on the stopped cart. */
	private static final int STATION_TICKET_RADIUS = 1;
	private static final int MAX_RAIL_TRACE_STEPS = 32;

	private @Nullable Vec3 lastPos;
	private boolean wasMoving;
	private long resubmitAtGameTime;
	private @Nullable ChunkPos submittedCurrentChunk;
	private @Nullable ChunkPos submittedNextChunk;

	public void tick(AbstractMinecart cart, ServerLevel level) {
		Vec3 pos = cart.position();
		Vec3 previousPos = this.lastPos;
		this.lastPos = pos;
		if (previousPos == null || cart.isRemoved()) {
			return;
		}

		double dx = pos.x - previousPos.x;
		double dz = pos.z - previousPos.z;
		boolean moving = dx * dx + dz * dz > MOVEMENT_EPSILON_SQR;
		boolean justStopped = this.wasMoving && !moving;
		this.wasMoving = moving;

		ChunkLoadingMode mode = level.getGameRules().get(ModGameRules.ALLOW_CHUNKLOADING);
		if (mode == ChunkLoadingMode.DISALLOW) {
			this.clearSubmitted();
			return;
		}

		if (moving) {
			this.tickMoving(cart, level, new Vec3(dx, 0.0, dz));
		} else {
			this.clearSubmitted();
			if (justStopped && mode == ChunkLoadingMode.ALLOW) {
				int duration = level.getGameRules().get(ModGameRules.STATION_LOADING_DURATION);
				if (duration > 0) {
					submitTicket(level, ModTicketTypes.STATION_CART, cart.chunkPosition(), STATION_TICKET_RADIUS, duration);
				}
			}
		}
	}

	private void tickMoving(AbstractMinecart cart, ServerLevel level, Vec3 movement) {
		int duration = level.getGameRules().get(ModGameRules.MOBILE_LOADING_DURATION);
		if (duration <= 0) {
			this.clearSubmitted();
			return;
		}

		ChunkPos current = cart.chunkPosition();
		ChunkPos next = findUpcomingChunk(cart, level, current, movement);
		long now = level.getGameTime();
		if (now < this.resubmitAtGameTime
				&& current.equals(this.submittedCurrentChunk)
				&& Objects.equals(next, this.submittedNextChunk)) {
			return;
		}

		submitTicket(level, ModTicketTypes.MOBILE_CART, current, MOBILE_TICKET_RADIUS, duration);
		if (next != null) {
			submitTicket(level, ModTicketTypes.MOBILE_CART, next, MOBILE_TICKET_RADIUS, duration);
		}

		this.submittedCurrentChunk = current;
		this.submittedNextChunk = next;

		this.resubmitAtGameTime = now + Math.max(1, duration - 1);
	}

	private void clearSubmitted() {
		this.resubmitAtGameTime = 0L;
		this.submittedCurrentChunk = null;
		this.submittedNextChunk = null;
	}

	private static void submitTicket(ServerLevel level, TicketType type, ChunkPos pos, int radius, int durationTicks) {
		ServerChunkCache chunkSource = level.getChunkSource();
		int ticketLevel = ChunkLevel.byStatus(FullChunkStatus.FULL) - radius;

		chunkSource.removeTicketWithRadius(type, pos, radius);
		chunkSource.addTicket(new Ticket(type, ticketLevel, durationTicks), pos);
	}

	/**
	 * The chunk the cart is about to enter, or null while it isn't nearing an edge
	 * in its direction of travel. Rails are only traced once the cart is close to
	 * the edge; off-rail carts fall back to a straight-line projection.
	 */
	private static @Nullable ChunkPos findUpcomingChunk(AbstractMinecart cart, ServerLevel level, ChunkPos current, Vec3 movement) {
		double speed = movement.horizontalDistance();
		if (speed < 1.0E-7) {
			return null;
		}

		Vec3 direction = movement.scale(1.0 / speed);
		double lookahead = Mth.clamp(speed * 40.0, 8.0, 16.0);
		double distanceToEdge = distanceToChunkEdge(cart.position(), direction, current);
		if (distanceToEdge > lookahead) {
			return null;
		}

		if (cart.isOnRails()) {
			ChunkPos traced = traceRailToNextChunk(cart, level, current, direction);
			if (traced != null) {
				return traced;
			}
		}

		ChunkPos projected = ChunkPos.containing(BlockPos.containing(cart.position().add(direction.scale(distanceToEdge + 0.5))));
		return projected.equals(current) ? null : projected;
	}

	/** Distance along {@code direction} until the position crosses out of {@code chunk}. */
	private static double distanceToChunkEdge(Vec3 pos, Vec3 direction, ChunkPos chunk) {
		double distance = Double.POSITIVE_INFINITY;
		if (direction.x > 1.0E-7) {
			distance = Math.min(distance, (chunk.getMaxBlockX() + 1 - pos.x) / direction.x);
		} else if (direction.x < -1.0E-7) {
			distance = Math.min(distance, (pos.x - chunk.getMinBlockX()) / -direction.x);
		}

		if (direction.z > 1.0E-7) {
			distance = Math.min(distance, (chunk.getMaxBlockZ() + 1 - pos.z) / direction.z);
		} else if (direction.z < -1.0E-7) {
			distance = Math.min(distance, (pos.z - chunk.getMinBlockZ()) / -direction.z);
		}

		return distance;
	}

	/**
	 * Walks the track from the cart's rail block in the direction of travel until it
	 * leaves the cart's chunk, returning the chunk the track continues into. Only
	 * blocks inside the cart's own (loaded) chunk are ever inspected. Returns null
	 * if the track ends or can't be followed, in which case the caller projects the
	 * cart's velocity instead.
	 */
	private static @Nullable ChunkPos traceRailToNextChunk(AbstractMinecart cart, ServerLevel level, ChunkPos current, Vec3 direction) {
		BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
		BlockState state = level.getBlockState(pos);
		Vec3i lastStep = null;

		for (int i = 0; i < MAX_RAIL_TRACE_STEPS; i++) {
			if (!(state.getBlock() instanceof BaseRailBlock rail)) {
				return null;
			}

			RailShape shape = state.getValue(rail.getShapeProperty());
			Vec3i step = chooseExit(AbstractMinecart.exits(shape), lastStep, direction);
			if (step == null) {
				return null;
			}

			pos = pos.offset(step);
			if (SectionPos.blockToSectionCoord(pos.getX()) != current.x() || SectionPos.blockToSectionCoord(pos.getZ()) != current.z()) {
				return ChunkPos.containing(pos);
			}

			state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof BaseRailBlock)) {
				BlockPos above = pos.above();
				BlockPos below = pos.below();
				if (level.getBlockState(above).getBlock() instanceof BaseRailBlock) {
					pos = above;
					state = level.getBlockState(above);
				} else if (level.getBlockState(below).getBlock() instanceof BaseRailBlock) {
					pos = below;
					state = level.getBlockState(below);
				}
			}

			lastStep = step;
		}

		return null;
	}

	private static @Nullable Vec3i chooseExit(Pair<Vec3i, Vec3i> exits, @Nullable Vec3i lastStep, Vec3 direction) {
		Vec3i first = exits.getFirst();
		Vec3i second = exits.getSecond();
		if (lastStep != null) {
			boolean firstIsBack = first.getX() == -lastStep.getX() && first.getZ() == -lastStep.getZ();
			boolean secondIsBack = second.getX() == -lastStep.getX() && second.getZ() == -lastStep.getZ();
			if (firstIsBack != secondIsBack) {
				return firstIsBack ? second : first;
			}
		}

		double firstDot = first.getX() * direction.x + first.getZ() * direction.z;
		double secondDot = second.getX() * direction.x + second.getZ() * direction.z;
		if (firstDot == secondDot) {
			return null;
		}

		return firstDot > secondDot ? first : second;
	}
}
