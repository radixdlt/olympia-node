/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.service;

import org.apache.commons.cli.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.network.transport.tcp.TCPConfiguration;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.sync.SyncConfig;

import java.util.List;
import java.util.TreeMap;

import static org.junit.Assert.fail;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class SystemConfigServiceTest {
	@Test
	public void testApiConversionToJson() {
		assertConversionIsFull("apiData", SystemConfigService.API_COUNTERS);
	}

	@Test
	public void testBftConversionToJson() {
		assertConversionIsFull("bftData", SystemConfigService.BFT_COUNTERS);
	}

	@Test
	public void testMempoolConversionToJson() {
		assertConversionIsFull("mempoolData", SystemConfigService.MEMPOOL_COUNTERS);
	}

	@Test
	public void testRadixEngineConversionToJson() {
		assertConversionIsFull("radixEngineData", SystemConfigService.RADIX_ENGINE_COUNTERS);
	}

	@Test
	public void testSyncConversionToJson() {
		assertConversionIsFull("syncData", SystemConfigService.SYNC_COUNTERS);
	}

	@Test
	public void testNetworkingConversionToJson() {
		assertConversionIsFull("networkingData", SystemConfigService.NETWORKING_COUNTERS);
	}

	@Test
	public void testPrepareBftConfiguration() {
		var result = SystemConfigService.prepareBftConfiguration(0, 0);
		printSchema("bftConfiguration", result);
	}

	@Test
	public void testPrepareRadixEngineConfiguration() {
		var map = new TreeMap<Long, ForkConfig>();
		var forkConfig = new ForkConfig("name", null, null, null, View.of(0));

		map.put(0L, forkConfig);

		var result = SystemConfigService.prepareRadixEngineConfiguration(map,0, 0, 0);
		printSchema("radixEngineConfiguration", result);
	}

	@Test
	public void testSyncConfiguration() {
		var config =	SyncConfig.of(0L, 0, 0L, 0, 0.0);

		printSchema("syncConfiguration", config.asJson());
	}

	@Test
	public void testNetworkingConfiguration() throws ParseException {
		var properties = new RuntimeProperties(jsonObject(), new String[] {});
		var config = TCPConfiguration.fromRuntimeProperties(properties);

		printSchema("syncConfiguration", config.asJson());
	}

	@Test
	public void testProofFormatting() {
		/*

		*/
		var input = "{\n"
			+ "  \"txn\": [\"020000010600000300010378726401020100045261647314526164697820426574616e657420546f6b656e731c68747470733a2f2f746f6b656e732e7261646978646c742e636f6d2f3468747470733a2f2f6173736574732e7261646978646c742e636f6d2f69636f6e732f69636f6e2d7872642d33327833322e706e6700010301040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402e493dbf1c10d80f3581e4904930b1404cc6c13900ee0758474fa94abe8c4cd13000000000000000000000000002cd76fe086b93ce2f768a00b22a000000000000001030104022f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010403d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe1000000000000000000000000002cd76fe086b93ce2f768a00b22a0000000000000020502bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b000000010502bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b01000000020503d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe1000000010503d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe10100000001040403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a146029755602bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b00000000000000000000000000000000000000000000d3c21bcecceda100000005000000100103010403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f694ddef53d3125f0000000001040403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a146029755603d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d3c21bcecceda1000000050000001e0103010403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f5c11bd3850624be00000000030003fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc06656d756e6965010203fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc02000000000000000000000000002cd76fe086b93ce2f768a00b22a000000000000d654d756e696520546f6b656e73000000010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000300037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b704636572620102037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b702000000000000000000000000002cd76fe086b93ce2f768a00b22a0000000000015436572627973205370656369616c20546f6b656e730000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f768a00b22a0000000000000030003263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160367756d010203263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c1602000000000000000000000000002cd76fe086b93ce2f768a00b22a000000000000847756d62616c6c73000000010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f768a00b22a0000000000000010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b00000000000000000000000000000000000000000000d3c21bcecceda10000000500000026010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f694ddef53d3125f000000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b00000000000000000000000000000000000000000000d3c21bcecceda1000000050000002a0103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f694ddef53d3125f00000000010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b00000000000000000000000000000000000000000000d3c21bcecceda1000000050000002e010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f694ddef53d3125f00000000010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d3c21bcecceda10000000500000032010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f5c11bd3850624be000000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d3c21bcecceda100000005000000360103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f5c11bd3850624be00000000010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d3c21bcecceda1000000050000003a010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f5c11bd3850624be0000000002010000000000000000000000000000000000000000000000000101000000000000000100000000000000000000016f5e66e80000\"],\n"
			+ "  \"proof\": {\n"
			+ "    \"opaque\": \"0000000000000000000000000000000000000000000000000000000000000000\",\n"
			+ "    \"sigs\": [{\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\",\"signature\":\"f723c8eac9d518b31f7d546cc3028119314bd1b98fe992d25f92990cf1d43d4910895a231179f33436195ab66b93c30f8b0db7f1e69c8d6936ffd04837bc4bc3\",\"timestamp\":1622726864803},{\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"signature\":\"bd71f8e552080190583e2e79a7e603bfe4cffdb27424cc02a04048d42d88fcdb6ed47855f8d09239830d82ea0818f5ddb137a75bc7ccc88a99d311793d94540d\",\"timestamp\":1622726864804}],\n"
			+ "    \"header\": {\n"
			+ "      \"view\": 0,\n"
			+ "      \"nextValidators\": [\n"
			+ "        {\n"
			+ "          \"stake\": \"1000000000000000000000000\",\n"
			+ "          \"address\": \"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\"\n"
			+ "        },\n"
			+ "        {\n"
			+ "          \"stake\": \"1000000000000000000000000\",\n"
			+ "          \"address\": \"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\"\n"
			+ "        }\n"
			+ "      ],\n"
			+ "      \"epoch\": 0,\n"
			+ "      \"accumulator\": \"91ca9eb2613eee8a2a7dcafb15578b7f032fc1d82979bf83d0ed9a6243275670\",\n"
			+ "      \"version\": 0,\n"
			+ "      \"timestamp\": 1577836800000\n"
			+ "    }\n"
			+ "  }\n"
			+ "}\n";

		printSchema("ledgerProof", new JSONObject(input));
	}

	private static void assertConversionIsFull(String name, List<CounterType> counterTypes) {
		var systemCounters = new SystemCountersImpl();
		var result = SystemConfigService.countersToJson(systemCounters, counterTypes);

		counterTypes.forEach(counterType -> assertPathExists(result, counterType.jsonPath()));

		printSchema(name, result);
	}

	private static void printSchema(String name, JSONObject output) {
		System.out.println("\"result\": {\n"
							   + "    \"name\": \"" + name + "Result\",\n"
							   + "    \"schema\": " + buildSchema(output).toString(4)
							   + "}");
	}

	private static JSONObject buildSchema(Object obj) {
		if (obj instanceof JSONObject) {
			return buildSchema((JSONObject) obj);
		}

		if (obj instanceof JSONArray) {
			return buildSchema((JSONArray) obj);
		}

		if (obj instanceof Number) {
			var type = (obj instanceof Float) | (obj instanceof Double) ? "number" : "integer";

			return jsonObject().put("type", type);
		}

		if (obj instanceof String) {
			return jsonObject().put("type", "string");
		}

		if (obj instanceof Boolean) {
			return jsonObject().put("type", "boolean");
		}

		throw new IllegalStateException("Unknown object: " + obj.toString());
	}

	private static JSONObject buildSchema(JSONObject obj) {
		var properties = jsonObject();
		var schema = jsonObject().put("type", "object").put("properties", properties);

		obj.keySet().forEach(key -> properties.put(key, buildSchema(obj.get(key))));

		return schema;
	}

	private static JSONObject buildSchema(JSONArray obj) {
		return jsonObject().put("type", "array").put("items", buildSchema(obj.get(0)));
	}

	private static void assertPathExists(JSONObject object, String path) {
		var iterator = List.of(path.split("\\.")).listIterator();
		var ptr = object;

		while (iterator.hasNext()) {
			var element = iterator.next();

			if (ptr.has(element)) {
				ptr = ptr.optJSONObject(element);

				if (ptr == null && iterator.hasNext()) {
					fail("Intermediate element " + element + " is missing");
				}
			} else {
				fail("Element " + element + " is missing");
			}
		}
	}
}