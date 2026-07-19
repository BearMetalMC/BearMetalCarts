package bearmetalcarts.client;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider.TranslationBuilder;
import net.minecraft.core.HolderLookup;

public class BearMetalCartsEnglishTranslationProvider extends FabricLanguageProvider {
    protected BearMetalCartsEnglishTranslationProvider(FabricPackOutput dataOutput,
                                                       CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider holderLookup, TranslationBuilder translationBuilder) {
        for (MinecartSpeed tier : BearMetalCartsRecipeProvider.minecartSpeeds) {
            translationBuilder.add("bearmetalcarts.carts." + tier.tierKey(), tier.tierName() + " %s");
        }
    }

}
