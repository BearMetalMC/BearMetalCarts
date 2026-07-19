package bearmetalcarts.client;

import java.util.function.Function;

import net.minecraft.world.level.ItemLike;

public record MinecartSpeed(ItemLike item, double speed, int cost, String nameKey,
		Function<String, String> fallbackName) {
}
