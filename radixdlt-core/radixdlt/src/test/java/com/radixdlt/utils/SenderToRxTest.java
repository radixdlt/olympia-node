/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.utils;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class SenderToRxTest {
	@Test
	public void when_send__sync_event__then_should_receive_it() {
		SenderToRx<Integer, Integer> senderToRx = new SenderToRx<>(i -> i + 1);
		TestObserver<Integer> testObserver = senderToRx.rx().test();
		senderToRx.send(1);
		testObserver.awaitCount(1);
		testObserver.assertValue(2);
		testObserver.assertNotComplete();
	}
}