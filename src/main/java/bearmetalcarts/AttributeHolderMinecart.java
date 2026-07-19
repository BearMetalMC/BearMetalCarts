package bearmetalcarts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

import org.jspecify.annotations.Nullable;

public interface AttributeHolderMinecart {
	AttributeMap bearmetalcarts$getAttributeMap();

	/**
	 * The display name a tiered minecart item stamped onto this entity at craft/place time, kept separate from
	 * vanilla's own custom name so it never renders as a nameplate or container title, while still being
	 * recoverable to restore the crafted item's name when the entity is broken.
	 */
	@Nullable Component bearmetalcarts$getTierName();

	void bearmetalcarts$setTierName(@Nullable Component name);
}
