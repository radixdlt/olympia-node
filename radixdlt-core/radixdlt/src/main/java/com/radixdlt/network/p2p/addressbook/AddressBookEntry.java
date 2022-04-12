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

package com.radixdlt.network.p2p.addressbook;

import static java.util.function.Predicate.not;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry.LatestConnectionStatus;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@SerializerId2("network.p2p.addressbook.address_book_entry")
public final class AddressBookEntry {
  // Placeholder for the serializer ID
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(DsonOutput.Output.ALL)
  private SerializerDummy serializer = SerializerDummy.DUMMY;

  @JsonProperty("nodeId")
  @DsonOutput(DsonOutput.Output.ALL)
  private final NodeId nodeId;

  private final Optional<Instant> bannedUntil;

  @JsonProperty("knownAddresses")
  @DsonOutput(DsonOutput.Output.ALL)
  private final ImmutableSet<PeerAddressEntry> knownAddresses;

  @JsonCreator
  private static AddressBookEntry deserialize(
      @JsonProperty(value = "nodeId", required = true) NodeId nodeId,
      @JsonProperty("bannedUntil") long rawBannedUntil,
      @JsonProperty("knownAddresses") ImmutableSet<PeerAddressEntry> knownAddresses) {
    final var bannedUntil =
        rawBannedUntil > 0
            ? Optional.of(Instant.ofEpochMilli(rawBannedUntil))
            : Optional.<Instant>empty();
    return new AddressBookEntry(
        nodeId, bannedUntil, knownAddresses != null ? knownAddresses : ImmutableSet.of());
  }

  public static AddressBookEntry create(RadixNodeUri uri) {
    return create(uri, Optional.empty());
  }

  public static AddressBookEntry createBanned(NodeId nodeId, Instant bannedUntil) {
    return new AddressBookEntry(nodeId, Optional.of(bannedUntil), ImmutableSet.of());
  }

  public static AddressBookEntry createWithLatestConnectionStatus(
      RadixNodeUri uri, LatestConnectionStatus latestConnectionStatus) {
    return create(uri, Optional.of(latestConnectionStatus));
  }

  public static AddressBookEntry createBlacklisted(RadixNodeUri uri, Instant blacklistedUntil) {
    return new AddressBookEntry(
        uri.getNodeId(),
        Optional.empty(),
        ImmutableSet.of(
            new PeerAddressEntry(uri, Optional.empty(), Optional.of(blacklistedUntil))));
  }

  public static AddressBookEntry create(
      RadixNodeUri uri, Optional<LatestConnectionStatus> latestConnectionStatus) {
    return new AddressBookEntry(
        uri.getNodeId(),
        Optional.empty(),
        ImmutableSet.of(
            new AddressBookEntry.PeerAddressEntry(uri, latestConnectionStatus, Optional.empty())));
  }

  AddressBookEntry(
      NodeId nodeId, Optional<Instant> bannedUntil, ImmutableSet<PeerAddressEntry> knownAddresses) {
    this.nodeId = Objects.requireNonNull(nodeId);
    this.bannedUntil = bannedUntil;
    this.knownAddresses = Objects.requireNonNull(knownAddresses);
  }

  @JsonProperty("bannedUntil")
  @DsonOutput(DsonOutput.Output.ALL)
  public long rawBannedUntilForSerializer() {
    return this.bannedUntil.map(Instant::toEpochMilli).orElse(0L);
  }

  public NodeId getNodeId() {
    return nodeId;
  }

  public boolean isBanned() {
    return bannedUntil.filter(v -> v.isAfter(Instant.now())).isPresent();
  }

  public Optional<Instant> bannedUntil() {
    return this.bannedUntil;
  }

  public ImmutableSet<PeerAddressEntry> getKnownAddresses() {
    return knownAddresses;
  }

  public AddressBookEntry withBanUntil(Instant banUntil) {
    return new AddressBookEntry(nodeId, Optional.of(banUntil), knownAddresses);
  }

