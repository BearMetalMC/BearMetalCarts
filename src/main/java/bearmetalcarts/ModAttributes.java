package bearmetalcarts;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public class ModAttributes {
	public static void init() {
		// This method is called during mod initialization to ensure that the attributes
		// are registered.
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
			"minecart_speed", 0.4D, 0.0D, 1024.0D, true);
}
