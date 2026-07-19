package bearmetalcarts;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ModMinecartAttributes {
	public static final AttributeSupplier MINECART = AttributeSupplier.builder()
			.add(ModAttributes.MINECART_SPEED)
			.build();

	private ModMinecartAttributes() {
	}
}