  public AddressBookEntry withReplacedKnownAddresses(
      ImmutableSet<PeerAddressEntry> knownAddresses) {
    return new AddressBookEntry(this.nodeId, this.bannedUntil, knownAddresses);
  }

  public AddressBookEntry addUriIfNotExists(RadixNodeUri uri) {
    if (entryFor(uri).isPresent()) {
      return this;
    }

    final var newAddressEntry = new PeerAddressEntry(uri, Optional.empty(), Optional.empty());
    final var newKnownAddresses =
        ImmutableSet.<PeerAddressEntry>builder()
            .addAll(this.knownAddresses)
            .add(newAddressEntry)
            .build();
    return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
  }

  public Optional<PeerAddressEntry> entryFor(RadixNodeUri uri) {
    return knownAddresses.stream().filter(e -> e.getUri().equals(uri)).findAny();
  }

  public AddressBookEntry withLatestConnectionStatusForUri(
      RadixNodeUri uri, LatestConnectionStatus latestConnectionStatus) {
    Objects.requireNonNull(latestConnectionStatus);

    final var maybeExistingAddress =
        this.knownAddresses.stream().filter(e -> e.getUri().equals(uri)).findAny();

    if (maybeExistingAddress.isPresent()) {
      final var updatedAddressEntry =
          maybeExistingAddress.get().withLatestConnectionStatus(latestConnectionStatus);
      final var knownAddressesWithoutTheOldOne =
          this.knownAddresses.stream()
              .filter(not(e -> e.getUri().equals(uri)))
              .collect(ImmutableSet.toImmutableSet());
      final var newKnownAddresses =
          ImmutableSet.<PeerAddressEntry>builder()
              .addAll(knownAddressesWithoutTheOldOne)
              .add(updatedAddressEntry)
              .build();
      return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
    } else {
      final var newAddressEntry =
          new PeerAddressEntry(uri, Optional.of(latestConnectionStatus), Optional.empty());
      final var newKnownAddresses =
          ImmutableSet.<PeerAddressEntry>builder()
              .addAll(this.knownAddresses)
              .add(newAddressEntry)
              .build();
      return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
    }
  }

  public AddressBookEntry withBlacklistedUri(RadixNodeUri uri, Instant blacklistedUntil) {
    final var maybeExistingAddress =
        this.knownAddresses.stream().filter(e -> e.getUri().equals(uri)).findAny();

    if (maybeExistingAddress.isPresent()) {
      final var updatedAddressEntry = maybeExistingAddress.get().blacklistUntil(blacklistedUntil);
      final var knownAddressesWithoutTheOldOne =
          this.knownAddresses.stream()
              .filter(not(e -> e.getUri().equals(uri)))
              .collect(ImmutableSet.toImmutableSet());
      final var newKnownAddresses =
          ImmutableSet.<PeerAddressEntry>builder()
              .addAll(knownAddressesWithoutTheOldOne)
              .add(updatedAddressEntry)
              .build();
      return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
    } else {
      final var newAddressEntry =
          new PeerAddressEntry(uri, Optional.empty(), Optional.of(blacklistedUntil));
      final var newKnownAddresses =
          ImmutableSet.<PeerAddressEntry>builder()
              .addAll(this.knownAddresses)
              .add(newAddressEntry)
              .build();
      return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
    }
  }

  public AddressBookEntry cleanupExpiredBlacklsitedUris() {
    final var newKnownAddresses =
        knownAddresses.stream()
            .filter(not(PeerAddressEntry::blacklistExpired))
            .collect(ImmutableSet.toImmutableSet());
    return new AddressBookEntry(nodeId, bannedUntil, newKnownAddresses);
  }

