package bearmetalcarts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import bearmetalcarts.AttributeHolderMinecart;
import bearmetalcarts.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;

@Mixin(MinecartItem.class)
public class MinecartItemMixin {
	@ModifyVariable(method = "useOn", at = @At("STORE"), name = "cart")
	private AbstractMinecart bearmetalcarts$setMaxSpeed(AbstractMinecart cart, UseOnContext context) {
		if (cart != null) {
			ItemStack stack = context.getItemInHand();
			Double speed = stack.getOrDefault(ModComponents.MINECART_SPEED, 0.4D);
			AttributeMap holder = ((AttributeHolderMinecart) cart).bearmetalcarts$getAttributeMap();
			holder.getInstance(bearmetalcarts.ModAttributes.MINECART_SPEED).setBaseValue(speed);
		}
		return cart;
	}
}
