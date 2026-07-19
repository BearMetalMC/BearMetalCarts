package bearmetalcarts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import bearmetalcarts.AttributeHolderMinecart;
import bearmetalcarts.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;

@Mixin(MinecartItem.class)
public class MinecartItemMixin {
	@ModifyVariable(method = "useOn", at = @At("STORE"), name = "cart")
	private AbstractMinecart bearmetalcarts$setMaxSpeed(AbstractMinecart cart, UseOnContext context) {
			ItemStack stack = context.getItemInHand();
			Double speed = stack.get(ModComponents.MINECART_SPEED);
		if (cart != null && speed !=null) {
			AttributeHolderMinecart holder = (AttributeHolderMinecart) cart;
			AttributeMap attributes = holder.bearmetalcarts$getAttributeMap();
			attributes.getInstance(bearmetalcarts.ModAttributes.MINECART_SPEED).setBaseValue(speed);
			holder.bearmetalcarts$setTierName(stack.get(DataComponents.CUSTOM_NAME));
			cart.setCustomName(null);
		}
		return cart;
	}
}
