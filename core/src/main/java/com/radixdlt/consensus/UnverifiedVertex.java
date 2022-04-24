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

package com.radixdlt.consensus;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.List;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/** Vertex in a Vertex graph */
@Immutable
@SerializerId2("consensus.vertex")
public final class UnverifiedVertex {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
  SerializerDummy serializer = SerializerDummy.DUMMY;

  @JsonProperty("qc")
  @DsonOutput(Output.ALL)
  private final QuorumCertificate qc;

  private final View view;

  @JsonProperty("txns")
  @DsonOutput(Output.ALL)
  private final List<byte[]> txns;

  @JsonProperty("tout")
  @DsonOutput(Output.ALL)
  private final Boolean proposerTimedOut;

  private final BFTNode proposer;

  private UnverifiedVertex(
      QuorumCertificate qc,
      View view,
      List<byte[]> txns,
      BFTNode proposer,
      Boolean proposerTimedOut) {
    this.qc = requireNonNull(qc);
    this.view = requireNonNull(view);

    if (proposerTimedOut != null && proposerTimedOut && !txns.isEmpty()) {
      throw new IllegalArgumentException("Txns must be empty if timeout");
    }

    if (txns != null) {
      txns.forEach(Objects::requireNonNull);
    }

    this.txns = txns;
    this.proposer = proposer;
    this.proposerTimedOut = proposerTimedOut;
  }

  @JsonCreator
  public static UnverifiedVertex create(
      @JsonProperty(value = "qc", required = true) QuorumCertificate qc,
      @JsonProperty("view") long viewId,
      @JsonProperty("txns") List<byte[]> txns,
      @JsonProperty("p") byte[] proposer,
      @JsonProperty("tout") Boolean proposerTimedOut)
      throws PublicKeyException {
    return new UnverifiedVertex(
        qc,
        View.of(viewId),
        txns == null ? List.of() : txns,
        proposer != null ? BFTNode.fromPublicKeyBytes(proposer) : null,
        proposerTimedOut);
  }

  public static UnverifiedVertex createGenesis(LedgerHeader ledgerHeader) {
    BFTHeader header = BFTHeader.ofGenesisAncestor(ledgerHeader);
    final VoteData voteData = new VoteData(header, header, header);
    final QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
    return new UnverifiedVertex(qc, View.genesis(), null, null, false);
  }

  public static UnverifiedVertex createTimeout(QuorumCertificate qc, View view, BFTNode proposer) {
    return new UnverifiedVertex(qc, view, List.of(), proposer, true);
  }

  public static UnverifiedVertex create(
      QuorumCertificate qc, View view, List<Txn> txns, BFTNode proposer) {
    if (view.number() == 0) {
      throw new IllegalArgumentException("Only genesis can have view 0.");
    }

    var txnBytes = txns.stream().map(Txn::getPayload).toList();

    return new UnverifiedVertex(qc, view, txnBytes, proposer, false);
  }

  @JsonProperty("p")
  @DsonOutput(Output.ALL)
  private byte[] getProposerJson() {
    return proposer == null ? null : proposer.getKey().getCompressedBytes();
  }

  public BFTNode getProposer() {
    return proposer;
  }

  public boolean isTimeout() {
    return proposerTimedOut != null && proposerTimedOut;
  }

  public QuorumCertificate getQC() {
    return qc;
  }

  public View getView() {
    return view;
  }

  public List<Txn> getTxns() {
    return txns == null ? List.of() : txns.stream().map(Txn::create).toList();
  }

  @JsonProperty("view")
  @DsonOutput(Output.ALL)
  private Long getSerializerView() {
    return this.view == null ? null : this.view.number();
  }

  @Override
  public String toString() {
    return String.format("Vertex{view=%s, qc=%s, txns=%s}", view, qc, getTxns());
  }

  @Override
  public int hashCode() {
    return Objects.hash(qc, proposer, view, txns, proposerTimedOut);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof UnverifiedVertex)) {
      return false;
    }

    UnverifiedVertex v = (UnverifiedVertex) o;
    return Objects.equals(v.view, this.view)
        && Objects.equals(v.proposerTimedOut, this.proposerTimedOut)
        && Objects.equals(v.proposer, this.proposer)
        && Objects.equals(v.getTxns(), this.getTxns())
        && Objects.equals(v.qc, this.qc);
  }
}
