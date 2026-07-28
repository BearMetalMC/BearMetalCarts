package bearmetalcarts.client;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * Carries a tiered cart's texture from the entity, which is only readable while the render state is being
 * extracted, to the submit pass, which is handed nothing but the render state. Mixed into vanilla's
 * {@code MinecartRenderState} the same way {@code CustomDataHolderMinecart} is mixed into minecarts.
 */
public interface TieredMinecartRenderState {
	/** The cart texture for this frame, or null to render with the vanilla texture. */
	@Nullable Identifier bearmetalcarts$getCartTexture();

	void bearmetalcarts$setCartTexture(@Nullable Identifier texture);
}
