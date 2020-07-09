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

package com.radixdlt.engine;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.constraintmachine.DataPointer;
import org.junit.Test;

public class RadixEngineExceptionTest {
	@Test
	public void testGetters() {
		RadixEngineErrorCode code = mock(RadixEngineErrorCode.class);
		DataPointer dp = mock(DataPointer.class);
		String message = "Error message";
		RadixEngineAtom related = mock(RadixEngineAtom.class);
		RadixEngineException e = new RadixEngineException(code, message, dp, related);
		assertThat(e.getRelated()).isEqualTo(related);
		assertThat(e.getMessage()).isEqualTo(message);
		assertThat(e.getDataPointer()).isEqualTo(dp);
		assertThat(e.getErrorCode()).isEqualTo(code);
	}
}