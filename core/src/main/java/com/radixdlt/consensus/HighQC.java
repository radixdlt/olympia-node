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
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/** Current state of synchronisation for sending node. */
@Immutable
@SerializerId2("consensus.high_qc")
public final class HighQC {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(Output.ALL)
  SerializerDummy serializer = SerializerDummy.DUMMY;

  @JsonProperty("highest_qc")
  @DsonOutput(Output.ALL)
  private final QuorumCertificate highestQC;

  @JsonProperty("committed_qc")
  @DsonOutput(Output.ALL)
  private final QuorumCertificate highestCommittedQC;

  @JsonProperty("highest_tc")
  @DsonOutput(Output.ALL)
  private final TimeoutCertificate highestTC;

  @JsonCreator
  @VisibleForTesting
  static HighQC serializerCreate(
      @JsonProperty(value = "highest_qc", required = true) QuorumCertificate highestQC,
      @JsonProperty("committed_qc") QuorumCertificate highestCommittedQC,
      @JsonProperty("highest_tc") TimeoutCertificate highestTC) {
    return new HighQC(highestQC, highestCommittedQC, highestTC);
  }

  private HighQC(
      QuorumCertificate highestQC,
      QuorumCertificate highestCommittedQC,
      TimeoutCertificate highestTC) {
    this.highestQC = requireNonNull(highestQC);
    // Don't include separate committedQC if it is the same as highQC.
    // This significantly reduces the serialised size of the object.
    if (highestCommittedQC == null || highestQC.equals(highestCommittedQC)) {
      this.highestCommittedQC = null;
    } else {
      this.highestCommittedQC = highestCommittedQC;
    }

    // only relevant if it's for a higher view than QC
    if (highestTC != null && highestTC.getView().gt(highestQC.getView())) {
      this.highestTC = highestTC;
    } else {
      this.highestTC = null;
    }
  }

  /**
   * Creates a {@link HighQC} from the a QC
   *
   * @param qc The qc
   * @return A new {@link HighQC}
   */
  public static HighQC from(QuorumCertificate qc) {
    return HighQC.from(qc, qc, Optional.empty());
  }

  /**
   * Creates a {@link HighQC} from the specified QCs.
   *
   * <p>Note that highestCommittedQC->committed needs to be an ancestor of highestQC->proposed, but
   * highestCommittedQC->proposed does not need to be an ancestor of highestQC->proposed.
   *
   * @param highestQC The highest QC we have seen
   * @param highestCommittedQC The highest QC we have committed
   * @param highestTC The highest timeout certificate
   * @return A new {@link HighQC}
   */
  public static HighQC from(
      QuorumCertificate highestQC,
      QuorumCertificate highestCommittedQC,
      Optional<TimeoutCertificate> highestTC) {
    return new HighQC(highestQC, highestCommittedQC, highestTC.orElse(null));
  }

  public Optional<TimeoutCertificate> highestTC() {
    return Optional.ofNullable(this.highestTC);
  }

  public QuorumCertificate highestQC() {
    return this.highestQC;
  }

  public View getHighestView() {
    if (this.highestTC != null && this.highestTC.getView().gt(this.highestQC.getView())) {
      return this.highestTC.getView();
    } else {
      return this.highestQC.getView();
    }
  }

  public QuorumCertificate highestCommittedQC() {
    return this.highestCommittedQC == null ? this.highestQC : this.highestCommittedQC;
  }

  @VisibleForTesting
  QuorumCertificate rawHighestCommittedQC() {
    return this.highestCommittedQC;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.highestQC, this.highestCommittedQC, this.highestTC);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    return (o instanceof HighQC that)
        && Objects.equals(this.highestCommittedQC, that.highestCommittedQC)
        && Objects.equals(this.highestQC, that.highestQC)
        && Objects.equals(this.highestTC, that.highestTC);
  }

  @Override
  public String toString() {
    String highestCommittedString =
        (this.highestCommittedQC == null) ? "<same>" : this.highestCommittedQC.toString();
    return String.format(
        "%s[highest=%s, highestCommitted=%s, highestTC=%s]",
        getClass().getSimpleName(), this.highestQC, highestCommittedString, highestTC);
  }
}
