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

package com.radixdlt.application;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.SubstateCacheRegister;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

/**
 * Module which manages different applications a node can run with
 * it's node key.
 */
public final class NodeWalletModule extends AbstractModule {
	@ProvidesIntoSet
	private SubstateCacheRegister<?> registeredSubstate(
		@NativeToken RRI tokenRRI,
		@Self RadixAddress self
	) {
		return new SubstateCacheRegister<>(
			TransferrableTokensParticle.class,
			p -> p.getAddress().equals(self) && p.getTokDefRef().equals(tokenRRI)
		);
	}
}
