package bearmetalcarts.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import bearmetalcarts.BearMetalCarts;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * Maps a cart's tier to its entity texture: tier {@code "_gold"} renders with
 * {@code bearmetalcarts:textures/entity/minecart/gold.png}, mirroring the {@code <tier>.png} overlay names the item
 * models already use. Tiers are not enumerated here on purpose — adding a tier to
 * {@code BearMetalCartsRecipeProvider.minecartSpeeds} and dropping in a matching texture is meant to be the whole
 * job, with no third list to keep in sync.
 *
 * <p>The consequence is that the tier string reaching this class is untrusted (it round-trips through NBT and can
 * be hand-set with {@code /data}), so it is matched against {@link #TIER} rather than pasted into a path — an
 * identifier path may legally contain {@code .} and {@code /}, which is enough to walk out of the texture
 * directory. Anything unrecognised resolves to null and the cart falls back to the vanilla texture.
 */
public final class CartTextures {
	private static final Pattern TIER = Pattern.compile("_?[a-z0-9_]+");

	/**
	 * Keyed by raw tier string, so a bad tier costs one regex match ever rather than one per cart per frame. Bounded
	 * in practice by the number of distinct tiers actually placed in the world.
	 */
	private static final Map<String, Optional<Identifier>> CACHE = new ConcurrentHashMap<>();

	private CartTextures() {
	}

	public static @Nullable Identifier forTier(String tier) {
		return CACHE.computeIfAbsent(tier, CartTextures::resolve).orElse(null);
	}

	private static Optional<Identifier> resolve(String tier) {
		if (!TIER.matcher(tier).matches()) {
			return Optional.empty();
		}

		String name = tier.startsWith("_") ? tier.substring(1) : tier;
		return Optional.ofNullable(
				Identifier.tryBuild(BearMetalCarts.MOD_ID, "textures/entity/minecart/" + name + ".png"));
	}
}
