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

        translationBuilder.add("gamerule.bearmetalcarts.allow_chunkloading", "Minecart Chunk Loading");
        translationBuilder.add("gamerule.bearmetalcarts.allow_chunkloading.description",
                "Whether minecarts load chunks: disallow, while_moving (only moving carts), or allow (moving carts plus a 3x3 area around carts that just stopped).");
        translationBuilder.add("gamerule.bearmetalcarts.mobile_loading_duration", "Moving Minecart Loading Duration");
        translationBuilder.add("gamerule.bearmetalcarts.mobile_loading_duration.description",
                "How long, in ticks, chunks stay loaded after a moving minecart last touched them.");
        translationBuilder.add("gamerule.bearmetalcarts.station_loading_duration", "Stopped Minecart Loading Duration");
        translationBuilder.add("gamerule.bearmetalcarts.station_loading_duration.description",
                "How long, in ticks, the 3x3 chunk area around a minecart stays loaded after it stops moving.");
    }

}
