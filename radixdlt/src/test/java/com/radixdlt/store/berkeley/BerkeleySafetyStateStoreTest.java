package com.radixdlt.store.berkeley;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.safety.SafetyState;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.radix.database.DatabaseEnvironment;

import java.util.Optional;

import static com.radixdlt.utils.SerializerTestDataGenerator.randomView;
import static com.radixdlt.utils.SerializerTestDataGenerator.randomVote;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BerkeleySafetyStateStoreTest {

    @Test
    public void should_be_able_to_restore_committed_state() {
        final var db = mock(Database.class);
        final var env = mock(Environment.class);
        final var dbEnv = mock(DatabaseEnvironment.class);
        final var tx = mock(Transaction.class);
        when(dbEnv.getEnvironment()).thenReturn(env);
        when(env.openDatabase(any(), any(), any())).thenReturn(db);

        final var store = new BerkeleySafetyStateStore(dbEnv, DefaultSerialization.getInstance());

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

        when(cursor.getLast(any(), any(), any())).thenAnswer(invocation -> {
            DatabaseEntry entry = (DatabaseEntry) invocation.getArguments()[1];
            entry.setData(entryCaptor.getValue().getData());
            return OperationStatus.SUCCESS;
        });

        assertEquals(safetyState, store.get().get());
    }
}
