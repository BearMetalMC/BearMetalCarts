package bearmetalcarts;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * DORMANT: not initialized and must not be referenced from live code — touching this class triggers static
 * registration of {@code bearmetalcarts:minecart_speed}, and any custom attribute in the registry breaks vanilla
 * clients at connection time (registry sync mismatch). Cart speed is currently driven by plain NBT instead (see
 * {@link BearMetalCartsData}). Kept for a future BMC+ variant that requires a client-side mod.
 */
public class ModAttributes {
	public static void init() {
		BearMetalCarts.LOGGER.info("Registering {} attributes", BearMetalCarts.MOD_ID);
	}

	private static Holder<Attribute> register(
			String name, double defaultValue, double minValue, double maxValue, boolean syncedWithClient) {
		Identifier identifier = Identifier.fromNamespaceAndPath(BearMetalCarts.MOD_ID, name);
		Attribute entityAttribute = new RangedAttribute(
				identifier.toLanguageKey(),
				defaultValue,
				minValue,
				maxValue).setSyncable(syncedWithClient);

		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, identifier, entityAttribute);
	}

	public static final Holder<Attribute> MINECART_SPEED = register(
			"minecart_speed", 0.4D, 0.0D, 1024.0D, false);
}
