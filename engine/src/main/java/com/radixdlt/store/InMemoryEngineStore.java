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

package com.radixdlt.store;

import com.google.common.primitives.UnsignedBytes;
import com.google.inject.Inject;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class InMemoryEngineStore<M> implements EngineStore<M> {
  public static final class Store<M> {
    private final Map<SubstateId, REStateUpdate> storedState = new HashMap<>();
    private final Map<REAddr, Supplier<ByteBuffer>> resources = new HashMap<>();
    private final Map<SystemMapKey, RawSubstateBytes> maps = new HashMap<>();
    private M metadata = null;
  }

  private final Object lock = new Object();
  private final Store<M> store;

  @Inject
  public InMemoryEngineStore(Store<M> store) {
    this.store = store;
  }

  public InMemoryEngineStore() {
    this.store = new Store<>();
  }

  @Override
  public <R> R transaction(TransactionEngineStoreConsumer<M, R> consumer)
      throws RadixEngineException {
    return consumer.start(
        new EngineStoreInTransaction<>() {
          @Override
          public void storeTxn(REProcessedTxn txn) {
            synchronized (lock) {
              txn.stateUpdates()
                  .forEach(
                      update -> {
                        store.storedState.put(update.getId(), update);

                        // FIXME: Superhack
                        if (update.isBootUp()) {
                          if (update.getParsed() instanceof TokenResource) {
                            var tokenDef = (TokenResource) update.getParsed();
                            store.resources.put(tokenDef.addr(), update::getStateBuf);
                          } else if (update.getParsed() instanceof VirtualParent) {
                            var p = (VirtualParent) update.getParsed();
                            var typeByte = p.data()[0];
                            var mapKey = SystemMapKey.ofSystem(typeByte);
                            store.maps.put(mapKey, update.getRawSubstateBytes());
                          } else if (update.getParsed() instanceof ValidatorData) {
                            var data = (ValidatorData) update.getParsed();
                            var mapKey =
                                SystemMapKey.ofSystem(
                                    update.typeByte(), data.validatorKey().getCompressedBytes());
                            store.maps.put(mapKey, update.getRawSubstateBytes());
                          } else if (update.getParsed() instanceof SystemData) {
                            var mapKey = SystemMapKey.ofSystem(update.typeByte());
                            store.maps.put(mapKey, update.getRawSubstateBytes());
                          }
                        } else if (update.isShutDown()) {
                          if (update.getParsed() instanceof ValidatorData) {
                            var data = (ValidatorData) update.getParsed();
                            var mapKey =
                                SystemMapKey.ofSystem(
                                    update.typeByte(), data.validatorKey().getCompressedBytes());
                            store.maps.remove(mapKey);
                          } else if (update.getParsed() instanceof SystemData) {
                            var mapKey = SystemMapKey.ofSystem(update.typeByte());
                            store.maps.remove(mapKey);
                          }
                        }
                      });
            }
          }

          @Override
          public void storeMetadata(M metadata) {
            store.metadata = metadata;
          }

          @Override
          public ByteBuffer verifyVirtualSubstate(SubstateId substateId)
              throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist {
            synchronized (lock) {
              var parent = substateId.getVirtualParent().orElseThrow();
              var update = store.storedState.get(parent);
              if (update == null || !(update.getParsed() instanceof VirtualParent)) {
                throw new VirtualParentStateDoesNotExist(parent);
              }

              var inst = store.storedState.get(substateId);
              if (inst != null && inst.isShutDown()) {
                throw new VirtualSubstateAlreadyDownException(substateId);
              }

              return update.getStateBuf();
            }
          }

          @Override
          public Optional<ByteBuffer> loadSubstate(SubstateId substateId) {
            synchronized (lock) {
              var inst = store.storedState.get(substateId);
              if (inst == null || !inst.isBootUp()) {
                return Optional.empty();
              }

              return Optional.of(inst.getStateBuf());
            }
          }

          @Override
          public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
            return InMemoryEngineStore.this.openIndexedCursor(index);
          }

          @Override
          public Optional<ByteBuffer> loadResource(REAddr addr) {
            synchronized (lock) {
              var supplier = store.resources.get(addr);
              return supplier == null ? Optional.empty() : Optional.of(supplier.get());
            }
          }
        });
  }

  @Override
  public M getMetadata() {
    return store.metadata;
  }

  @Override
  public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
    final List<RawSubstateBytes> substates = new ArrayList<>();
    synchronized (lock) {
      for (var i : store.storedState.values()) {
        if (!i.isBootUp()) {
          continue;
        }
        if (!index.test(i.getRawSubstateBytes())) {
          continue;
        }
        substates.add(i.getRawSubstateBytes());
      }
    }
    substates.sort(
        Comparator.comparing(
            RawSubstateBytes::getData, UnsignedBytes.lexicographicalComparator().reversed()));

    return CloseableCursor.wrapIterator(substates.iterator());
  }

  @Override
  public Optional<RawSubstateBytes> get(SystemMapKey key) {
    return Optional.ofNullable(store.maps.get(key));
  }

  public boolean contains(SubstateId substateId) {
    synchronized (lock) {
      var inst = store.storedState.get(substateId);
      return inst != null;
    }
  }

  public Store<M> getStore() {
    return store;
  }
}
