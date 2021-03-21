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

package com.radixdlt.application.faucet;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.chaos.mempoolfiller.InMemoryWallet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Faucet service which sends funds from this node to another address
 * when requested.
 */
public final class Faucet {
	private static final Logger log = LogManager.getLogger();

	private final RadixAddress self;
	private final RadixEngine<LedgerAtom, LedgerProof> radixEngine;
	private final HashSigner hashSigner;
	private final Serialization serialization;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final RRI nativeToken;
	private final UInt256 amount = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	@Inject
	public Faucet(
		@Self RadixAddress self,
		@Named("RadixEngine") HashSigner hashSigner,
		@NativeToken RRI nativeToken,
		Serialization serialization,
		RadixEngine<LedgerAtom, LedgerProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		this.self = self;
		this.hashSigner = hashSigner;
		this.nativeToken = nativeToken;
		this.serialization = serialization;
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
	}

	private void processRequest(FaucetRequest request) {
		log.info("Faucet Request {}", request);

		var wallet = radixEngine.getComputedState(InMemoryWallet.class);

		wallet.createTransaction(request.getAddress(), amount)
			.ifPresentOrElse(
				builder -> {
					log.info("Faucet sending tokens to {}", request.getAddress());

					var hashToSign = builder.computeHashToSign();
					builder.setSignature(self.euid(), hashSigner.sign(hashToSign));
					builder.message(String.format("Sent you %s %s", amount, nativeToken.getName()));
					var atom = builder.buildAtom();
					var payload = serialization.toDson(atom, DsonOutput.Output.ALL);
					var command = new Command(payload);
					this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));
					request.onSuccess(atom.getAID());
				},
				() -> {
					log.info("Faucet not enough funds to fulfill request {}", request);
					request.onFailure("Not enough funds in Faucet.");
				}
			);
	}

	public EventProcessor<FaucetRequest> requestEventProcessor() {
		return this::processRequest;
	}
}
