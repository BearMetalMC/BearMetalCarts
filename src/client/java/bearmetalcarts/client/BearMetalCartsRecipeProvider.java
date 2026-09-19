package bearmetalcarts.client;

import java.util.concurrent.CompletableFuture;

import bearmetalcarts.BearMetalCartsData;
import net.minecraft.advancements.Advancement;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import org.jspecify.annotations.NonNull;

public class BearMetalCartsRecipeProvider extends FabricRecipeProvider {
	public BearMetalCartsRecipeProvider(FabricPackOutput output,
			CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	public static MinecartSpeed[] minecartSpeeds = new MinecartSpeed[] {
			new MinecartSpeed(Items.COPPER_BLOCK.weathering().oxidized(), 0.05, 3, "_oxidized", "Oxidized Copper", 1.25),
			new MinecartSpeed(Items.COPPER_BLOCK.weathering().weathered(), 0.1, 3, "_weathered", "Weathered Copper", 1.25),
			new MinecartSpeed(Items.COPPER_BLOCK.weathering().exposed(), 0.2, 3, "_exposed", "Exposed Copper", 1.25),
			new MinecartSpeed(Items.COPPER_BLOCK.weathering().unaffected(), 0.6, 3, "_copper", "Copper", 1.25),
			new MinecartSpeed(Items.GOLD_BLOCK, .8, 3, "_gold", "Gold", 1.5),
			new MinecartSpeed(Items.NETHERITE_BLOCK, 1.2, 1, "_netherite", "Netherite", 2.0)
	};

	@Override
	protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider registries,
			@NonNull BootstrapContext<Recipe<?>> recipes, @NonNull BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				HolderLookup.RegistryLookup<Item> itemLookup = registries.lookupOrThrow(Registries.ITEM);
				for (var itemHolder : itemLookup.listElements().toList()) {
					Item item = itemHolder.value();
					Identifier id = Identifier.parse(getItemName(item));
					if (id.getNamespace().equals("minecraft") && id.getPath().contains("minecart") && !id.getPath().contains("command")) {
						for (MinecartSpeed minecartSpeed : minecartSpeeds) {
							Component itemName = Component.translatable(item.getDescriptionId());
							var text = Component.translatableWithFallback(
											"bearmetalcarts.carts.minecraft" + minecartSpeed.tierKey(),
											minecartSpeed.tierName() + " %s", itemName);
									text.setStyle(Style.EMPTY.withItalic(false));
							double mass = BearMetalCartsData.baseMassForCartId(id.getPath()) * minecartSpeed.massMultiplier();
							DataComponentPatch patch = DataComponentPatch.builder()
									.set(DataComponents.CUSTOM_DATA,
											CustomData.of(BearMetalCartsData.customDataForTier(minecartSpeed.speed(), mass, minecartSpeed.tierKey())))
									.set(DataComponents.CUSTOM_NAME, text)
									.build();

							ItemStackTemplate template = new ItemStackTemplate(itemHolder, 1, patch);

							var b = shapeless(RecipeCategory.TRANSPORTATION, template).requires(item);
							for (int i = 0; i < minecartSpeed.cost(); i++) {
								b.requires(minecartSpeed.item());
							}
							b.unlockedBy(getHasName(minecartSpeed.item()),has(minecartSpeed.item())).save(output, "bearmetal_" +minecartSpeed.nameKey()+ "_"+ id.getPath());
						}
					}
				}
			}
		};
	}

	@Override
	public @NonNull String getName() {
		return "BearMetalCartsRecipeProvider";
	}
}
