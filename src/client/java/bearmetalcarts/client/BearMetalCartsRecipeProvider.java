package bearmetalcarts.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import bearmetalcarts.BearMetalCartsData;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.datafix.fixes.ItemStackCustomNameToOverrideComponentFix;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

public class BearMetalCartsRecipeProvider extends FabricRecipeProvider {
	public BearMetalCartsRecipeProvider(FabricPackOutput output,
			CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	// Mass multipliers scale the per-type base masses in BearMetalCartsData: higher tiers are built heavier, so
	// they carry proportionally more momentum into the push-chain collision solver.
	public static MinecartSpeed[] minecartSpeeds = new MinecartSpeed[] {
			new MinecartSpeed(Items.COPPER_BLOCK.weathering().unaffected(), 0.6, 3, "copper_minecart", "Copper", 1.25),
			new MinecartSpeed(Items.GOLD_BLOCK, .8, 3, "gold_minecart", "Gold", 1.5),
			new MinecartSpeed(Items.NETHERITE_BLOCK, 1.2, 1, "netherite_minecart", "Netherite", 2.0)
	};

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup, RecipeOutput exporter) {
		return new RecipeProvider(registryLookup, exporter) {
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
											"bearmetalcarts.carts." + minecartSpeed.tierKey(),
											minecartSpeed.tierName() + " %s", itemName);
									text.setStyle(Style.EMPTY.withItalic(false));
							// Dormant custom-component variant for a future BMC+ mod with a required client mod:
							//	.set(ModComponents.MINECART_SPEED, minecartSpeed.speed())
							double mass = BearMetalCartsData.baseMassForCartId(id.getPath()) * minecartSpeed.massMultiplier();
							DataComponentPatch patch = DataComponentPatch.builder()
									.set(DataComponents.CUSTOM_DATA,
											CustomData.of(BearMetalCartsData.customDataForTier(minecartSpeed.speed(), mass)))
									.set(DataComponents.CUSTOM_NAME, text)
									.build();

							ItemStackTemplate template = new ItemStackTemplate(itemHolder, 1, patch);

							var b = shapeless(RecipeCategory.TRANSPORTATION, template).requires(item);
							for (int i = 0; i < minecartSpeed.cost(); i++) {
								b.requires(minecartSpeed.item());
							}
							b.unlockedBy(getHasName(minecartSpeed.item()),has(minecartSpeed.item())).save(exporter, "bearmetal_" +minecartSpeed.nameKey()+ "_"+ id.getPath());
						}
					}
				}
			}
		};
	}

	@Override
	public String getName() {
		return "BearMetalCartsRecipeProvider";
	}
}
