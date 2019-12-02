package com.radixdlt.tempo;

import com.google.common.collect.Lists;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.ledger.LedgerEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LedgerEntryGenerator {
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
        ECKeyPair identity = new ECKeyPair();
        return createLedgerEntries(identity, n);
    }

    public LedgerEntry createLedgerEntry(ECKeyPair identity, Random r) {
        byte[] pKey = new byte[32];
        r.nextBytes(pKey);
        Atom atom = new Atom();
        try {
            LedgerEntry ledgerEntry = new LedgerEntry(
                Serialization.getDefault().toDson(atom, DsonOutput.Output.API),
                AID.from(pKey)
            );
            return ledgerEntry;
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }
}
