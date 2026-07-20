package bearmetalcarts.mixin;

import bearmetalcarts.BearMetalCartsData;
import bearmetalcarts.CustomDataHolderMinecart;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Vanilla-client-safe replacement for {@link AbstractMinecartAttributeMixin}: cart behavior overrides are read
 * from a plain {@code BearMetalCarts} NBT compound persisted in the entity's save data, rather than a custom
 * attribute map. Because it round-trips through addAdditionalSaveData/readAdditionalSaveData, the compound is
 * also visible to and editable by vanilla's /data command.
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartCustomDataMixin implements CustomDataHolderMinecart {

	@Unique
	private CompoundTag bearmetalcarts$customData = new CompoundTag();

	@Unique
	private @Nullable Component bearmetalcarts$tierName;

	@Override
	public CompoundTag bearmetalcarts$getCustomData() {
		return this.bearmetalcarts$customData;
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
		this.bearmetalcarts$customData.getDouble(BearMetalCartsData.TAG_MAX_SPEED).ifPresent(cir::setReturnValue);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$writeCustomData(final ValueOutput output, final CallbackInfo ci) {
		if (!this.bearmetalcarts$customData.isEmpty()) {
			output.store(BearMetalCartsData.TAG_ROOT, CompoundTag.CODEC, this.bearmetalcarts$customData);
		}

		output.storeNullable("BearMetalCartsTierName", ComponentSerialization.CODEC, this.bearmetalcarts$tierName);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$readCustomData(final ValueInput input, final CallbackInfo ci) {
		this.bearmetalcarts$customData = input.read(BearMetalCartsData.TAG_ROOT, CompoundTag.CODEC).orElseGet(CompoundTag::new);
		this.bearmetalcarts$tierName = input.read("BearMetalCartsTierName", ComponentSerialization.CODEC).orElse(null);
	}
}
