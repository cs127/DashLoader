package dev.notalpha.dashloader.mixin.option.cache.font;

import dev.notalpha.dashloader.Cache;
import dev.notalpha.dashloader.DashLoader;
import dev.notalpha.dashloader.client.font.FontModule;
import dev.notalpha.dashloader.mixin.accessor.FontManagerAccessor;
import dev.notalpha.dashloader.mixin.accessor.FontStorageAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.font.BuiltinEmptyGlyph;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(targets = "net/minecraft/client/font/FontManager$1")
public class FontManagerOverride {

	@SuppressWarnings("UnresolvedMixinReference")
	@Inject(
			method = {"method_18638", "prepare*"},
			at = @At(value = "HEAD"),
			cancellable = true
	)
	private void overridePrepare(ResourceManager resourceManager, Profiler profiler, CallbackInfoReturnable<Map<Identifier, List<Font>>> cir) {
		FontModule.DATA.visit(Cache.Status.LOAD, data -> {
			DashLoader.LOG.info("Preparing fonts");
			Map<Identifier, List<Font>> out = new Object2ObjectOpenHashMap<>();
			data.forEach(
					(identifier, int2ObjectMapListPair) -> out.put(identifier, int2ObjectMapListPair.getValue())
			);
			cir.setReturnValue(out);
		});
	}

	@SuppressWarnings("UnresolvedMixinReference")
	@Inject(
			method = {"method_18635", "apply*"},
			at = @At(value = "HEAD"),
			cancellable = true
	)
	private void overrideApply(Map<Identifier, List<Font>> map, ResourceManager resourceManager, Profiler profiler, CallbackInfo ci) {
		FontModule.DATA.visit(Cache.Status.LOAD, data -> {
			profiler.startTick();
			profiler.push("closing");
			final FontManagerAccessor fontManagerAccessor = (FontManagerAccessor) FontModule.FONTMANAGER;
			final Map<Identifier, FontStorage> fontStorages = fontManagerAccessor.getFontStorages();

			fontStorages.values().forEach(FontStorage::close);
			fontStorages.clear();

			DashLoader.LOG.info("Applying fonts off-thread");
			profiler.swap("reloading");
			data.forEach((identifier, entry) -> {
				FontStorage fontStorage = new FontStorage(fontManagerAccessor.getTextureManager(), identifier);
				FontStorageAccessor access = (FontStorageAccessor) fontStorage;
				access.callCloseFonts();
				access.callCloseGlyphAtlases();
				access.getGlyphRendererCache().clear();
				access.getGlyphCache().clear();
				access.getCharactersByWidth().clear();
				access.setBlankGlyphRenderer(BuiltinEmptyGlyph.MISSING.bake(access::callGetGlyphRenderer));
				access.setWhiteRectangleGlyphRenderer(BuiltinEmptyGlyph.WHITE.bake(access::callGetGlyphRenderer));

				access.getCharactersByWidth().putAll(entry.getKey());
				access.getFonts().addAll(entry.getValue());
				fontStorages.put(identifier, fontStorage);
			});

			profiler.pop();
			profiler.endTick();
			ci.cancel();
		});
	}

	@SuppressWarnings("UnresolvedMixinReference")
	@Inject(method = {"method_18635", "apply*"}, at = @At(value = "TAIL"))
	private void applyInject(Map<Identifier, List<Font>> map, ResourceManager resourceManager, Profiler profiler, CallbackInfo ci) {
		FontModule.DATA.visit(Cache.Status.SAVE, data -> {
			data.clear();
			final FontManagerAccessor fontManagerAccessor = (FontManagerAccessor) FontModule.FONTMANAGER;
			final Map<Identifier, FontStorage> fontStorages = fontManagerAccessor.getFontStorages();
			fontStorages.forEach((identifier, fontStorage) -> {
				var access = ((FontStorageAccessor) fontStorage);
				data.put(identifier, Pair.of(access.getCharactersByWidth(), access.getFonts()));
			});
		});
	}

	@Mixin(FontManager.class)
	private static class LeoFontSolution {
		@Inject(method = "<init>", at = @At(value = "TAIL"))
		private void initInject(TextureManager manager, CallbackInfo ci) {
			FontModule.FONTMANAGER = ((FontManager) (Object) this);
		}
	}

}