  @Override
  public String toString() {
    return String.format(
        "%s[nodeId=%s, bannedUntil=%s, knownAddresses=%s]",
        getClass().getSimpleName(), nodeId, bannedUntil, knownAddresses);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    return (o instanceof AddressBookEntry that)
        && Objects.equals(bannedUntil, that.bannedUntil)
        && Objects.equals(nodeId, that.nodeId)
        && Objects.equals(knownAddresses, that.knownAddresses);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, bannedUntil, knownAddresses);
  }

  @SerializerId2("network.p2p.addressbook.peer_address_entry")
  public static final class PeerAddressEntry {
    public enum LatestConnectionStatus {
      SUCCESS,
      FAILURE
    }

    // Placeholder for the serializer ID
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(DsonOutput.Output.ALL)
    private SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("uri")
    @DsonOutput(DsonOutput.Output.ALL)
    private final RadixNodeUri uri;

    private final Optional<LatestConnectionStatus> latestConnectionStatus;

    private final Optional<Instant> blacklistedUntil;

    @JsonCreator
    private static PeerAddressEntry deserialize(
        @JsonProperty("uri") RadixNodeUri uri,
        @JsonProperty("latestConnectionStatus") String rawLatestConnectionStatus,
        @JsonProperty("blacklistedUntil") Long rawBlacklistedUntil) {
      final var latestConnectionStatus =
          Optional.ofNullable(rawLatestConnectionStatus).map(LatestConnectionStatus::valueOf);
      final var blacklistedUntil =
          Optional.ofNullable(rawBlacklistedUntil).map(Instant::ofEpochMilli);
      return new PeerAddressEntry(uri, latestConnectionStatus, blacklistedUntil);
    }

    PeerAddressEntry(
        RadixNodeUri uri,
        Optional<LatestConnectionStatus> latestConnectionStatus,
        Optional<Instant> blacklistedUntil) {
      this.uri = Objects.requireNonNull(uri);
      this.latestConnectionStatus = Objects.requireNonNull(latestConnectionStatus);
      this.blacklistedUntil = Objects.requireNonNull(blacklistedUntil);
    }

    public RadixNodeUri getUri() {
      return uri;
    }

    public Optional<LatestConnectionStatus> getLatestConnectionStatus() {
      return latestConnectionStatus;
    }

    public boolean blacklisted() {
      return blacklistedUntil.filter(v -> v.isAfter(Instant.now())).isPresent();
    }

    public boolean blacklistExpired() {
      return blacklistedUntil.isPresent() && !blacklisted();
    }

    @JsonProperty("latestConnectionStatus")
    @DsonOutput(DsonOutput.Output.ALL)
    private String getLatestConnectionStatusForSerializer() {
      return latestConnectionStatus.map(LatestConnectionStatus::toString).orElse(null);
    }

    @JsonProperty("blacklistedUntil")
    @DsonOutput(DsonOutput.Output.ALL)
    public Long rawBlacklistedUntilForSerializer() {
      return this.blacklistedUntil.map(Instant::toEpochMilli).orElse(null);
    }

    public PeerAddressEntry withLatestConnectionStatus(
        LatestConnectionStatus latestConnectionStatus) {
      return new PeerAddressEntry(uri, Optional.of(latestConnectionStatus), blacklistedUntil);
    }

    public PeerAddressEntry blacklistUntil(Instant blacklistedUntil) {
      return new PeerAddressEntry(uri, latestConnectionStatus, Optional.of(blacklistedUntil));
    }

    @Override
    public String toString() {
      return String.format(
          "%s[uri=%s, latestConnectionStatus=%s, blacklistedUntil=%s]",
          getClass().getSimpleName(), uri, latestConnectionStatus, blacklistedUntil);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PeerAddressEntry that = (PeerAddressEntry) o;
      return Objects.equals(uri, that.uri)
          && Objects.equals(latestConnectionStatus, that.latestConnectionStatus)
          && Objects.equals(blacklistedUntil, that.blacklistedUntil);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uri, latestConnectionStatus, blacklistedUntil);
    }
  }
}
