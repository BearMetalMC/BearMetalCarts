package bearmetalcarts;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
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
	public static final String TAG_TIER = "BearMetalCarts_tier";
	public static final String TAG_MAX_SPEED = "max_speed";
	public static final String TAG_MASS = "mass";
	public static final String TAG_PUSH_X = "push_x";
	public static final String TAG_PUSH_Z = "push_z";

	/**
	 * Default masses by cart type, used by {@link PushChainSolver} whenever a cart has no {@code mass} tag of its
	 * own. The unit is arbitrary — only ratios enter the momentum solve — but the ordering is deliberate: a furnace
	 * cart shoves a bare cart around far more than the reverse, a loaded-down chest cart takes real momentum to get
	 * moving, and even the lightest cart is several "units" so small floating-point dust never dominates a solve.
	 */
	public static final double MASS_MINECART = 4.0;
	public static final double MASS_HOPPER = 6.0;
	public static final double MASS_CHEST = 8.0;
	public static final double MASS_FURNACE = 12.0;

	private BearMetalCartsData() {
	}

	/** The type-based default mass for a cart entity with no {@code mass} tag; mirrors {@link #baseMassForCartId}. */
	public static double defaultMass(AbstractMinecart cart) {
		if (cart.isFurnace()) {
			return MASS_FURNACE;
		}
		if (cart instanceof MinecartChest) {
			return MASS_CHEST;
		}
		if (cart instanceof MinecartHopper) {
			return MASS_HOPPER;
		}

		return MASS_MINECART;
	}

	/** The type-based default mass keyed off a vanilla minecart item id path; mirrors {@link #defaultMass}. */
	public static double baseMassForCartId(String itemPath) {
		if (itemPath.contains("furnace")) {
			return MASS_FURNACE;
		}
		if (itemPath.contains("chest")) {
			return MASS_CHEST;
		}
		if (itemPath.contains("hopper")) {
			return MASS_HOPPER;
		}

		return MASS_MINECART;
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

	/** Builds the full custom_data tag for a tiered cart item: placed carts get this max speed and mass. */
	public static CompoundTag customDataForTier(double speed, double mass, String tier) {
		CompoundTag data = new CompoundTag();
		data.putDouble(TAG_MAX_SPEED, speed);
		data.putDouble(TAG_MASS, mass);
		CompoundTag root = new CompoundTag();
		root.put(TAG_ROOT, data);
		root.put(TAG_TIER, new StringTag(tier));
		return root;
	}

	public static Optional<Double> maxSpeedFromStack(ItemStack stack) {
		return doubleFromStack(stack, TAG_MAX_SPEED);
	}

	public static Optional<Double> massFromStack(ItemStack stack) {
		return doubleFromStack(stack, TAG_MASS);
	}

	private static Optional<Double> doubleFromStack(ItemStack stack, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return Optional.empty();
		}

		return customData.copyTag().getCompound(TAG_ROOT).flatMap(data -> data.getDouble(key));
	}

	public static void setDoubleOnStack(ItemStack stack, String key, double value) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag data = tag.getCompound(TAG_ROOT).orElseGet(CompoundTag::new);
			data.putDouble(key, value);
			tag.put(TAG_ROOT, data);
		});
	}
}
