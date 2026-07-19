package bearmetalcarts;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public final class ModMinecartAttributes {
	public static final AttributeSupplier MINECART = AttributeSupplier.builder()
			.add(ModAttributes.MINECART_SPEED)
			.build();

	private ModMinecartAttributes() {
	}
}
