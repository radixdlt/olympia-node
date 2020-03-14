/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store.berkeley;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.consensus.tempo.LedgerEntryGenerator;
import com.radixdlt.store.LedgerEntryStatus;
import com.radixdlt.utils.Ints;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.integration.RadixTestWithStores;
import java.util.List;
import java.util.function.Supplier;

public class BerkeleyRadixLedgerEntryStoreTests extends RadixTestWithStores {

    private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();
    private Serialization serialization = Serialization.getDefault();
    private BerkeleyLedgerEntryStore tempoAtomStore;

    private List<LedgerEntry> ledgerEntries;

    private ECKeyPair identity;

    @Before
    public void setup() throws CryptoException {
        tempoAtomStore = new BerkeleyLedgerEntryStore(serialization, this.getDbEnv());

        identity = new ECKeyPair();
        ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 5);
    }

    @After
    public void teardown() {
    	if (tempoAtomStore != null) {
    		tempoAtomStore.close();
    	}
    }

    @Test
    public void storePendingCommitTest() {
        SoftAssertions.assertSoftly(softly -> {
            //atom added to store successfully
            softly.assertThat(tempoAtomStore.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of()).isSuccess()).isTrue();

            //atom added to store is pending
            softly.assertThat(tempoAtomStore.getStatus(ledgerEntries.get(0).getAID())).isEqualTo(LedgerEntryStatus.PENDING);

            // atom added to store should be in pending
            softly.assertThat(tempoAtomStore.getPending()).contains(ledgerEntries.get(0).getAID());

            //added atom is present in store
            softly.assertThat(tempoAtomStore.contains(ledgerEntries.get(0).getAID())).isTrue();

            // commit atom
            tempoAtomStore.commit(ledgerEntries.get(0).getAID());

            // committed atom is committed
            softly.assertThat(tempoAtomStore.getStatus(ledgerEntries.get(0).getAID())).isEqualTo(LedgerEntryStatus.COMMITTED);

            // committed atom is not in pending
            softly.assertThat(tempoAtomStore.getPending()).doesNotContain(ledgerEntries.get(0).getAID());

            //not added atom is absent in store
            softly.assertThat(tempoAtomStore.contains(ledgerEntries.get(1).getAID())).isFalse();
        });
    }

    @Test
    public void storeContainsTest() {
        SoftAssertions.assertSoftly(softly -> {
            //atom added to store successfully
            softly.assertThat(tempoAtomStore.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of()).isSuccess()).isTrue();

            //added atom is present in store
            softly.assertThat(tempoAtomStore.contains(ledgerEntries.get(0).getAID())).isTrue();

            //not added atom is absent in store
            softly.assertThat(tempoAtomStore.contains(ledgerEntries.get(1).getAID())).isFalse();
        });
    }

    @Test
    public void storeGetTest() {
        SoftAssertions.assertSoftly(softly -> {
            //atom added to store successfully
            softly.assertThat(tempoAtomStore.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of()).isSuccess()).isTrue();

            //added atom is present in store
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(0).getAID()).get()).isEqualTo(ledgerEntries.get(0));

            //not added atom is absent in store
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(1).getAID()).isPresent()).isFalse();
        });
    }

    @Test
    public void storeGetReplaceTest() {
        SoftAssertions.assertSoftly(softly -> {
            //atom added to store successfully
            softly.assertThat(tempoAtomStore.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of()).isSuccess()).isTrue();

            //added atom is present in store
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(0).getAID()).isPresent()).isTrue();

            //not added atom is absent in store
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(1).getAID()).isPresent()).isFalse();

            //atom replaced successfully
            softly.assertThat(
            	tempoAtomStore.replace(ImmutableSet.of(ledgerEntries.get(0).getAID()), ledgerEntries.get(1), ImmutableSet.of(), ImmutableSet.of())
            		.isSuccess()
            ).isTrue();

            //replaced atom gone
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(0).getAID()).isPresent()).isFalse();

            //new atom is present in store
            softly.assertThat(tempoAtomStore.get(ledgerEntries.get(1).getAID()).isPresent()).isTrue();
        });
    }

    @Test
    public void searchDuplicateExactTest() {
        storeAndCommitAtoms();
        // LedgerIndex for shard 200
        StoreIndex storeIndex = new StoreIndex((byte) 200, Ints.toByteArray(200));
        validateShard200(() ->
        	(BerkeleySearchCursor) tempoAtomStore.search(StoreIndex.LedgerIndexType.DUPLICATE, storeIndex, LedgerSearchMode.EXACT)
        );
    }

    @Test
    public void searchDuplicateRangeTest() {
        storeAndCommitAtoms();
        StoreIndex storeIndex = new StoreIndex((byte) 200, Ints.toByteArray(150));
        // LedgerIndex pointing to not existing shard 150.
        // But because ofLedgerSearchMode.RANGE Cursor will point it to next available shard - shard 200
        validateShard200(() ->
        	(BerkeleySearchCursor) tempoAtomStore.search(StoreIndex.LedgerIndexType.DUPLICATE, storeIndex, LedgerSearchMode.RANGE)
        );
    }

    @Test
    public void searchUniqueExactTest() {
        storeAndCommitAtoms();
        SoftAssertions.assertSoftly(softly -> {
            // LedgerIndex for Atom 3
            StoreIndex storeIndex = new StoreIndex(LedgerEntryIndices.ENTRY_INDEX_PREFIX, ledgerEntries.get(3).getAID().getBytes());

            BerkeleySearchCursor tempoCursor =
            	(BerkeleySearchCursor) tempoAtomStore.search(StoreIndex.LedgerIndexType.UNIQUE, storeIndex, LedgerSearchMode.EXACT);
            //Cursor pointing to unique single result.
            //getFirst and getLast pointing to the same value
            //getNext and getPrev are not available
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(3).getAID());
            tempoCursor = tempoAtomStore.getFirst(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(3).getAID());
            tempoCursor = tempoAtomStore.getLast(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(3).getAID());
            softly.assertThat((tempoAtomStore.getNext(tempoCursor))).isNull();
            softly.assertThat((tempoAtomStore.getPrev(tempoCursor))).isNull();
        });
    }

    /**
     * Method validating navigation when shard200Supplier returning BerkeleyCursor which pointing to "Shard 200" which contains TempoAtoms(2,3,4)
     *
     * @param shard200Supplier function which return BerkeleyCursor to "shard 200"
     */
    private void validateShard200(Supplier<BerkeleySearchCursor> shard200Supplier) {
        SoftAssertions.assertSoftly(softly -> {
            BerkeleySearchCursor tempoCursor = shard200Supplier.get();
            //Navigation in scope of shard 200 => (2,3,4)
            //Pointing Atom[2] - first element in shard
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(2).getAID());
            //Atom[2] getNext -> cursor pointing to Atom[3] - second element in shard
            tempoCursor = tempoAtomStore.getNext(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(3).getAID());

            //Atom[3] getNext -> cursor pointing to Atom[4] - third element in shard
            tempoCursor = tempoAtomStore.getNext(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(4).getAID());

            //Atom[4] getFirst -> cursor pointing to Atom[2] - first element in shard
            tempoCursor = tempoAtomStore.getFirst(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(2).getAID());

            //Atom[2] getPrev -> cursor is null, no previous element for first element.
            // Cursor is not saved, tempoCursor still pointing to Atom[2] - first element
            softly.assertThat((tempoAtomStore.getPrev(tempoCursor))).isNull();

            //Atom[2] getLast -> cursor pointing to Atom[4] - last element in shard
            tempoCursor = tempoAtomStore.getLast(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(4).getAID());

            //Atom[4] getNext -> cursor is null, no next element for last element.
            // Cursor is not saved, tempoCursor still pointing to Atom[4] - last element
            softly.assertThat((tempoAtomStore.getNext(tempoCursor))).isNull();

            //Atom[4] getPrev -> cursor pointing to Atom[3] - element before last one
            tempoCursor = tempoAtomStore.getPrev(tempoCursor);
            softly.assertThat(tempoCursor.get()).isEqualTo(ledgerEntries.get(3).getAID());
        });
    }

    /**
     * Method for storing and committing atoms in atomStore with sharding
     * Atoms are committed because some tests rely on them being ordered, which is currently only guaranteed for committed atoms
     * Shard 100 -> (0,1)
     * Shard 200 -> (2,3,4)
     */
    private void storeAndCommitAtoms() {
        SoftAssertions.assertSoftly(softly -> {
            for (int i = 0; i < ledgerEntries.size(); i++) {
                int shard = i < ledgerEntries.size() / 2 ? 100 : 200;
                StoreIndex storeIndex = new StoreIndex((byte) 200, Ints.toByteArray(shard));
                softly.assertThat(tempoAtomStore.store(ledgerEntries.get(i), ImmutableSet.of(), ImmutableSet.of(storeIndex)).isSuccess()).isTrue();
                tempoAtomStore.commit(ledgerEntries.get(i).getAID());
            }
        });
    }

}
