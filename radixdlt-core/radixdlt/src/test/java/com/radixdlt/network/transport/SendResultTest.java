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

package com.radixdlt.network.transport;

import java.io.IOException;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class SendResultTest {

	@Test
	public void testComplete() {
		SendResult complete = SendResult.complete();

		assertThat(complete.toString()).contains("Complete");
		assertTrue(complete.isComplete());
	}

	@Test
	public void testFailure() {
		SendResult complete = SendResult.failure(new IOException());

		assertThat(complete.toString()).contains(IOException.class.getName());
		assertFalse(complete.isComplete());
	}

	@Test
	public void testGetException() {
		SendResult complete = SendResult.failure(new IOException());

		assertThat(complete.getThrowable()).isNotNull();
		assertThat(complete.getThrowable().getClass().getName()).isEqualTo(IOException.class.getName());
	}
}
