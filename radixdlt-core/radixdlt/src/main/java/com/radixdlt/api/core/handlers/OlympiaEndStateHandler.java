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

package com.radixdlt.api.core.handlers;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.radixdlt.api.core.CoreJsonRpcHandler;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.OlympiaEndStateNotReadyResponse;
import com.radixdlt.api.core.openapitools.model.OlympiaEndStateReadyResponse;
import com.radixdlt.api.core.openapitools.model.OlympiaEndStateRequest;
import com.radixdlt.api.core.openapitools.model.OlympiaEndStateResponse;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.hotstuff.HashSigner;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.stateir.OlympiaStateIRSerializer;
import com.radixdlt.stateir.StateIRConstructor;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Compress;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class OlympiaEndStateHandler
    extends CoreJsonRpcHandler<OlympiaEndStateRequest, OlympiaEndStateResponse> {
  private static final Logger log = LogManager.getLogger();

  // The size of a test payload that's used (on the Babylon side) to check that:
  // a) large response can be successfully received
  // b) node public key matches configuration (signature verification)
  private static final int TEST_PAYLOAD_SIZE = 100 * 1024 * 1024; // 100 MiB

  private final Object endStateLock = new Object();

  private final CoreModelMapper coreModelMapper;
  private final RadixEngine<LedgerAndBFTProof> radixEngine;
  private final EngineStore<LedgerAndBFTProof> engineStore;
  private final CurrentForkView currentForkView;
  private final Hasher hasher;
  private final HashSigner hashSigner;

  private Optional<OlympiaEndStateResponse> cachedEndStateReadyResponse = Optional.empty();

  @Inject
  OlympiaEndStateHandler(
      CoreModelMapper coreModelMapper,
      RadixEngine<LedgerAndBFTProof> radixEngine,
      EngineStore<LedgerAndBFTProof> engineStore,
      CurrentForkView currentForkView,
      Hasher hasher,
      HashSigner hashSigner) {
    super(OlympiaEndStateRequest.class);
    this.coreModelMapper = Objects.requireNonNull(coreModelMapper);
    this.radixEngine = Objects.requireNonNull(radixEngine);
    this.engineStore = Objects.requireNonNull(engineStore);
    this.currentForkView = Objects.requireNonNull(currentForkView);
    this.hasher = Objects.requireNonNull(hasher);
    this.hashSigner = Objects.requireNonNull(hashSigner);
  }

  @Override
  public OlympiaEndStateResponse handleRequest(OlympiaEndStateRequest request)
      throws CoreApiException {
    coreModelMapper.verifyNetwork(request.getNetworkIdentifier());

    if (!radixEngine.isShutDown()) {
      return createNotReadyResponse(request);
    }

    synchronized (endStateLock) {
      try {
        if (this.cachedEndStateReadyResponse.isEmpty()) {
          prepareEndStateResponseAndSaveToCache();
        }
        return this.cachedEndStateReadyResponse.orElseThrow();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void prepareEndStateResponseAndSaveToCache() throws IOException {
    final var stopwatch = Stopwatch.createStarted();
    log.info("Olympia end state preparation started (this may take a few minutes)...");
    final var endStateBytes = prepareEndState();
    final var hash = hasher.hashBytes(endStateBytes);
    final var signature = hashSigner.sign(hash);
    log.info("Olympia end state prepared in {} s", stopwatch.elapsed(TimeUnit.SECONDS));
    final var response =
        new OlympiaEndStateReadyResponse()
            .hash(Bytes.toHexString(hash.asBytes()))
            .signature(signature.toHexString())
            .contents(Bytes.toBase64String(endStateBytes))
            .status(OlympiaEndStateResponse.StatusEnum.READY);
    this.cachedEndStateReadyResponse = Optional.of(response);
  }

  private OlympiaEndStateResponse createNotReadyResponse(OlympiaEndStateRequest request) {
    final var includeTestPayload =
        Optional.ofNullable(request.getIncludeTestPayload()).orElse(false);
    if (includeTestPayload) {
      // We're writing it as hex, so the size will double
      final var testPayload = new byte[TEST_PAYLOAD_SIZE / 2];
      // Just setting some bytes so that it's not all zeros
      testPayload[0] = 0x01;
      testPayload[testPayload.length - 1] = (byte) 0xff;
      final var testPayloadHash = hasher.hashBytes(testPayload);
      final var signature = hashSigner.sign(testPayloadHash);
      return new OlympiaEndStateNotReadyResponse()
          .testPayload(Hex.toHexString(testPayload))
          .testPayloadHash(Hex.toHexString(testPayloadHash.asBytes()))
          .signature(signature.toHexString())
          .status(OlympiaEndStateResponse.StatusEnum.NOT_READY);
    } else {
      return new OlympiaEndStateNotReadyResponse()
          .status(OlympiaEndStateResponse.StatusEnum.NOT_READY);
    }
  }

  private byte[] prepareEndState() throws IOException {
    final var substateDeserialization =
        currentForkView.currentForkConfig().engineRules().parser().getSubstateDeserialization();
    final var state =
        new StateIRConstructor(engineStore, substateDeserialization).prepareOlympiaStateIR();
    final var serialized = new OlympiaStateIRSerializer().serialize(state);
    return Compress.compress(serialized);
  }
}
