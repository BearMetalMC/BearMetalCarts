package bearmetalcarts.mixin;

import bearmetalcarts.BearMetalCartsData;
import bearmetalcarts.CustomDataHolderMinecart;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the furnace minecart a single-item fuel reserve slot. Implementing
 * {@link Container} is what makes hoppers see the cart at all
 * ({@code EntitySelector.CONTAINER_ENTITY_SELECTOR}): insertion is limited to one
 * item of {@link ItemTags#FURNACE_MINECART_FUEL} via {@link #canPlaceItem} and
 * {@link #getMaxStackSize()}, and hopper extraction is blocked entirely by
 * {@link #canTakeItem} always returning false. When the engine's fuel runs out,
 * the reserved item is consumed to top it back up, keeping the cart's push vector
 * alive so it doesn't stall between refuels.
 *
 * <p>The mixin extends {@link AbstractMinecart} so {@link #remove} can soft-override
 * the vanilla method and spill the reserve on destruction, mirroring
 * {@code AbstractMinecartContainer.remove}.
 */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceFuelSlotMixin extends AbstractMinecart implements Container {

	// Mirrors MinecartFurnace.FUEL_TICKS_PER_ITEM, which is private and inlined.
	@Unique
	private static final int BEARMETALCARTS$FUEL_TICKS_PER_ITEM = 3600;

	@Shadow
	private int fuel;

	@Shadow
	public Vec3 push;

	// Null means empty: mixin field initializers never run.
	@Unique
	private ItemStack bearmetalcarts$fuelSlot;

	protected MinecartFurnaceFuelSlotMixin(final EntityType<?> type, final Level level) {
		super(type, level);
	}

	@Unique
	private ItemStack bearmetalcarts$getFuelSlot() {
		return this.bearmetalcarts$fuelSlot == null ? ItemStack.EMPTY : this.bearmetalcarts$fuelSlot;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void bearmetalcarts$refuelFromSlot(final CallbackInfo ci) {
		if (this.level().isClientSide()) {
			return;
		}

		ItemStack reserve = this.bearmetalcarts$getFuelSlot();
		// fuel <= 1 so the top-up lands before vanilla's decrement-and-zero-push
		// logic sees an empty tank; the cart never loses its push direction.
		if (this.fuel <= 1 && !reserve.isEmpty()) {
			reserve.shrink(1);
			this.bearmetalcarts$fuelSlot = reserve.isEmpty() ? ItemStack.EMPTY : reserve;
			this.fuel += BEARMETALCARTS$FUEL_TICKS_PER_ITEM;
			if (this.push.horizontalDistanceSqr() < 1.0E-7) {
				Vec3 movement = this.getDeltaMovement().horizontal();
				if (movement.lengthSqr() > 1.0E-6) {
					this.push = movement.normalize();
				} else {
					// Standing still, so its own movement says nothing about which way it faces: either it burnt
					// dry while parked and lost vanilla's push vector, or it has never moved at all and this
					// hopper-fed item is its first fuel. Fall back to the heading saved when it last ran, or
					// failing that the one MinecartItemMixin seeded from the player who placed it.
					CustomDataHolderMinecart holder = (CustomDataHolderMinecart) this;
					BearMetalCartsData.pushDirection(holder.bearmetalcarts$getCustomData())
							.ifPresent(direction -> this.push = direction);
				}
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$writeFuelSlot(final ValueOutput output, final CallbackInfo ci) {
		if (!this.bearmetalcarts$getFuelSlot().isEmpty()) {
			output.store("BearMetalCartsFuelSlot", ItemStack.CODEC, this.bearmetalcarts$getFuelSlot());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void bearmetalcarts$readFuelSlot(final ValueInput input, final CallbackInfo ci) {
		this.bearmetalcarts$fuelSlot = input.read("BearMetalCartsFuelSlot", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	public void remove(final Entity.RemovalReason reason) {
		if (!this.level().isClientSide() && reason.shouldDestroy()) {
			Containers.dropContents(this.level(), this, this);
		}

		super.remove(reason);
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return this.bearmetalcarts$getFuelSlot().isEmpty();
	}

	@Override
	public ItemStack getItem(final int slot) {
		return slot == 0 ? this.bearmetalcarts$getFuelSlot() : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		if (slot != 0 || count <= 0) {
			return ItemStack.EMPTY;
		}

		ItemStack reserve = this.bearmetalcarts$getFuelSlot();
		ItemStack taken = reserve.split(count);
		this.bearmetalcarts$fuelSlot = reserve.isEmpty() ? ItemStack.EMPTY : reserve;
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(final int slot) {
		ItemStack reserve = this.bearmetalcarts$getFuelSlot();
		this.bearmetalcarts$fuelSlot = ItemStack.EMPTY;
		return slot == 0 ? reserve : ItemStack.EMPTY;
	}

	@Override
	public void setItem(final int slot, final ItemStack itemStack) {
		if (slot == 0) {
			itemStack.limitSize(this.getMaxStackSize(itemStack));
			this.bearmetalcarts$fuelSlot = itemStack;
		}
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(final Player player) {
		return !this.isRemoved() && player.isWithinEntityInteractionRange(this.getBoundingBox(), 4.0);
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack itemStack) {
		return slot == 0 && this.bearmetalcarts$getFuelSlot().isEmpty() && itemStack.is(ItemTags.FURNACE_MINECART_FUEL);
	}

	@Override
	public boolean canTakeItem(final Container into, final int slot, final ItemStack itemStack) {
		return false;
	}

	@Override
	public void clearContent() {
		this.bearmetalcarts$fuelSlot = ItemStack.EMPTY;
	}
}
