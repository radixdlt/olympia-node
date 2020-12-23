/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store.berkeley;

import com.google.common.collect.Lists;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.store.LedgerEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LedgerEntryGenerator {
    private long stateVersion = 0;

    public List<LedgerEntry> createLedgerEntries(ECKeyPair identity, int n) {
        Random r = new Random(); // SecureRandom not required for test
        // Super paranoid way of doing things
        Map<AID, LedgerEntry> ledgerEntries = new LinkedHashMap<>(n);
        while (ledgerEntries.size() < n) {
            LedgerEntry ledgerEntry = createLedgerEntry(identity, r);
            ledgerEntries.put(ledgerEntry.getAID(), ledgerEntry);
        }

        // Make sure return list is ordered by atom clock.
        List<LedgerEntry> ledgerEntryList = Lists.newArrayList(ledgerEntries.values());
        return ledgerEntryList;
    }

    public List<LedgerEntry> createLedgerEntries(int n) throws Exception {
        ECKeyPair identity = ECKeyPair.generateNew();
        return createLedgerEntries(identity, n);
    }

    public LedgerEntry createLedgerEntry(ECKeyPair identity, Random r) {
        byte[] pKey = new byte[32];
        r.nextBytes(pKey);
        Atom atom = new Atom();

        LedgerEntry ledgerEntry = new LedgerEntry(
            DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.API),
            stateVersion,
            stateVersion,
            AID.from(pKey)
        );
        stateVersion++;
        return ledgerEntry;
    }
}
