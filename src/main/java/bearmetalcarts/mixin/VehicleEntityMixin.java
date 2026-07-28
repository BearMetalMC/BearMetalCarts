package bearmetalcarts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import bearmetalcarts.BearMetalCartsData;
import bearmetalcarts.CustomDataHolderMinecart;
import bearmetalcarts.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's shared vehicle destroy path only ever preserves an entity's custom name onto the item it drops, so
 * a tiered minecart's speed data is otherwise lost when it's broken. This restores it, along with the
 * tier's display name (kept off the entity itself so it never renders as a nameplate or container title, see
 * {@link CustomDataHolderMinecart#bearmetalcarts$getTierName()}).
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {
	@ModifyVariable(
			method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
			),
			name = "itemStack"
	)
	private ItemStack bearmetalcarts$preserveTierData(ItemStack itemStack) {
		if ((Object) this instanceof CustomDataHolderMinecart holder) {
			Component tierName = holder.bearmetalcarts$getTierName();
			if (tierName != null) {
				holder.bearmetalcarts$getCustomData()
						.getDouble(BearMetalCartsData.TAG_MAX_SPEED)
						.ifPresent(speed -> BearMetalCartsData.setDoubleOnStack(itemStack, BearMetalCartsData.TAG_MAX_SPEED, speed));
				holder.bearmetalcarts$getCustomData()
						.getDouble(BearMetalCartsData.TAG_MASS)
						.ifPresent(mass -> BearMetalCartsData.setDoubleOnStack(itemStack, BearMetalCartsData.TAG_MASS, mass));
				itemStack.set(DataComponents.CUSTOM_NAME, tierName);
			}
		}

		if ((Object) this instanceof AbstractMinecart cart) {
			String tier = ModAttachments.tier(cart);
			if (tier != null) {
				BearMetalCartsData.setTierOnStack(itemStack, tier);
			}
		}

		// Dormant attribute-based implementation, kept for a future BMC+ variant with a required client mod:
		//	if ((Object) this instanceof AttributeHolderMinecart holder) {
		//		Component tierName = holder.bearmetalcarts$getTierName();
		//		if (tierName != null) {
		//			AttributeInstance instance = holder.bearmetalcarts$getAttributeMap().getInstance(bearmetalcarts.ModAttributes.MINECART_SPEED);
		//			if (instance != null) {
		//				itemStack.set(bearmetalcarts.ModComponents.MINECART_SPEED, instance.getBaseValue());
		//			}
		//
		//			itemStack.set(DataComponents.CUSTOM_NAME, tierName);
		//		}
		//	}
		return itemStack;
	}
}
