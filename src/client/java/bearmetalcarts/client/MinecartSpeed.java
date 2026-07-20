package bearmetalcarts.client;

import net.minecraft.world.level.ItemLike;

public record MinecartSpeed(ItemLike item, double speed, int cost, String nameKey, String tierName, double massMultiplier) {
	/**
	 * The translation-key fragment for this tier, e.g. "copper" for nameKey "copper_minecart". Shared between the
	 * recipe and language providers so a tier's display name only needs one translation key regardless of which
	 * vanilla minecart variant it's crafted from.
	 */
	public String tierKey() {
		return this.nameKey.replace("_minecart", "");
	}
}
