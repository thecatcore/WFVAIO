package fr.catcore.wfvaio;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class WhichFabricVariantAmIOn implements ModInitializer {

	private static final Version MC_VERSION = FabricLoader.getInstance().getModContainer("minecraft")
			.get().getMetadata().getVersion();
	private static FabricVariants VARIANT = null;
	private static final Map<String, Object> attributes = new HashMap<>();
	private static final String MAPPINGS_PATH = "mappings/mappings.tiny";
	private static final String META_INF_PATH = "META-INF/MANIFEST.MF";
	private static final String LF_INTERMEDIARY_KEY = "Intermediary-Version";
	private static final String ORNITHE_INTERMEDIARY_KEY = "Calanus-Generation";

	private static final String FIRST_1_15_SNAPSHOT = "1.15-alpha.19.34.a";
	private static final String FIRST_OFFICIAL = "1.14-alpha.18.43.b";
	private static final String FIRST_MERGED = "1.3";
	private static final String BABRIC = "1.0.0-beta.7.3";

	public static FabricVariants getVariant() {
		if (VARIANT != null) return VARIANT;

        try {
            VARIANT = computeVariant();
        } catch (Exception e) {
            e.printStackTrace();
			VARIANT = FabricVariants.UNKNOWN;
        }

        return VARIANT;
	}

	private static FabricVariants computeVariant() throws VersionParsingException, IOException {
		if (VersionPredicate.parse(">=" + FIRST_1_15_SNAPSHOT).test(MC_VERSION)) {
			return FabricVariants.OFFICIAL;
		}

		MemoryMappingTree mappingTree = getMappingsContent();

		if (isOrnithe(mappingTree)) return differentiateOrnitheVersions();

		if (VersionPredicate.parse(">=" + FIRST_OFFICIAL).test(MC_VERSION)) {
			return FabricVariants.OFFICIAL;
		}

		if (VersionPredicate.parse(">=" + FIRST_MERGED).test(MC_VERSION)) {
			return differentiateLFVersions();
		}

		if (VersionPredicate.parse(BABRIC).test(MC_VERSION)) {
			return differentiateBabricFormats(mappingTree);
		}

		return FabricVariants.UNKNOWN;
	}

	private static boolean isOrnithe(MemoryMappingTree mappingTree) {
		for (MappingTree.ClassMapping mapping : mappingTree.getClasses()) {
			String className = mapping.getName("intermediary");
			if (className != null && className.startsWith("net/minecraft/unmapped/")) {
				return true;
			}
		}

		return false;
	}

	private static FabricVariants differentiateLFVersions() {
		if (attributes.containsKey(LF_INTERMEDIARY_KEY)) {
			String value = attributes.get(LF_INTERMEDIARY_KEY).toString();

			if ("2".equals(value)) return FabricVariants.LEGACY_FABRIC_V2;
		}

		return FabricVariants.LEGACY_FABRIC_V1;
	}

	private static FabricVariants differentiateOrnitheVersions() {
		if (attributes.containsKey(ORNITHE_INTERMEDIARY_KEY)) {
			String value = attributes.get(ORNITHE_INTERMEDIARY_KEY).toString();

			if ("2".equals(value)) return FabricVariants.ORNITHE_V2;
		}

		return FabricVariants.ORNITHE_V1;
	}

	private static FabricVariants differentiateBabricFormats(MemoryMappingTree mappingTree) {
		return mappingTree.getDstNamespaces().contains("glue") ? FabricVariants.BABRIC : FabricVariants.BABRIC_NEW_FORMAT;
	}

	private static MemoryMappingTree getMappingsContent() throws IOException {
		URL mappingsUrl = WhichFabricVariantAmIOn.class.getClassLoader().getResource(MAPPINGS_PATH);

		URLConnection connection = mappingsUrl.openConnection();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			// We will only ever need to read tiny here
			// so to strip the other formats from the included copy of mapping IO, don't use MappingReader.read()
			reader.mark(4096);
			final MappingFormat format = MappingReader.detectFormat(reader);
			reader.reset();

			switch (format) {
				case TINY_FILE:
					Tiny1FileReader.read(reader, mappingTree);
					break;
				case TINY_2_FILE:
					Tiny2FileReader.read(reader, mappingTree);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported mapping format: " + format);
			}

			readMappingsAttributes(mappingsUrl);

			return mappingTree;
		}
	}

	private static void readMappingsAttributes(URL mappingsUrl) {
		String str = mappingsUrl.toString();

		try {
			URL metaInfUrl = new URL(str.substring(0, str.length() - MAPPINGS_PATH.length()) + META_INF_PATH);
			Manifest manifest = new Manifest(metaInfUrl.openStream());
			Attributes attributesObject = manifest.getMainAttributes();

			for (Map.Entry<Object, Object> entry : attributesObject.entrySet()) {
				attributes.put(entry.getKey().toString(), entry.getValue());
			}
		} catch (IOException ignored) {}
	}

	@Override
	public void onInitialize() {
		System.out.println("Current Fabric Variant: " + getVariant());
	}
}