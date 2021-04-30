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

package com.radixdlt.network.p2p.test;

import com.google.inject.Injector;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.RadixNodeUri;

final class TestNode {
	Injector injector;
	RadixNodeUri uri;
	ECKeyPair keyPair;

	TestNode(Injector injector, RadixNodeUri uri, ECKeyPair keyPair) {
		this.injector = injector;
		this.uri = uri;
		this.keyPair = keyPair;
	}
}
