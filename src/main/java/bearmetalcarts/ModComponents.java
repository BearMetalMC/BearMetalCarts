package bearmetalcarts;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class ModComponents {
	protected static void init() {
		BearMetalCarts.LOGGER.info("Registering {} components", BearMetalCarts.MOD_ID);
	}

	public static final DataComponentType<Double> MINECART_SPEED = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(BearMetalCarts.MOD_ID, "minecart_speed"),
			DataComponentType.<Double>builder().persistent(Codec.DOUBLE).build());
}
