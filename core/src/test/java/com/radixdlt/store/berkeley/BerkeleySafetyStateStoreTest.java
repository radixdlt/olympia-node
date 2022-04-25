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

package com.radixdlt.store.berkeley;

import static com.radixdlt.utils.SerializerTestDataGenerator.randomView;
import static com.radixdlt.utils.SerializerTestDataGenerator.randomVote;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.store.DatabaseEnvironment;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import java.util.Optional;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class BerkeleySafetyStateStoreTest {

  @Test
  public void should_be_able_to_restore_committed_state() {
    final var db = mock(Database.class);
    final var env = mock(Environment.class);
    final var dbEnv = mock(DatabaseEnvironment.class);
    final var tx = mock(com.sleepycat.je.Transaction.class);
    when(dbEnv.getEnvironment()).thenReturn(env);
    when(env.openDatabase(any(), any(), any())).thenReturn(db);

    final var store =
        new BerkeleySafetyStateStore(
            dbEnv, DefaultSerialization.getInstance(), new SystemCountersImpl());

    final var safetyState = new SafetyState(randomView(), Optional.of(randomVote()));

    when(env.beginTransaction(any(), any())).thenReturn(tx);

    when(db.put(any(), any(), any())).thenReturn(OperationStatus.SUCCESS);

    ArgumentCaptor<DatabaseEntry> entryCaptor = ArgumentCaptor.forClass(DatabaseEntry.class);

    store.commitState(safetyState);

    verify(db, times(1)).put(any(), any(), entryCaptor.capture());
    verify(tx, times(1)).commit();
    verifyNoMoreInteractions(tx);

    final var cursor = mock(Cursor.class);
    when(db.openCursor(any(), any())).thenReturn(cursor);

    when(cursor.getLast(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              DatabaseEntry entry = (DatabaseEntry) invocation.getArguments()[1];
              entry.setData(entryCaptor.getValue().getData());
              return OperationStatus.SUCCESS;
            });

    var state = store.get();

    assertTrue(state.isPresent());
    assertEquals(safetyState, state.get());
  }
}
