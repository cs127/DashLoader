package dev.notalpha.dashloader.mixin.main;

import dev.notalpha.dashloader.Cache;
import dev.notalpha.dashloader.client.DashLoaderClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	protected abstract void render(boolean tick);

	@Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;reloadResources(Z)Ljava/util/concurrent/CompletableFuture;"))
	private void requestReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		DashLoaderClient.NEEDS_RELOAD = true;
	}


	@Inject(method = "reloadResources(Z)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "RETURN"))
	private void reloadComplete(boolean thing, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		cir.getReturnValue().thenRun(() -> {
			// Reset the manager
			if (DashLoaderClient.CACHE.getStatus() == Cache.Status.LOAD) {
				DashLoaderClient.CACHE.setStatus(Cache.Status.IDLE);
			}
		});
	}


}
