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

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

/**
 * The Data Transfer Object for Consensus messages. Each type of consensus message currently needs
 * to be a parameter in this class due to lack of interface serialization.
 */
@SerializerId2("message.consensus.event")
// TODO: requires rework
public final class ConsensusEventMessage extends Message {
  @JsonProperty("proposal")
  @DsonOutput(Output.ALL)
  private final Proposal proposal;

  @JsonProperty("vote")
  @DsonOutput(Output.ALL)
  private final Vote vote;

  @JsonCreator
  public ConsensusEventMessage(
      @JsonProperty("proposal") Proposal proposal, @JsonProperty("vote") Vote vote) {
    if (proposal == null && vote == null) {
      throw new IllegalStateException(
          "No vote nor proposal are provided for ConsensusEventMessage");
    }

    if (proposal != null && vote != null) {
      throw new IllegalArgumentException(
          "Both, vote and proposal are provided for ConsensusEventMessage");
    }

    this.proposal = proposal;
    this.vote = vote;
  }

  public ConsensusEventMessage(Proposal proposal) {
    this(proposal, null);
  }

  public ConsensusEventMessage(Vote vote) {
    this(null, vote);
  }

  public ConsensusEvent getConsensusMessage() {
    var event = consensusMessageInternal();
    if (event == null) {
      throw new IllegalStateException("No consensus message.");
    }
    return event;
  }

  private ConsensusEvent consensusMessageInternal() {
    if (this.proposal != null) {
      return this.proposal;
    }

    if (this.vote != null) {
      return this.vote;
    }

    return null;
  }

  @Override
  public String toString() {
    return String.format("%s[%s]", getClass().getSimpleName(), consensusMessageInternal());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    return (o instanceof ConsensusEventMessage that)
        && Objects.equals(proposal, that.proposal)
        && Objects.equals(vote, that.vote)
        && Objects.equals(getTimestamp(), that.getTimestamp());
  }

  @Override
  public int hashCode() {
    return Objects.hash(proposal, vote, getTimestamp());
  }
}
