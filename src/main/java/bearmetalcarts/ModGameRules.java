package bearmetalcarts;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public class ModGameRules {
	public static void init() {
		BearMetalCarts.LOGGER.info("Registering {} game rules", BearMetalCarts.MOD_ID);
	}

	public static final GameRule<ChunkLoadingMode> ALLOW_CHUNKLOADING = GameRuleBuilder
			.forEnum(ChunkLoadingMode.ALLOW)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(BearMetalCarts.id("allow_chunkloading"));

	public static final GameRule<Integer> MOBILE_LOADING_DURATION = GameRuleBuilder
			.forInteger(40)
			.minValue(0)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(BearMetalCarts.id("mobile_loading_duration"));

	public static final GameRule<Integer> STATION_LOADING_DURATION = GameRuleBuilder
			.forInteger(600)
			.minValue(0)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(BearMetalCarts.id("station_loading_duration"));
}
