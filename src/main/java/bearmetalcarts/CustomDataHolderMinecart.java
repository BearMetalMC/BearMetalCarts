package bearmetalcarts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

/**
 * Vanilla-client-safe successor to {@link AttributeHolderMinecart}: mixed-in minecarts expose the mutable
 * {@code BearMetalCarts} compound persisted with the entity (layout in {@link BearMetalCartsData}) instead of a
 * custom attribute map, so no registry entries have to sync to the client.
 */
public interface CustomDataHolderMinecart {
	/** The live {@code BearMetalCarts} compound for this cart; mutations persist with the entity. */
	CompoundTag bearmetalcarts$getCustomData();

	/**
	 * The display name a tiered minecart item stamped onto this entity at craft/place time, kept separate from
	 * vanilla's own custom name so it never renders as a nameplate or container title, while still being
	 * recoverable to restore the crafted item's name when the entity is broken.
	 */
	@Nullable Component bearmetalcarts$getTierName();

	void bearmetalcarts$setTierName(@Nullable Component name);
}
