package bearmetalcarts;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public class ModTicketTypes {
	public static void init() {
		BearMetalCarts.LOGGER.info("Registering {} ticket types", BearMetalCarts.MOD_ID);
	}

	private static TicketType register(String name, long timeout) {
		return Registry.register(
				BuiltInRegistries.TICKET_TYPE,
				BearMetalCarts.id(name),
				new TicketType(timeout,
						TicketType.FLAG_PERSIST
								| TicketType.FLAG_LOADING
								| TicketType.FLAG_SIMULATION
								| TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
	}

	// The type timeouts mirror the duration gamerule defaults, but every ticket is
	// submitted with an explicit ticks-left read from the gamerule at submit time
	// (see MinecartChunkLoader). The timeout must stay non-zero: vanilla's
	// TicketStorage only counts down and expires tickets whose type has a timeout.
	public static final TicketType MOBILE_CART = register("mobile_cart", 40L);
	public static final TicketType STATION_CART = register("station_cart", 600L);
}
