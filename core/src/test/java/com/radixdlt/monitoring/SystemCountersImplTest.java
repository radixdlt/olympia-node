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

package com.radixdlt.monitoring;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import java.util.Map;
import java.util.TreeMap;

import com.radixdlt.monitoring.SystemCountersImpl;
import org.junit.Test;

public class SystemCountersImplTest {
  @Test
  public void when_get_count__then_count_should_be_0() {
    SystemCounters counters = new SystemCountersImpl();
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(0L);
  }

  @Test
  public void when_increment__then_count_should_be_1() {
    SystemCounters counters = new SystemCountersImpl();
    counters.increment(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(1L);
    counters.increment(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(2L);
  }

  @Test
  public void when_add__then_count_should_be_added_value() {
    SystemCounters counters = new SystemCountersImpl();
    counters.add(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 1234);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(1234L);
    counters.add(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 4321);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(1234L + 4321L);
  }

  @Test
  public void when_set__then_count_should_be_1() {
    SystemCounters counters = new SystemCountersImpl();
    counters.set(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 1234);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(1234L);
    counters.set(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 4321);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(4321L);
  }

  @Test
  public void when_set_all__then_count_should_be_correct() {
    SystemCounters counters = new SystemCountersImpl();
    counters.setAll(
        ImmutableMap.of(
            CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 1234L,
            CounterType.BFT_PACEMAKER_PROPOSALS_SENT, 4567L));
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(1234L);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_PROPOSALS_SENT)).isEqualTo(4567L);
    counters.setAll(
        ImmutableMap.of(
            CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 2345L,
            CounterType.BFT_PACEMAKER_PROPOSALS_SENT, 5678L));
    assertThat(counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT)).isEqualTo(2345L);
    assertThat(counters.get(CounterType.BFT_PACEMAKER_PROPOSALS_SENT)).isEqualTo(5678L);
  }

  @Test
  public void when_tomap__then_values_correct() {
    SystemCounters counters = new SystemCountersImpl();
    for (CounterType value : CounterType.values()) {
      counters.set(value, ordinal(value));
    }
    // Ensure writeable
    Map<String, Object> m = new TreeMap<>(counters.toMap());
    assertNotNull(m.remove("time"));
    testMap("", m);
  }

  @Test
  public void sensible_tostring() {
    SystemCounters counters = new SystemCountersImpl();
    counters.set(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT, 1234);
    String s = counters.toString();
    assertThat(s).contains(SystemCountersImpl.class.getSimpleName());
    assertThat(s).contains("1234");
  }

  private int ordinal(CounterType value) {
    // Add one so that none are zero.
    // Zero is the default value, and this lets us catch the "not set" case.
    return value.ordinal() + 1;
  }

  private void testMap(String path, Map<String, Object> m) {
    for (Map.Entry<String, Object> entry : m.entrySet()) {
      String p = entry.getKey().toUpperCase();
      String newPath = path.isEmpty() ? p : path + "_" + p;
      Object o = entry.getValue();
      if (o instanceof Map<?, ?>) {
        @SuppressWarnings("unchecked")
        Map<String, Object> newm = (Map<String, Object>) o;
        testMap(newPath, newm);
      } else {
        String s = o.toString();
        CounterType ct = CounterType.valueOf(newPath);
        // Check that values in the map match the values we set above
        assertThat(Long.parseLong(s)).isEqualTo(ordinal(ct));
      }
    }
  }
}
