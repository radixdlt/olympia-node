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

package com.radixdlt.environment.deterministic.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Queue for messages by view. */
public final class MessageQueue {

  private final HashMap<Long, LinkedList<ControlledMessage>> messagesByTime = Maps.newHashMap();
  private long minimumMessageTime = Long.MAX_VALUE; // Cached minimum time

  MessageQueue() {
    // Nothing here for now
  }

  public boolean add(ControlledMessage item) {
    long messageTime = item.arrivalTime();
    this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).add(item);
    if (messageTime < this.minimumMessageTime) {
      this.minimumMessageTime = messageTime;
    }
    return true;
  }

  public boolean addFirst(ControlledMessage item) {
    long messageTime = item.arrivalTime();
    this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).addFirst(item);
    if (messageTime < this.minimumMessageTime) {
      this.minimumMessageTime = messageTime;
    }
    return true;
  }

  public boolean addBefore(ControlledMessage item, Predicate<ControlledMessage> test) {
    var messageTime = item.arrivalTime();
    var i =
        this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).listIterator();
    var inserted = false;
    while (i.hasNext()) {
      if (test.test(i.next())) {
        // Backup and insert
        i.previous();
        i.add(item);
        inserted = true;
        break;
      }
    }
    if (!inserted) {
      i.add(item);
    }
    if (messageTime < this.minimumMessageTime) {
      this.minimumMessageTime = messageTime;
    }
    return true;
  }

  void remove(Predicate<ControlledMessage> filter) {
    messagesByTime.values().forEach(l -> l.removeIf(filter));
    messagesByTime.values().removeIf(List::isEmpty);
    this.minimumMessageTime = minimumKey(this.messagesByTime.keySet());
  }

  void remove(ControlledMessage message) {
    LinkedList<ControlledMessage> msgs = this.messagesByTime.get(this.minimumMessageTime);
    if (msgs == null) {
      painfulRemove(message);
      return;
    }
    if (!msgs.remove(message)) {
      painfulRemove(message);
      return;
    }
    if (msgs.isEmpty()) {
      this.messagesByTime.remove(this.minimumMessageTime);
      this.minimumMessageTime = minimumKey(this.messagesByTime.keySet());
    }
  }

  public void dump(PrintStream out) {
    Comparator<ChannelId> channelIdComparator =
        Comparator.<ChannelId>comparingInt(ChannelId::senderIndex)
            .thenComparing(ChannelId::receiverIndex);
    out.println("{");
    this.messagesByTime.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEachOrdered(
            e1 -> {
              out.format("    %s {%n", e1.getKey());
              e1.getValue().stream()
                  .sorted(Comparator.comparing(ControlledMessage::channelId, channelIdComparator))
                  .forEach(cm -> out.format("        %s%n", cm));
              out.println("    }");
            });
    out.println("}");
  }

  Set<ControlledMessage> allMessages() {
    return this.messagesByTime.values().stream()
        .flatMap(LinkedList::stream)
        .collect(Collectors.toSet());
  }

  List<ControlledMessage> lowestTimeMessages() {
    if (this.messagesByTime.isEmpty()) {
      return Collections.emptyList();
    }
    return this.messagesByTime.get(this.minimumMessageTime);
  }

  @Override
  public String toString() {
    return this.messagesByTime.toString();
  }

  // If not removing message of the lowest rank, then we do it the painful way
  private void painfulRemove(ControlledMessage message) {
    List<Map.Entry<Long, LinkedList<ControlledMessage>>> entries =
        Lists.newArrayList(this.messagesByTime.entrySet());
    Collections.sort(entries, Map.Entry.comparingByKey());
    for (Map.Entry<Long, LinkedList<ControlledMessage>> entry : entries) {
      LinkedList<ControlledMessage> msgs = entry.getValue();
      if (msgs != null && msgs.remove(message)) {
        if (msgs.isEmpty()) {
          this.messagesByTime.remove(entry.getKey());
          // Can't affect minimumView if we are here
        }
        return;
      }
    }
    throw new NoSuchElementException();
  }

  // Believe it or not, this is faster, when coupled with minimumView
  // caching, than using a TreeMap for nodes == 100.
  private static long minimumKey(Set<Long> eavs) {
    if (eavs.isEmpty()) {
      return Long.MAX_VALUE;
    }
    return Collections.min(eavs);
  }
}
