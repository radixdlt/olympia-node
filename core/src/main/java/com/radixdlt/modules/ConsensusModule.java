/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.modules;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.NoVote;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ExponentialPacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.NextTxnsGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerReducer;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.utils.TimeSupplier;
import java.util.Comparator;
import java.util.Random;

/** Module responsible for running BFT validator logic */
public final class ConsensusModule extends AbstractModule {

  @Override
  public void configure() {
    bind(SafetyRules.class).in(Scopes.SINGLETON);
    bind(PacemakerState.class).in(Scopes.SINGLETON);
    bind(PacemakerReducer.class).to(PacemakerState.class);
    bind(ExponentialPacemakerTimeoutCalculator.class).in(Scopes.SINGLETON);
    bind(PacemakerTimeoutCalculator.class).to(ExponentialPacemakerTimeoutCalculator.class);
    bind(VertexStoreBFTSyncRequestProcessor.class).in(Scopes.SINGLETON);

    var eventBinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, LocalEvents.class)
            .permitDuplicates();
    eventBinder.addBinding().toInstance(ViewUpdate.class);
    eventBinder.addBinding().toInstance(BFTRebuildUpdate.class);
    eventBinder.addBinding().toInstance(BFTInsertUpdate.class);
    eventBinder.addBinding().toInstance(Proposal.class);
    eventBinder.addBinding().toInstance(Vote.class);
  }

  @Provides
  private BFTFactory bftFactory(
      Hasher hasher,
      HashVerifier verifier,
      EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher,
      EventDispatcher<NoVote> noVoteEventDispatcher,
      RemoteEventDispatcher<Vote> voteDispatcher) {
    return (self,
        pacemaker,
        vertexStore,
        bftSyncer,
        viewQuorumReachedEventProcessor,
        validatorSet,
        viewUpdate,
        safetyRules) ->
        BFTBuilder.create()
            .self(self)
            .hasher(hasher)
            .verifier(verifier)
            .voteDispatcher(voteDispatcher)
            .safetyRules(safetyRules)
            .pacemaker(pacemaker)
            .vertexStore(vertexStore)
            .viewQuorumReachedEventDispatcher(
                viewQuorumReached -> {
                  // FIXME: a hack for now until replacement of epochmanager factories
                  viewQuorumReachedEventProcessor.process(viewQuorumReached);
                  viewQuorumReachedEventDispatcher.dispatch(viewQuorumReached);
                })
            .noVoteEventDispatcher(noVoteEventDispatcher)
            .viewUpdate(viewUpdate)
            .bftSyncer(bftSyncer)
            .validatorSet(validatorSet)
            .build();
  }

  @Provides
  @Singleton
  public ProposerElection proposerElection(BFTConfiguration configuration) {
    return configuration.getProposerElection();
  }

  @Provides
  @Singleton
  public BFTEventProcessor eventProcessor(
      @Self BFTNode self,
      BFTConfiguration config,
      BFTFactory bftFactory,
      Pacemaker pacemaker,
      VertexStore vertexStore,
      BFTSync bftSync,
      SafetyRules safetyRules,
      ViewUpdate viewUpdate) {
    return bftFactory.create(
        self,
        pacemaker,
        vertexStore,
        bftSync,
        bftSync.viewQuorumReachedEventProcessor(),
        config.getValidatorSet(),
        viewUpdate,
        safetyRules);
  }

  @Provides
  @Singleton
  private Pacemaker pacemaker(
      @Self BFTNode self,
      SafetyRules safetyRules,
      SystemCounters counters,
      BFTConfiguration configuration,
      VertexStore vertexStore,
      EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher,
      ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender,
      PacemakerTimeoutCalculator timeoutCalculator,
      NextTxnsGenerator nextTxnsGenerator,
      Hasher hasher,
      RemoteEventDispatcher<Proposal> proposalDispatcher,
      RemoteEventDispatcher<Vote> voteDispatcher,
      TimeSupplier timeSupplier,
      ViewUpdate initialViewUpdate,
      SystemCounters systemCounters) {
    BFTValidatorSet validatorSet = configuration.getValidatorSet();
    return new Pacemaker(
        self,
        counters,
        validatorSet,
        vertexStore,
        safetyRules,
        timeoutDispatcher,
        timeoutSender,
        timeoutCalculator,
        nextTxnsGenerator,
        proposalDispatcher,
        voteDispatcher,
        hasher,
        timeSupplier,
        initialViewUpdate,
        systemCounters);
  }

  @Provides
  @Singleton
  private BFTSync bftSync(
      @Self BFTNode self,
      @GetVerticesRequestRateLimit RateLimiter syncRequestRateLimiter,
      VertexStore vertexStore,
      PacemakerReducer pacemakerReducer,
      RemoteEventDispatcher<GetVerticesRequest> requestSender,
      EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
      ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
      @LastProof LedgerProof ledgerLastProof, // Use this instead of configuration.getRoot()
      Random random,
      @BFTSyncPatienceMillis int bftSyncPatienceMillis,
      Hasher hasher,
      SystemCounters counters) {
    return new BFTSync(
        self,
        syncRequestRateLimiter,
        vertexStore,
        hasher,
        pacemakerReducer,
        Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
        requestSender,
        syncLedgerRequestSender,
        timeoutDispatcher,
        ledgerLastProof,
        random,
        bftSyncPatienceMillis,
        counters);
  }

  @Provides
  @Singleton
  private VertexStore vertexStore(
      EventDispatcher<BFTInsertUpdate> updateSender,
      EventDispatcher<BFTRebuildUpdate> rebuildUpdateDispatcher,
      EventDispatcher<BFTHighQCUpdate> highQCUpdateEventDispatcher,
      EventDispatcher<BFTCommittedUpdate> committedSender,
      BFTConfiguration bftConfiguration,
      Ledger ledger,
      Hasher hasher) {
    return VertexStore.create(
        bftConfiguration.getVertexStoreState(),
        ledger,
        hasher,
        updateSender,
        rebuildUpdateDispatcher,
        highQCUpdateEventDispatcher,
        committedSender);
  }
}
