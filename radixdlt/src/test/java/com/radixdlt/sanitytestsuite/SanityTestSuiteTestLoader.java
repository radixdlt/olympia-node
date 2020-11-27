package com.radixdlt.sanitytestsuite;

import com.google.common.hash.HashCode;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.utils.JSONFormatter;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public class SanityTestSuiteTestLoader {

	public static Gson gson = new GsonBuilder()
			.registerTypeAdapter(Double.class, new SanityTestSuiteTestLoader.DoubleSerializer())
			.create();

	public static class DoubleSerializer implements JsonSerializer<Double> {
		@Override
		public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
			return src == src.longValue() ? new JsonPrimitive(src.longValue()) : new JsonPrimitive(src);
		}
	}

	public SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {

		JsonReader reader = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(sanityTestJSONFileName).getFile());

			// Compare saved hash in file with calculated hash of test.
			String jsonFileContent = Files.asCharSource(file, StandardCharsets.UTF_8).read();
			JSONObject sanityTestSuiteRootAsJsonObject = new JSONObject(jsonFileContent);
			String sanityTestSuiteSavedHash = sanityTestSuiteRootAsJsonObject.getString("hashOfSuite");
			JSONObject sanityTestSuiteAsJsonObject = sanityTestSuiteRootAsJsonObject.getJSONObject("suite");
			String sanityTestSuiteAsJsonStringPretty = JSONFormatter.sortPrettyPrintJSONString(sanityTestSuiteAsJsonObject.toString(4));
			Hasher hasher = Sha256Hasher.withDefaultSerialization();
			HashCode calculatedHashOfSanityTestSuite = hasher.hash(sanityTestSuiteAsJsonStringPretty);
			assertEquals(sanityTestSuiteSavedHash, calculatedHashOfSanityTestSuite.toString());

			FileReader fileReader = new FileReader(file);
			reader = new JsonReader(fileReader);
		} catch (Exception e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}

		SanityTestSuiteRoot sanityTestSuiteRoot = gson.fromJson(reader, SanityTestSuiteRoot.class);

		return sanityTestSuiteRoot;

	}
}

// CHECKSTYLE:ON checkstyle:VisibilityModifier
