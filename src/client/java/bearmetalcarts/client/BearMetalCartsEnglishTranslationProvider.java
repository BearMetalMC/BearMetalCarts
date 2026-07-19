package bearmetalcarts.client;

import java.lang.ref.Reference;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider.TranslationBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class BearMetalCartsEnglishTranslationProvider extends FabricLanguageProvider {
    protected BearMetalCartsEnglishTranslationProvider(FabricPackOutput dataOutput,
                                                       CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider holderLookup, TranslationBuilder translationBuilder) {
        String base = "bearmetalcarts.carts.";


        for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
            Item item = holder.value();
            var path = BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
            Identifier id = Identifier.parse(path);
            if (id.getPath().contains("minecart") && !id.getPath().contains("command")) {
                for (MinecartSpeed cart : BearMetalCartsRecipeProvider.minecartSpeeds) {
                    var cartPath = BuiltInRegistries.ITEM.getKey(cart.item().asItem()).getPath();
                    translationBuilder.add(base + cartPath.replace("_minecart", "") + "." + id.getPath(), cart.fallbackName().apply(id.getPath()));
                }
            }
        }
    }

}
