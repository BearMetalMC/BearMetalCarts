package bearmetalcarts.client.mixin;

import bearmetalcarts.client.TieredMinecartRenderState;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the tier texture slot described by {@link TieredMinecartRenderState} to vanilla's minecart render state. */
@Mixin(MinecartRenderState.class)
public abstract class MinecartRenderStateMixin implements TieredMinecartRenderState {

	@Unique
	private @Nullable Identifier bearmetalcarts$cartTexture;

	@Override
	public @Nullable Identifier bearmetalcarts$getCartTexture() {
		return this.bearmetalcarts$cartTexture;
	}

	@Override
	public void bearmetalcarts$setCartTexture(final @Nullable Identifier texture) {
		this.bearmetalcarts$cartTexture = texture;
	}
}
