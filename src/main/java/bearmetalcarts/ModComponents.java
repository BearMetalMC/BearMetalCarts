package bearmetalcarts;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * DORMANT: not initialized and must not be referenced from live code — touching this class triggers static
 * registration of the {@code bearmetalcarts:minecart_speed} data component, and any custom component in the
 * registry breaks vanilla clients at connection time (registry sync mismatch). Item speed is currently carried in
 * the vanilla {@code minecraft:custom_data} component instead (see {@link BearMetalCartsData}). Kept for a future
 * BMC+ variant that requires a client-side mod.
 */
public class ModComponents {
	protected static void init() {
		BearMetalCarts.LOGGER.info("Registering {} components", BearMetalCarts.MOD_ID);
	}

	public static final DataComponentType<Double> MINECART_SPEED = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(BearMetalCarts.MOD_ID, "minecart_speed"),
			DataComponentType.<Double>builder().persistent(Codec.DOUBLE).build());
}
