package bearmetalcarts.mixin;

import bearmetalcarts.MinecartChunkLoader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartChunkLoadingMixin {

	@Unique
	private MinecartChunkLoader bearmetalcarts$chunkLoader;

	@Inject(method = "tick", at = @At("TAIL"))
	private void bearmetalcarts$tickChunkLoading(final CallbackInfo ci) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (self.level() instanceof ServerLevel serverLevel) {
			if (this.bearmetalcarts$chunkLoader == null) {
				this.bearmetalcarts$chunkLoader = new MinecartChunkLoader();
			}

			this.bearmetalcarts$chunkLoader.tick(self, serverLevel);
		}
	}
}
