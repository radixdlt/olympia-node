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

package com.radixdlt;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.system.ValidatorBFTDataEvent;
import com.radixdlt.application.system.ValidatorMissedProposalsEvent;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.utils.Bytes;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public final class EventLoggerModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	@Provides
	Function<BFTNode, String> stringForNodes1(Addressing addressing) {
		return n -> addressing.forValidators().of(n.getKey()).substring(0, 11);
	}

	@Provides
	Function<ECPublicKey, String> stringForNodes2(Addressing addressing) {
		return n -> addressing.forValidators().of(n).substring(0, 11);
	}

	@ProvidesIntoSet
	EventProcessorOnDispatch<?> invalidProposedTxn(Function<ECPublicKey, String> nodeString) {
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		return new EventProcessorOnDispatch<>(
			InvalidProposedTxn.class,
			i -> {
				Level logLevel = logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
				logger.log(logLevel, "eng_badprp{proposer={}}", nodeString.apply(i.getProposer()));
			}
		);
	}

	@ProvidesIntoSet
	EventProcessorOnDispatch<?> logTimeouts(Function<BFTNode, String> nodeString) {
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		return new EventProcessorOnDispatch<>(
			EpochLocalTimeoutOccurrence.class,
			t -> {
				Level logLevel = logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
				logger.log(logLevel, "bft_timout{epoch={} round={} leader={} nextLeader={} count={}}",
					t.getEpochView().getEpoch(),
					t.getEpochView().getView().number(),
					nodeString.apply(t.getLeader()),
					nodeString.apply(t.getNextLeader()),
					t.getBase().timeout().count()
				);
			}
		);
	}

	@ProvidesIntoSet
	EventProcessorOnDispatch<?> logRounds(Function<BFTNode, String> nodeString) {
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		return new EventProcessorOnDispatch<>(
			EpochViewUpdate.class,
			u -> {
				Level logLevel = logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
				logger.log(logLevel, "bft_nxtrnd{epoch={} round={} leader={} nextLeader={}}",
					u.getEpoch(),
					u.getEpochView().getView().number(),
					nodeString.apply(u.getViewUpdate().getLeader()),
					nodeString.apply(u.getViewUpdate().getNextLeader())
				);
			}
		);
	}

	@ProvidesIntoSet
	@Singleton
	EventProcessorOnDispatch<?> ledgerUpdate(@Self BFTNode self, Function<ECPublicKey, String> nodeString) {
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		return new EventProcessorOnDispatch<>(
			LedgerUpdate.class,
			u -> {
				var output = u.getStateComputerOutput().getInstance(REOutput.class);
				var epochChange = u.getStateComputerOutput().getInstance(EpochChange.class);
				if (epochChange != null) {
					var validatorSet = epochChange.getBFTConfiguration().getValidatorSet();
					logger.info("lgr_nepoch{epoch={} included={} num_validators={} total_stake={}}",
						epochChange.getEpoch(),
						validatorSet.containsNode(self),
						validatorSet.getValidators().size(),
						Amount.ofSubunits(validatorSet.getTotalPower())
					);

				} else {
					long userTxns = output != null ? output.getProcessedTxns().stream().filter(t -> !t.isSystemOnly()).count() : 0;
					Level logLevel = logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
					logger.log(logLevel, "lgr_commit{epoch={} round={} version={} hash={} user_txns={}}",
						u.getTail().getEpoch(),
						u.getTail().getView().number(),
						u.getTail().getStateVersion(),
						Bytes.toHexString(u.getTail().getAccumulatorState().getAccumulatorHash().asBytes()).substring(0, 16),
						userTxns
					);
				}

				if (output == null) {
					return;
				}

				output.getProcessedTxns().stream().flatMap(t -> t.getEvents().stream())
					.forEach(e -> {
						if (e instanceof ValidatorBFTDataEvent) {
							var event = (ValidatorBFTDataEvent) e;
							Level logLevel = event.getMissedProposals() > 0 ? Level.WARN : Level.INFO;
							logger.log(logLevel, "vdr_epochr{validator={} completed_proposals={} missed_proposals={}}",
								nodeString.apply(event.getValidatorKey()),
								event.getCompletedProposals(),
								event.getMissedProposals()
							);
						} else if (e instanceof ValidatorMissedProposalsEvent) {
							var event = (ValidatorMissedProposalsEvent) e;
							var you = event.getValidatorKey().equals(self.getKey());
							Level logLevel = you ? Level.ERROR : Level.WARN;
							logger.log(logLevel, "{}_failed{validator={} missed_proposals={}}",
								you ? "you" : "vdr",
								nodeString.apply(event.getValidatorKey()),
								event.getMissedProposals()
							);
						}
					});
			}
		);
	}
}
