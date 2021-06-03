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

import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.counters.SystemCountersImpl;

import java.util.List;

import static org.junit.Assert.fail;

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

	private static void assertConversionIsFull(String name, List<CounterType> counterTypes) {
		var systemCounters = new SystemCountersImpl();
		var result = SystemConfigService.countersToJson(systemCounters, counterTypes);

		counterTypes.forEach(counterType -> assertPathExists(result, counterType.jsonPath()));
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
