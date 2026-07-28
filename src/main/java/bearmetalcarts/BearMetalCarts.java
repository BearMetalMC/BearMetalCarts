package bearmetalcarts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BearMetalCarts implements ModInitializer {
	public static final String MOD_ID = "bearmetalcarts";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Bear Metal Carts mod is initializing!");
		// Attribute/component registration is intentionally dormant: registering custom registry entries breaks
		// vanilla clients at connection time (registry sync mismatch). Cart data now lives in plain NBT/custom_data
		// (see BearMetalCartsData). Re-enable these for a future BMC+ variant that requires a client-side mod.
		// ModAttributes.init();
		// ModComponents.init();
		ModGameRules.init();
		ModTicketTypes.init();
		ModCommands.init();
		ModExperiments.init();

		ServerTickEvents.END_SERVER_TICK.register(server -> PushChainSolver.enforceSpacing());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
