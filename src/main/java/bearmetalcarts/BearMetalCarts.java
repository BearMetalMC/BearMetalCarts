package bearmetalcarts;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BearMetalCarts implements ModInitializer {
	public static final String MOD_ID = "bearmetalcarts";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Bear Metal Carts mod is initializing!");
		ModAttributes.init();
		ModComponents.init();
		ModGameRules.init();
		ModTicketTypes.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
