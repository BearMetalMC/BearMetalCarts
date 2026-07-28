package bearmetalcarts;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.jspecify.annotations.Nullable;

/**
 * The crafted tier ("_copper", "_gold", …) a minecart entity was placed from, held as a Fabric data attachment
 * rather than in the {@code BearMetalCarts} compound of {@link BearMetalCartsData} — because it is the one piece
 * of cart data the <em>client</em> needs, and entity save data is never sent to clients. Everything else the mod
 * stores is server-side physics; the tier only decides which texture the cart renders with.
 *
 * <p>Attachments give persistence and client sync for free, and stay inside the mod's vanilla-client-compatibility
 * rule: an attachment type is not a Minecraft registry entry (so nothing is added to registry sync), and Fabric
 * only sends the sync payload to clients that advertised support for this attachment id during the configuration
 * phase — a vanilla client is sent nothing at all and simply renders every cart with the vanilla texture. The
 * value is a plain string so a hand-edited {@code fabric:attachments} compound can retier a cart via {@code /data}.
 */
public final class ModAttachments {
	/**
	 * Registered eagerly in this class's static initializer, which {@link #init()} exists to trigger from the mod
	 * initializer — attachment types must be registered before any world loads.
	 */
	public static final AttachmentType<String> TIER = AttachmentRegistry.create(
			BearMetalCarts.id("tier"),
			builder -> builder
					.persistent(Codec.STRING)
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private ModAttachments() {
	}

	public static void init() {
	}

	/** The tier this cart was placed from, or null for a plain vanilla cart. Valid on both sides once synced. */
	public static @Nullable String tier(AbstractMinecart cart) {
		return ((AttachmentTarget) cart).getAttached(TIER);
	}

	public static void setTier(AbstractMinecart cart, String tier) {
		((AttachmentTarget) cart).setAttached(TIER, tier);
	}
}
