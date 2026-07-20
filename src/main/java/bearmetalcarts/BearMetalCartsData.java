package bearmetalcarts;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The shared NBT layout for tiered-cart data, kept identical in both places it lives so values move between them
 * without translation: on items as {@code {BearMetalCarts:{max_speed:...}}} inside the vanilla
 * {@code minecraft:custom_data} component, and on minecart entities as a top-level {@code BearMetalCarts} compound
 * in their save data. Using only vanilla components and plain NBT means vanilla clients can join a server running
 * this mod — no custom registry entries need to sync.
 */
public final class BearMetalCartsData {
	public static final String TAG_ROOT = "BearMetalCarts";
	public static final String TAG_MAX_SPEED = "max_speed";

	private BearMetalCartsData() {
	}

	/** Builds the full custom_data tag for an item that should place a cart with the given max speed. */
	public static CompoundTag customDataWithMaxSpeed(double speed) {
		CompoundTag data = new CompoundTag();
		data.putDouble(TAG_MAX_SPEED, speed);
		CompoundTag root = new CompoundTag();
		root.put(TAG_ROOT, data);
		return root;
	}

	public static Optional<Double> maxSpeedFromStack(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return Optional.empty();
		}

		return customData.copyTag().getCompound(TAG_ROOT).flatMap(data -> data.getDouble(TAG_MAX_SPEED));
	}

	public static void setMaxSpeedOnStack(ItemStack stack, double speed) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag data = tag.getCompound(TAG_ROOT).orElseGet(CompoundTag::new);
			data.putDouble(TAG_MAX_SPEED, speed);
			tag.put(TAG_ROOT, data);
		});
	}
}
