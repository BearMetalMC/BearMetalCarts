package bearmetalcarts;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * DORMANT: part of the attribute-based implementation (see {@link ModAttributes}); referencing this class loads
 * ModAttributes and registers the custom attribute, which breaks vanilla clients. Kept for a future BMC+ variant.
 */
public final class ModMinecartAttributes {
	public static final AttributeSupplier MINECART = AttributeSupplier.builder()
			.add(ModAttributes.MINECART_SPEED)
			.build();

	private ModMinecartAttributes() {
	}
}
