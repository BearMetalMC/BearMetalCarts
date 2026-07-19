package bearmetalcarts.mixin;

import bearmetalcarts.AttributeHolderMinecart;
import bearmetalcarts.ModAttributes;
import bearmetalcarts.ModMinecartAttributes;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartAttributeMixin implements AttributeHolderMinecart {

	@Unique
	private AttributeMap bearmetalcarts$attributeMap;

	@Unique
	private @Nullable Component bearmetalcarts$tierName;

	@Override
	public AttributeMap bearmetalcarts$getAttributeMap() {
		if (this.bearmetalcarts$attributeMap == null) {
			this.bearmetalcarts$attributeMap = new AttributeMap(ModMinecartAttributes.MINECART);
		}

		return this.bearmetalcarts$attributeMap;
	}

	@Override
	public @Nullable Component bearmetalcarts$getTierName() {
		return this.bearmetalcarts$tierName;
	}

	@Override
	public void bearmetalcarts$setTierName(final @Nullable Component name) {
		this.bearmetalcarts$tierName = name;
	}

	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
	private void bearmetalcarts$overrideMaxSpeed(final ServerLevel level, final CallbackInfoReturnable<Double> cir) {
		AttributeInstance instance = this.bearmetalcarts$getAttributeMap().getInstance(ModAttributes.MINECART_SPEED);
		if (instance != null) {
			cir.setReturnValue(instance.getValue());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$writeAttributes(final ValueOutput output, final CallbackInfo ci) {
		output.store("BearMetalCartsAttributes", AttributeInstance.Packed.LIST_CODEC, this.bearmetalcarts$getAttributeMap().pack());
		output.storeNullable("BearMetalCartsTierName", ComponentSerialization.CODEC, this.bearmetalcarts$tierName);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$readAttributes(final ValueInput input, final CallbackInfo ci) {
		input.read("BearMetalCartsAttributes", AttributeInstance.Packed.LIST_CODEC)
				.ifPresent(this.bearmetalcarts$getAttributeMap()::apply);
		this.bearmetalcarts$tierName = input.read("BearMetalCartsTierName", ComponentSerialization.CODEC).orElse(null);
	}
}
