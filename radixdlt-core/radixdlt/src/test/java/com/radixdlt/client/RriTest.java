/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.client;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RriTest {
	private final BiMap<Pair<String, String>, String> reAddressToRri = HashBiMap.create(
		Map.of(
			Pair.of("xrd", "01"), "xrd_rb1qya85pwq",
			Pair.of("xrd2", "01"), "xrd2_rb1qy557l44",
			Pair.of("usdc", "03" + "00".repeat(26)), "usdc_rb1qvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6gwwwd",
			Pair.of("t2t2t2", "03" + "03".repeat(26)), "t2t2t2_rb1qvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsmr9n0r"
		)
	);

	private final Map<String, String> invalidRris = Map.of(
		"xrd1pzdsczc", "no _rb suffix",
		"xrd_rb1avu205I", "invalid address type (0)",
		"usdc_rb1qg8vs72e", "invalid address type (2)",
		"usdc_rb1qqqsqs6ztc", "invalid length for address type 1",
		"usdc_rb1qvgxjc9r", "invalid length for address type 3",
		"xrd_2_rb1qvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpszyaqyw", "invalid characters in hrp"
	);

	@Test
	public void test_rri_serialization() {
		reAddressToRri.forEach((pair, expected) -> {
			var reAddr = REAddr.of(Bytes.fromHexString(pair.getSecond()));
			var rri = Rri.of(pair.getFirst(), reAddr);
			assertThat(expected).isEqualTo(rri);
		});
	}

	@Test
	public void test_rri_deserialization() {
		reAddressToRri.forEach((expected, rri) -> {
			var pair = Rri.parse(rri);
			var expectedAddr = REAddr.of(Bytes.fromHexString(expected.getSecond()));
			assertThat(expected.getFirst()).isEqualTo(pair.getFirst());
			assertThat(expectedAddr).isEqualTo(pair.getSecond());
		});
	}

	@Test
	public void test_invalid_rris() {
		for (var e : invalidRris.entrySet()) {
			assertThatThrownBy(() -> Rri.parse(e.getKey()), e.getValue()).isInstanceOf(IllegalArgumentException.class);
		}
	}
}