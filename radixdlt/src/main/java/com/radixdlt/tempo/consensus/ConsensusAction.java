package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAtom;
import org.radix.network2.addressbook.Peer;

import java.util.Objects;
import java.util.Set;

public final class ConsensusAction {
	public enum Type {
		COMMIT,
		SWITCH_PREFERENCE
	}

	private final Type type;
	private final TempoAtom preference;
	// TODO remove need for preferenceAid instead of preference, delivery and caching should really be handled on the caller site
	// but this is necessitated by the hack described below..
	private final AID preferenceAid;
	private final Set<TempoAtom> oldPreferences;
	// TODO remove peersToContact, ugly hack required because RequestDeliverer doesn't return anything
	// which means that we have to get the main Tempo instance to specially treat a new preference (to replace instead of just store)
	private final Set<Peer> peersToContact;

	private ConsensusAction(Type type, TempoAtom preference, AID preferenceAid, Set<TempoAtom> oldPreferences, Set<Peer> peersToContact) {
		this.type = Objects.requireNonNull(type);
		this.preference = preference;
		this.preferenceAid = preferenceAid;
		this.oldPreferences = Objects.requireNonNull(oldPreferences);
		this.peersToContact = Objects.requireNonNull(peersToContact);
	}

	public Type getType() {
		return type;
	}

	public TempoAtom getPreference() {
		return preference;
	}

	public boolean hasPreference() {
		return preference != null;
	}

	public Set<TempoAtom> getOldPreferences() {
		return oldPreferences;
	}

	public AID getPreferenceAid() {
		return preferenceAid;
	}

	public Set<Peer> getPeersToContact() {
		return peersToContact;
	}

	@Override
	public String toString() {
		return String.format("%s %s", type, preferenceAid);
	}

	public static ConsensusAction commit(TempoAtom preference) {
		return new ConsensusAction(Type.COMMIT, preference, preference.getAID(), ImmutableSet.of(), ImmutableSet.of());
	}

	public static ConsensusAction changePreference(AID newPreferenceAid, Set<TempoAtom> oldPreferences, Set<Peer> peersToContact) {
		return changePreference(null, newPreferenceAid, oldPreferences, peersToContact);
	}

	public static ConsensusAction changePreference(TempoAtom newPreference, AID newPreferenceAid, Set<TempoAtom> oldPreferences, Set<Peer> peersToContact) {
		return new ConsensusAction(Type.SWITCH_PREFERENCE, newPreference, newPreferenceAid, oldPreferences, peersToContact);
	}
}
