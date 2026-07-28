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

	public static final TicketType MOBILE_CART = register("mobile_cart", 40L);
	public static final TicketType STATION_CART = register("station_cart", 600L);
}
