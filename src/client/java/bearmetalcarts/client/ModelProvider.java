package bearmetalcarts.client;

import bearmetalcarts.BearMetalCartsData;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.conditional.ComponentMatches;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.component.predicates.CustomDataPredicate;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ModelProvider extends FabricModelProvider {
    private static final String MOD_ID = "bearmetalcarts";

    public ModelProvider(FabricPackOutput output) {
        super(output);
    }

    public static ConditionalItemModelProperty tierMatches(String tier) {
        CompoundTag compound = new CompoundTag();
        compound.put(BearMetalCartsData.TAG_TIER, new StringTag(tier));
        CustomDataPredicate predicate = CustomDataPredicate.customData(new NbtPredicate(compound));
        DataComponentPredicate.Single<CustomDataPredicate> single =
                new DataComponentPredicate.Single<>(DataComponentPredicates.CUSTOM_DATA, predicate);
        return new ComponentMatches(single);
    }

    @Override
    public void generateBlockStateModels(@NonNull BlockModelGenerators blockModelGenerators) {

    }

    @Override
    public void generateItemModels(@NonNull ItemModelGenerators generator) {
        var variants = List.of("_gold", "_netherite", "_copper", "_exposed", "_weathered", "_oxidized");
        var items = List.of(Items.MINECART, Items.CHEST_MINECART, Items.FURNACE_MINECART, Items.TNT_MINECART);

        for (var item : items) {
            String baseTextureName = itemBaseTextureName(item);

            ItemModel.Unbaked model = ItemModelUtils.plainModel(
                    generator.createFlatItemModel(item, ModelTemplates.FLAT_ITEM));

            for (var variant : variants) {
                String modelName = baseTextureName + variant;
                Identifier variantModel = createLayeredVariantModel(generator, item, modelName, variant.substring(1));

                model = ItemModelUtils.conditional(
                        tierMatches(variant),
                        ItemModelUtils.plainModel(variantModel),
                        model
                );
            }

            generator.itemModelOutput.accept(item, model);
        }
    }

    private Identifier createLayeredVariantModel(ItemModelGenerators generator, Item vanillaItem, String modelName, String overlayTextureName) {
        Identifier modelId = Identifier.fromNamespaceAndPath(MOD_ID, "item/" + modelName);
        Material vanillaLayer = TextureMapping.getItemTexture(vanillaItem);
        Material overlayLayer = new Material(Identifier.fromNamespaceAndPath(MOD_ID, "item/" + overlayTextureName));
        return ModelTemplates.TWO_LAYERED_ITEM.create(modelId, TextureMapping.layered(vanillaLayer, overlayLayer), generator.modelOutput);
    }

    private static String itemBaseTextureName(Item item) {
        if (item == Items.MINECART) return "minecart";
        if (item == Items.CHEST_MINECART) return "chest_minecart";
        if (item == Items.FURNACE_MINECART) return "furnace_minecart";
        if (item == Items.TNT_MINECART) return "tnt_minecart";
        throw new IllegalArgumentException("No texture name mapping for " + item);
    }

    @Override
    public @NonNull String getName() {
        return "BearMetalCartsModelProvider";
    }
}