package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.mock.MockAtomContent;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class AtomGenerator {
    public List<Atom> createAtoms(ECKeyPair identity, int n) {
        Random r = new Random(); // SecureRandom not required for test
        // Super paranoid way of doing things
        Map<AID, Atom> atoms = Maps.newLinkedHashMap();
        while (atoms.size() < n) {
            Atom atom = createAtom(identity, r);
            atoms.put(atom.getAID(), atom);
        }

        // Make sure return list is ordered by atom clock.
        List<Atom> atomList = Lists.newArrayList(atoms.values());
        return atomList;
    }

    public List<Atom> createAtoms(int n) throws Exception {
        ECKeyPair identity = new ECKeyPair();
        return createAtoms(identity, n);
    }

    public TempoAtom createAtom(ECKeyPair identity, Random r) {
        byte[] pKey = new byte[32];
        r.nextBytes(pKey);
        MockAtomContent content = new MockAtomContent(
                new LedgerIndex((byte) 7, pKey),
                identity.getPublicKey().getBytes()
        );
        TempoAtom atom = new TempoAtom(
                content,
                AID.from(pKey),
            ImmutableSet.of(Longs.fromByteArray(pKey))
        );
        return atom;
    }

    public TempoAtom createAtom(Random r) throws Exception {
        ECKeyPair identity = new ECKeyPair();
        return createAtom(identity, r);
    }

    public TempoAtom convertToTempoAtom(Atom atom) {
        return new TempoAtom(
                atom.getContent(),
                atom.getAID(),
                atom.getShards()
        );
    }
}
