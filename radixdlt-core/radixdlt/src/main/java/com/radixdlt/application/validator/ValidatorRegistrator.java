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

package com.radixdlt.application.validator;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.chaos.mempoolfiller.InMemoryWallet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Registers/Unregisters self as a validator by submitting request
 * to the mempool.
 */
public final class ValidatorRegistrator {
	private static final Logger logger = LogManager.getLogger();
	private final RadixEngine<LedgerAtom> radixEngine;
	private final RadixAddress self;
	private final HashSigner hashSigner;
	private final Serialization serialization;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

	// TODO: do something better than this hack
	@Inject(optional = true)
	private FeeTable feeTable;

	@Inject
	public ValidatorRegistrator(
		@Self RadixAddress self,
		@Named("RadixEngine") HashSigner hashSigner,
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		this.self = self;
		this.hashSigner = hashSigner;
		this.serialization = serialization;
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
	}

	public EventProcessor<ValidatorRegistration> validatorRegistrationEventProcessor() {
		return this::process;
	}

	private void process(ValidatorRegistration registration) {
		var validatorState = radixEngine.getComputedState(ValidatorState.class);
		if (registration.isRegister() == validatorState.isRegistered()) {
			logger.warn("Node is already {}", registration.isRegister() ? "registered." : "unregistered.");
			return;
		}

		var builder = Atom.newBuilder();
		var validatorUpdate = validatorState.map(
			nonce -> {
				var pgBuilder = ParticleGroup.builder();
				var unregisterParticle = new UnregisteredValidatorParticle(self, nonce);
				if (nonce == 0) {
					pgBuilder.virtualSpinDown(unregisterParticle);
				} else {
					pgBuilder.spinDown(unregisterParticle);
				}
				return pgBuilder.spinUp(new RegisteredValidatorParticle(self, ImmutableSet.of(), nonce + 1)).build();
			},
			r -> ParticleGroup.builder()
				.spinDown(r)
				.spinUp(new UnregisteredValidatorParticle(self, r.getNonce() + 1))
				.build()
		);
		builder.addParticleGroup(validatorUpdate);

		if (feeTable != null) {
			InMemoryWallet wallet = radixEngine.getComputedState(InMemoryWallet.class);
			Optional<ParticleGroup> feeGroup = wallet.createFeeGroup();
			if (feeGroup.isEmpty()) {
				BigDecimal balance = wallet.getBalance();
				logger.warn("Cannot {} since balance too low: {}",
					registration.isRegister() ? "register" : "unregister",
					balance
				);
				return;
			}
			builder.addParticleGroup(feeGroup.get());
		}

		logger.info("Validator submitting {}.", registration.isRegister() ? "register" : "unregister");

		var hashToSign = builder.computeHashToSign();
		builder.setSignature(self.euid(), hashSigner.sign(hashToSign));
		var atom = builder.buildAtom();
		var payload = serialization.toDson(atom, DsonOutput.Output.ALL);
		var command = new Command(payload);
		this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));
	}
}
