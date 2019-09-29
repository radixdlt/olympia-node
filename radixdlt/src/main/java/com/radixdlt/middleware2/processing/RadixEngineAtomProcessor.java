package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Offset;
import com.radixdlt.utils.Pair;
import org.radix.atoms.Atom;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.routing.NodeAddressGroupTable;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.time.NtpService;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalProofNotValidException;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RadixEngineAtomProcessor {
    private static final Logger log = Logging.getLogger("RadixEngineAtomProcessor");

    private boolean interrupted;

    private Ledger ledger;
    private RadixEngine radixEngine;
    private SimpleRadixEngineAtomToEngineAtom atomConverter;

    @Inject
    public RadixEngineAtomProcessor(Ledger ledger, RadixEngine<SimpleRadixEngineAtom> radixEngine,
                                    SimpleRadixEngineAtomToEngineAtom atomConverter) {
        this.ledger = ledger;
        this.radixEngine = radixEngine;
        this.atomConverter = atomConverter;
    }

    private void process() throws InterruptedException {
        while (!interrupted) {
            LedgerObservation ledgerObservation = ledger.observe();
            if (ledgerObservation.getType() == LedgerObservation.Type.ADOPT) {
                log.info("Middleware received ADOPT event");
                try {
                    SimpleRadixEngineAtom simpleRadixEngineAtom = atomConverter.convert(ledgerObservation.getAtom());
                    simpleRadixEngineAtom = witnessed(simpleRadixEngineAtom);
                    log.debug("Store received atom to engine");
                    radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
                    });
                } catch (Exception e) {
                    log.error("Atom processing failed", e);
                }
            }
        }
        log.info("Processing stopped");
    }

    public void start() {
        log.info("RadixEngineAtomProcessor starting");
        new Thread(() -> {
            try {
                process();
            } catch (InterruptedException e) {
                log.error("Starting of RadixEngineAtomProcessor failed");
            }
        }).start();
    }

    /**
     * Method copied from AtomSync
     * @param cmAtom
     * @return SimpleRadixEngineAtom with TemporalProof
     * @throws CryptoException
     * @throws ValidationException
     */
    public SimpleRadixEngineAtom witnessed(SimpleRadixEngineAtom cmAtom) throws CryptoException, ValidationException {
        SimpleRadixEngineAtom simpleRadixEngineAtom = getSimpleRadixEngineAtomWithLegacyAtom(cmAtom);
        final Atom atom = (Atom) simpleRadixEngineAtom.getAtom();
        TemporalVertex existingNIDVertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());

        if (existingNIDVertex != null)
        {
            if (existingNIDVertex.getClock() > LocalSystem.getInstance().getClock().get())
            {
                LocalSystem.getInstance().set(existingNIDVertex.getClock(), existingNIDVertex.getCommitment(), atom.getTimestamp());
                if (log.hasLevel(Logging.DEBUG))
                    log.debug("Discovered existing TemporalVertex "+existingNIDVertex+" for Atom "+atom.getHID());
            }

            return simpleRadixEngineAtom;
        }

        try
        {
            Pair<Long, Hash> update = LocalSystem.getInstance().update(atom.getAID(), atom.getTimestamp());

            NodeAddressGroupTable nodeGroupTable = null;
            Set<EUID> filteredNIDs = new HashSet<>();

            if (Modules.isAvailable(AddressBook.class) == true)
            {
                // FIXME If PeerHandler is not available yet, causes a real mess when genesis atoms are committed.
                // Filter out the live peers with shards we need that are within sync bounds
                filteredNIDs.addAll(Modules.get(AddressBook.class).recentPeers().
                        filter(Peer::hasSystem).
                        filter(peer -> peer.getSystem().isSynced(LocalSystem.getInstance()) == true). 					// Gossip to nodes that are in sync TODO isAhead is better?
                        filter(peer -> peer.getSystem().getShards().intersects(atom.getShards()) == true). 			// Gossip to nodes that serve the atom shards
                        map(Peer::getNID).
                        filter(nid -> nid.equals(LocalSystem.getInstance().getNID()) == false).	// Don't gossip to the local node
                        collect(Collectors.toSet()));
            }

            /*
             * Broadcasts about new / updated Atoms should propagate via UDP up the RoutingTable groups.
             */
            if (atom.getTemporalProof().isEmpty() == false)
            {
                // TODO check atom specifies the correct origin

                nodeGroupTable = new NodeAddressGroupTable(atom.getTemporalProof().getOrigin().getOwner().getUID(), filteredNIDs);

                List<EUID> broadcastNIDs = nodeGroupTable.getNext(LocalSystem.getInstance().getNID(), true).stream().limit(TemporalProof.BRANCH_VERTEX_NIDS).collect(Collectors.toList());
                if (broadcastNIDs.isEmpty() == true && log.hasLevel(Logging.DEBUG))
                    log.debug("Broadcast NIDs from "+LocalSystem.getInstance().getNID()+" for "+atom.getAID()+" @ "+Modules.get(NtpService.class).getUTCTimeSeconds()+":"+Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE)+" are empty");

                List<EUID> previousNIDs = nodeGroupTable.getPrevious(LocalSystem.getInstance().getNID());
                TemporalVertex previousVertex = null;

                for (TemporalVertex vertex : atom.getTemporalProof().getVertices())
                {
                    if (previousNIDs.contains(vertex.getOwner().getUID()) && vertex.getEdges().contains(LocalSystem.getInstance().getNID()))
                    {
                        previousVertex = vertex;
                        break;
                    }
                    else if (previousVertex == null)
                        previousVertex = vertex;
                }

                // TODO need to handle this better due to RoutingTable poisoning?
                if (previousVertex == null)
                    throw new TemporalProofNotValidException(atom.getTemporalProof());

                TemporalProof branch = atom.getTemporalProof().getBranch(previousVertex, true);
                ECKeyPair nodeKey = LocalSystem.getInstance().getKeyPair();
                TemporalVertex vertex = new TemporalVertex(nodeKey.getPublicKey(),
                        update.getFirst(), Time.currentTimestamp(),
                        update.getSecond(),
                        previousVertex.getHID(), broadcastNIDs);
                branch.add(vertex, nodeKey);
                atom.getTemporalProof().add(vertex, nodeKey);
            }
            else
            {
                // TODO check atom specifies local node as the origin
                nodeGroupTable = new NodeAddressGroupTable(LocalSystem.getInstance().getNID(), filteredNIDs);

                List<EUID> broadcastNIDs = nodeGroupTable.getNext(LocalSystem.getInstance().getNID(), true).stream().limit(TemporalProof.ROOT_VERTEX_NIDS).collect(Collectors.toList());
                if (broadcastNIDs.isEmpty() == true && log.hasLevel(Logging.DEBUG))
                    log.debug("Broadcast NIDs from origin "+LocalSystem.getInstance().getNID()+" for "+atom.getAID()+" @ "+Modules.get(NtpService.class).getUTCTimeSeconds()+":"+Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE)+" are empty");

                ECKeyPair nodeKey = LocalSystem.getInstance().getKeyPair();
                TemporalVertex vertex = new TemporalVertex(nodeKey.getPublicKey(),
                        update.getFirst(), Time.currentTimestamp(),
                        update.getSecond(),
                        EUID.ZERO, broadcastNIDs);
                atom.getTemporalProof().add(vertex, nodeKey);
            }

            atom.getTemporalProof().setState(StateDomain.VALIDATION, new State(State.COMPLETE));

            if (log.hasLevel(Logging.DEBUG))
                log.debug("Appended to TemporalProof for Atom "+atom.getHID());
        }
        catch (Throwable t)
        {
            LocalSystem.getInstance().revert();
            log.error("Reverted System for Atom "+atom.getHID(), t);
            throw t;
        }
        return simpleRadixEngineAtom;

    }


    private static SimpleRadixEngineAtom getSimpleRadixEngineAtomWithLegacyAtom(SimpleRadixEngineAtom cmAtom) {
        ImmutableAtom immutableAtom = cmAtom.getAtom();
        Atom atom = new Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
        return new SimpleRadixEngineAtom(atom, cmAtom.getCMInstruction());
    }

    public void stop() {
        interrupted = true;
    }
}
