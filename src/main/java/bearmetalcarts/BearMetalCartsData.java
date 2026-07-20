package bearmetalcarts;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

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
	public static final String TAG_PUSH_X = "push_x";
	public static final String TAG_PUSH_Z = "push_z";

	private BearMetalCartsData() {
	}

	/**
	 * The heading a furnace cart was travelling in before it stopped. Vanilla's own {@code push} vector is zeroed
	 * the moment a furnace cart runs out of fuel, which loses the heading of any cart parked on a brake rail long
	 * enough to burn through its tank — so the direction is mirrored here, where it survives both the fuel running
	 * out and a trip through the save file.
	 */
	public static Optional<Vec3> pushDirection(CompoundTag data) {
		Optional<Double> x = data.getDouble(TAG_PUSH_X);
		Optional<Double> z = data.getDouble(TAG_PUSH_Z);
		if (x.isEmpty() || z.isEmpty()) {
			return Optional.empty();
		}

		Vec3 direction = new Vec3(x.get(), 0.0, z.get());
		return direction.lengthSqr() > 1.0E-7 ? Optional.of(direction) : Optional.empty();
	}

	public static void setPushDirection(CompoundTag data, Vec3 direction) {
		data.putDouble(TAG_PUSH_X, direction.x);
		data.putDouble(TAG_PUSH_Z, direction.z);
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
