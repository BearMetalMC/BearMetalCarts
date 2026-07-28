package bearmetalcarts.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;

import bearmetalcarts.ModAttachments;
import bearmetalcarts.client.CartTextures;
import bearmetalcarts.client.TieredMinecartRenderState;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a tiered cart with its own texture. Vanilla submits the cart model with a single hardcoded
 * {@code MINECART_LOCATION}, so the swap is one substitution at that field read — the whole model, animation and
 * display-block pipeline is left alone, which also means chest/furnace/hopper/TNT carts (all of which render
 * through this class, only differing in the block they display) get tiered for free.
 *
 * <p>Split over two injections because the two halves of the render pipeline see different things: the tier lives
 * on the entity, which only {@code extractRenderState} is handed, while the texture is needed in {@code submit},
 * which is handed only the render state. {@link TieredMinecartRenderState} is the slot in between. The texture is
 * re-resolved on every extract, including back to null, because render states are pooled and reused — a cart that
 * lost its tier must not keep rendering with the last one's texture.
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class AbstractMinecartRendererMixin {

	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
			at = @At("TAIL")
	)
	private void bearmetalcarts$extractTier(final AbstractMinecart cart, final MinecartRenderState state,
			final float partialTick, final CallbackInfo ci) {
		String tier = ModAttachments.tier(cart);
		((TieredMinecartRenderState) state)
				.bearmetalcarts$setCartTexture(tier == null ? null : CartTextures.forTier(tier));
	}

	@ModifyExpressionValue(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;MINECART_LOCATION:Lnet/minecraft/resources/Identifier;",
					opcode = Opcodes.GETSTATIC
			)
	)
	private Identifier bearmetalcarts$tierTexture(final Identifier vanillaTexture, final MinecartRenderState state,
			final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState cameraState) {
		Identifier texture = ((TieredMinecartRenderState) state).bearmetalcarts$getCartTexture();
		return texture != null ? texture : vanillaTexture;
	}
}
