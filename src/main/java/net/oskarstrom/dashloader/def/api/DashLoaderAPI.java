package net.oskarstrom.dashloader.def.api;

import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.oskarstrom.dashloader.core.Dashable;
import net.oskarstrom.dashloader.core.registry.DashRegistry;
import net.oskarstrom.dashloader.core.registry.RegistryStorage;
import net.oskarstrom.dashloader.core.registry.RegistryStorageFactory;
import net.oskarstrom.dashloader.def.DashLoader;
import net.oskarstrom.dashloader.def.blockstate.DashBlockState;
import net.oskarstrom.dashloader.def.blockstate.property.DashBooleanProperty;
import net.oskarstrom.dashloader.def.blockstate.property.DashDirectionProperty;
import net.oskarstrom.dashloader.def.blockstate.property.DashEnumProperty;
import net.oskarstrom.dashloader.def.blockstate.property.DashIntProperty;
import net.oskarstrom.dashloader.def.blockstate.property.value.DashBooleanValue;
import net.oskarstrom.dashloader.def.blockstate.property.value.DashDirectionValue;
import net.oskarstrom.dashloader.def.blockstate.property.value.DashEnumValue;
import net.oskarstrom.dashloader.def.blockstate.property.value.DashIntValue;
import net.oskarstrom.dashloader.def.common.DashIdentifier;
import net.oskarstrom.dashloader.def.common.DashIdentifierInterface;
import net.oskarstrom.dashloader.def.common.DashModelIdentifier;
import net.oskarstrom.dashloader.def.data.DashSerializers;
import net.oskarstrom.dashloader.def.font.DashBitmapFont;
import net.oskarstrom.dashloader.def.font.DashBlankFont;
import net.oskarstrom.dashloader.def.font.DashTrueTypeFont;
import net.oskarstrom.dashloader.def.font.DashUnicodeFont;
import net.oskarstrom.dashloader.def.image.DashImage;
import net.oskarstrom.dashloader.def.image.DashSprite;
import net.oskarstrom.dashloader.def.model.DashBasicBakedModel;
import net.oskarstrom.dashloader.def.model.DashBuiltinBakedModel;
import net.oskarstrom.dashloader.def.model.DashMultipartBakedModel;
import net.oskarstrom.dashloader.def.model.DashWeightedBakedModel;
import net.oskarstrom.dashloader.def.model.components.DashBakedQuad;
import net.oskarstrom.dashloader.def.model.predicates.DashAndPredicate;
import net.oskarstrom.dashloader.def.model.predicates.DashOrPredicate;
import net.oskarstrom.dashloader.def.model.predicates.DashSimplePredicate;
import net.oskarstrom.dashloader.def.model.predicates.DashStaticPredicate;
import net.oskarstrom.dashloader.def.registry.PropertyValueRegistryStorage;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public class DashLoaderAPI {
	public static final Logger LOGGER = LogManager.getLogger();
	public final Map<DashDataType, MappingMap<?, ?>> mappings;
	public final Object2ByteMap<DashDataType> storageMappings;
	public final List<DashDataClass> dataClasses;
	private final DashLoader manager;
	private boolean initialized = false;
	private boolean failed = false;

	public DashLoaderAPI(DashLoader manager) {
		this.manager = manager;
		storageMappings = Object2ByteMaps.synchronize(new Object2ByteOpenHashMap<>());
		mappings = Collections.synchronizedMap(new HashMap<>());
		dataClasses = Collections.synchronizedList(new ArrayList<>());
	}


	private void clearAPI() {
		mappings.clear();
		storageMappings.clear();
		dataClasses.clear();
	}

	private void addType(DashDataType type, Class<?> dashClass) {
		manager.getSerializerManager().addSubclass(type.internalName, dashClass);
	}

	private <F, D extends Dashable<F>> void addFactoryToType(DashDataType type, Class<F> targetClass, Class<D> dashClass) {
		addType(type, dashClass);
		//noinspection unchecked
		final MappingMap<F, D> mappingMap = (MappingMap<F, D>) mappings.computeIfAbsent(type, type1 -> new MappingMap<>(new Object2ObjectOpenHashMap<>()));
		mappingMap.put(targetClass, dashClass);

		//add subclasses to the serializer
		DashLoader.getInstance().getSerializerManager().addSubclass(type.internalName, dashClass);
		LOGGER.info("Added custom DashObject: {} {}", type, dashClass.getSimpleName());
	}


	private void addDataObjectToType(DashDataType type, Class<?> dataClass) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
		addType(type, dataClass);
		dataClasses.add((DashDataClass) dataClass.getDeclaredConstructor().newInstance());
		LOGGER.info("Added custom DashDataObject: {}", dataClass.getSimpleName());
	}

	private DashDataType getTypeFromFactoryInterface(Class<?> closs) {
		for (DashDataType value : DashDataType.values()) {
			if (value.clazz == closs) {
				return value;
			}
		}
		LOGGER.error("Cannot find Factory Type from {} class parameter.", closs.getSimpleName());
		failed = true;
		return null;
	}

	public <F, D extends Dashable<F>> void registerDashObject(Class<D> dashClass) {
		final Class<?>[] interfaces = dashClass.getInterfaces();
		if (interfaces.length == 0) {
			LOGGER.error("No Interfaces found. Class: {}", dashClass.getSimpleName());
			failed = true;
			return;
		}
		final DashObject annotation = dashClass.getDeclaredAnnotation(DashObject.class);
		if (annotation == null) {
			LOGGER.error("Custom DashObject implementation does not have DashObject Annotation. Class: {}", dashClass.getSimpleName());
			failed = true;
			return;
		}
		DashDataType type = getTypeFromFactoryInterface(interfaces[0]);

		if (type == null) {
			LOGGER.error("Factory type could not be identified. Class: {}", dashClass.getSimpleName());
			failed = true;
			return;
		}
		if (type.requiresTargetObject) {
			if (annotation.value() == NullPointerException.class) {
				LOGGER.error("The type {} requires a target object in the @DashObject annotation", type.name);
				failed = true;
				return;
			}
		}
		if (type != DashDataType.DATA) {
			final Class<F> rawClass = (Class<F>) annotation.value();
			addFactoryToType(type, rawClass, dashClass);
		} else {
			try {
				addDataObjectToType(type, dashClass);
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
				failed = true;
			}
		}
	}


	private void initNativeAPI() {
		registerDashObject(DashBasicBakedModel.class);
		registerDashObject(DashBuiltinBakedModel.class);
		registerDashObject(DashMultipartBakedModel.class);
		registerDashObject(DashWeightedBakedModel.class);

		registerDashObject(DashAndPredicate.class);
		registerDashObject(DashOrPredicate.class);
		registerDashObject(DashSimplePredicate.class);
		registerDashObject(DashStaticPredicate.class); // no longer cursed

		registerDashObject(DashBooleanProperty.class);
		registerDashObject(DashDirectionProperty.class);
		registerDashObject(DashEnumProperty.class);
		registerDashObject(DashIntProperty.class);

		registerDashObject(DashBooleanValue.class);
		registerDashObject(DashDirectionValue.class);
		registerDashObject(DashEnumValue.class);
		registerDashObject(DashIntValue.class);

		registerDashObject(DashBitmapFont.class);
		registerDashObject(DashBlankFont.class);
		registerDashObject(DashTrueTypeFont.class);
		registerDashObject(DashUnicodeFont.class);
	}


	public void initAPI(DashRegistry registry) {
		if (!initialized) {
			Instant start = Instant.now();
			clearAPI();
			initNativeAPI();
			FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
				final ModMetadata metadata = modContainer.getMetadata();
				if (metadata.getCustomValues().size() != 0) {
					applyForClassesInValue(metadata, "dashloader:customobject", this::registerDashObject);
				}
			});

			if (failed)
				throw new RuntimeException("Failed to initialize the API");
			DashSerializers.initSerializers();
			addStorages(registry);
			LOGGER.info("[" + Duration.between(start, Instant.now()).toMillis() + "ms] Initialized api.");
			initialized = true;
		}
	}

	private void addStorages(DashRegistry registry) {


		// TODO add data
		// addRegistryStorage(DashDataType.DATA, identifiers);

		//stage 1 (basics)
		List<Map.Entry<Class<? extends Identifier>, Class<? extends DashIdentifierInterface>>> identifiers = new ArrayList<>();
		identifiers.add(Pair.of(Identifier.class, DashIdentifier.class));
		identifiers.add(Pair.of(ModelIdentifier.class, DashModelIdentifier.class));
		addMultiRegistryStorage(registry, DashDataType.IDENTIFIER, identifiers);

		//stage 1.5 (images)
		addSimpleRegistryStorage(registry, DashDataType.NATIVEIMAGE, NativeImage.class, DashImage.class);

		//stage 2 (blockstate dependencies)
		addMultiRegistryStorage(registry, DashDataType.PROPERTY);
		addPropertyValueRegistry(registry, DashDataType.PROPERTY_VALUE, mappings.get(DashDataType.PROPERTY_VALUE).entrySet());


		//stage 2.5 (blockstate)
		addSimpleRegistryStorage(registry, DashDataType.BLOCKSTATE, BlockState.class, DashBlockState.class);

		//stage 3 (model dependencies)
		addMultiRegistryStorage(registry, DashDataType.PREDICATE);
		final byte b = addSimpleRegistryStorage(registry, DashDataType.SPRITE, Sprite.class, DashSprite.class);
		registry.addMapping(MissingSprite.class,b);
		addSimpleRegistryStorage(registry, DashDataType.BAKEDQUAD, BakedQuad.class, DashBakedQuad.class);

		//stage 3.5 (models)
		addMultiRegistryStorage(registry, DashDataType.MODEL);


		///stage 4 fonts
		addMultiRegistryStorage(registry, DashDataType.FONT);


	}

	private <F, D extends Dashable<F>> void addMultiRegistryStorage(DashRegistry registry, DashDataType data) {
		addMultiRegistryStorage(registry, data,mappings.get(data).entrySet());
	}

	private <F, D extends Dashable<F>> void addMultiRegistryStorage(DashRegistry registry, DashDataType data, Collection<Map.Entry<Class<? extends F>, Class<? extends D>>> classes) {
		var multiRegistry = RegistryStorageFactory.createMultiRegistry(registry, classes);
		final byte storagePointer = addToRegistry(registry, data, multiRegistry);
		classes.forEach(entry -> registry.addMapping(entry.getKey(), storagePointer));
	}

	private <F, D extends Dashable<F>> void addPropertyValueRegistry(DashRegistry registry, DashDataType data, Collection<Map.Entry<Class<? extends F>, Class<? extends D>>> classes) {
		final byte storagePointer = addToRegistry(registry, data, PropertyValueRegistryStorage.create(registry, classes));
		classes.forEach(entry -> registry.addMapping(entry.getKey(), storagePointer));
	}

	private <F, D extends Dashable<F>> byte addSimpleRegistryStorage(DashRegistry registry, DashDataType data, Class<F> from, Class<D> to) {
		var simpleRegistry = RegistryStorageFactory.createSimpleRegistry(registry, from, to);
		final byte registryPointer = addToRegistry(registry, data, simpleRegistry);
		registry.addMapping(from, registryPointer);
		return registryPointer;
	}

	private byte addToRegistry(DashRegistry registry, DashDataType data, RegistryStorage<?> registryStorage) {
		//add registry storage to the registry
		final byte storagePointer = registry.addStorage(registryStorage);

		//save that registry for future use
		storageMappings.put(data, storagePointer);

		return storagePointer;
	}


	private <F, D extends Dashable<F>> void applyForClassesInValue(ModMetadata modMetadata, String valueName, Consumer<Class<D>> func) {
		CustomValue value = modMetadata.getCustomValue(valueName);
		if (value != null) {
			for (CustomValue customValue : value.getAsArray()) {
				final String dashObject = customValue.getAsString();
				try {
					final Class<D> closs = (Class<D>) Class.forName(dashObject);
					func.accept(closs);
				} catch (ClassNotFoundException e) {
					LOGGER.error("Class not found, Mod: \"{}\", Value: \"{}\"", modMetadata.getId(), customValue.getAsString());
					failed = true;
				}
			}
		}
	}

	public static class MappingMap<T, D extends Dashable<T>> extends AbstractObject2ObjectMap<Class<? extends T>, Class<? extends D>> {

		private final Object2ObjectMap<Class<? extends T>, Class<? extends D>> delegate;

		private MappingMap(Object2ObjectMap<Class<? extends T>, Class<? extends D>> delegate) {
			this.delegate = delegate;
		}

		@Override
		public int size() {
			return delegate.size();
		}

		@Override
		public ObjectSet<Entry<Class<? extends T>, Class<? extends D>>> object2ObjectEntrySet() {
			return delegate.object2ObjectEntrySet();
		}


		@Override
		public Class<? extends D> get(Object key) {
			return delegate.get(key);
		}

		@Override
		public Class<? extends D> put(Class<? extends T> key, Class<? extends D> value) {
			return delegate.put(key, value);

		}
	}


}