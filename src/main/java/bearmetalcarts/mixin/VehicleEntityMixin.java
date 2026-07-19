package bearmetalcarts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import bearmetalcarts.AttributeHolderMinecart;
import bearmetalcarts.ModAttributes;
import bearmetalcarts.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's shared vehicle destroy path only ever preserves an entity's custom name onto the item it drops, so
 * a tiered minecart's speed component is otherwise lost when it's broken. This restores it, along with the
 * tier's display name (kept off the entity itself so it never renders as a nameplate or container title, see
 * {@link AttributeHolderMinecart#bearmetalcarts$getTierName()}).
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
		if ((Object) this instanceof AttributeHolderMinecart holder) {
			Component tierName = holder.bearmetalcarts$getTierName();
			if (tierName != null) {
				AttributeInstance instance = holder.bearmetalcarts$getAttributeMap().getInstance(ModAttributes.MINECART_SPEED);
				if (instance != null) {
					itemStack.set(ModComponents.MINECART_SPEED, instance.getBaseValue());
				}

				itemStack.set(DataComponents.CUSTOM_NAME, tierName);
			}
		}

		return itemStack;
	}
}
