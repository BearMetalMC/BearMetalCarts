package bearmetalcarts;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;

/**
 * Tiered cart speeds are only applied on worlds with vanilla's {@code minecart_improvements} experimental feature
 * enabled (see the gate in {@code AbstractMinecartCustomDataMixin}) — without the experimental minecart physics,
 * carts moving faster than the vanilla speed cap behave unpredictably (derailing, rubber-banding, desync). This
 * class just tells server operators and singleplayer users why their tiered carts aren't going any faster than a
 * vanilla cart.
 */
public final class ModExperiments {
	private static final Component WARNING_MESSAGE = Component.literal(
			"BearMetalCarts: the 'Minecart Improvements' experimental feature is not enabled on this world, so "
					+ "tiered carts are capped at vanilla speed. Enable it when creating a world (Experiments > "
					+ "Minecart Improvements) to unlock higher speeds.");

	private ModExperiments() {
	}

	public static boolean minecartImprovementsEnabled(Level level) {
		return level.enabledFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS);
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(ModExperiments::warnOnServerStarted);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> warnOnPlayerJoin(listener.player, server));
	}

	private static void warnOnServerStarted(MinecraftServer server) {
		if (!minecartImprovementsEnabled(server.overworld())) {
			BearMetalCarts.LOGGER.warn(
					"The 'minecart_improvements' experimental feature is not enabled on this world. Tiered "
							+ "minecart speed overrides are disabled and vanilla speed limits apply, since "
							+ "high-speed carts behave unpredictably without the experimental minecart physics. "
							+ "Enable the Minecart Improvements experiment when creating a world to use custom "
							+ "cart speeds.");
		}
	}

	private static void warnOnPlayerJoin(ServerPlayer player, MinecraftServer server) {
		if (minecartImprovementsEnabled(player.level())) {
			return;
		}

		boolean isOp = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
		if (server.isSingleplayer() || isOp) {
			player.sendSystemMessage(WARNING_MESSAGE);
		}
	}
}
