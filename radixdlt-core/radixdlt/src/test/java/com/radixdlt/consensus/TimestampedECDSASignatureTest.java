/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

import org.junit.Test;
import org.radix.serialization.SerializeObject;

import com.radixdlt.crypto.ECDSASignature;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class TimestampedECDSASignatureTest extends SerializeObject<TimestampedECDSASignature> {
	public TimestampedECDSASignatureTest() {
		super(TimestampedECDSASignature.class, TimestampedECDSASignatureTest::create);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(TimestampedECDSASignature.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		assertThat(create().toString()).contains(TimestampedECDSASignature.class.getSimpleName());
	}

	private static TimestampedECDSASignature create() {
		return TimestampedECDSASignature.from(1L, ECDSASignature.zeroSignature());
	}
}