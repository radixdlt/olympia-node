package org.radix.atoms.particles.conflict;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.SpunParticle;
import org.radix.collections.WireableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import org.radix.common.ID.ID;
import org.radix.exceptions.ValidationException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.state.SingletonState;
import org.radix.state.State;
import org.radix.validation.ValidatableObject;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("conflict.particle_conflict")
public final class ParticleConflict extends ValidatableObject implements ID, SingletonState
{
	@Override
	public short VERSION() { return 100; }

	@JsonProperty("particle")
	@DsonOutput(Output.ALL)
	private SpunParticle<? extends Particle> particle;

	@JsonProperty("atoms")
	@DsonOutput(Output.ALL)
    private WireableSet<Atom>	atoms;

	@JsonProperty("result")
	@DsonOutput(value = {Output.WIRE, Output.PERSIST})
	private AID				result;

    private boolean assisted;
    private int		cycle;
	private State  	state;

	public ParticleConflict()
	{
		super();

    	this.state = new State(State.PENDING);
	}

    public ParticleConflict(SpunParticle<? extends Particle> particle, Atom conflictor, Atom invoker)
    {
    	this();

    	this.result = null;
    	this.particle = particle;
    	this.atoms = new WireableSet<>();
    	this.atoms.add(invoker);
    	this.atoms.add(conflictor);
    }

    public ParticleConflict(SpunParticle<? extends Particle> particle, Set<Atom> atoms)
    {
    	this();

    	this.result = null;
    	this.particle = particle;
    	this.atoms = new WireableSet<>(atoms);
    }

    @Override
    public int hashCode()
    {
	    return this.getUID().hashCode();
    }

    @Override
    public boolean equals(Object object)
    {
	    if (object == null) {
		    return false;
	    }

	    if (object == this) {
		    return true;
	    }

	    if (object instanceof ParticleConflict && this.getUID().equals(((ParticleConflict) object).getUID())) {
		    return true;
	    }

    	return false;
    }

    @Override
    public String toString()
    {
    	return this.particle.getClass().toString()+": "+this.particle.getParticle().getHID()+" "+this.state+" "+this.atoms.stream().map(Object::toString).collect(Collectors.joining(" -> "));
    }

	boolean isAssisted()
	{
		return this.assisted;
	}

	void setAssisted(boolean assisted)
	{
		this.assisted = assisted;
	}

	int getCycle()
	{
		return this.cycle;
	}

	void setCycle(int cycle)
	{
		this.cycle = cycle;
	}

	@Override
	public EUID getUID()
	{
		return this.particle.getParticle().getHID();
	}

	@Override
	public void setUID(EUID id)
	{
		throw new UnsupportedOperationException("UID can not be set on ParticleConflict");
	}

	@Override
	public State getState()
	{
		return this.state;
	}

	@Override
	public void setState(State state)
	{
		this.state = state;
	}

    public AID getResult()
    {
    	return this.result;
    }

    void setResult(AID atomHID)
    {
   		this.result = atomHID;

	    if (this.result != null) {
		    this.state = new State(State.RESOLVED);
	    } else {
		    this.state = new State(State.PENDING);
	    }
    }

    public SpunParticle<? extends Particle> getSpunParticle() {
    	return this.particle;
    }

    public Set<Atom> getAtoms()
    {
    	return Collections.unmodifiableSet(new HashSet<>(this.atoms));
    }

	public boolean addAtom(Atom atom)
	{
		return this.atoms.add(atom);
	}

	boolean hasAtom(AID atomId)
	{
		for (Atom atom : this.atoms)
			if (atom.getAID().equals(atomId))
				return true;

		return false;
	}

	boolean removeAtom(Atom atom)
	{
		return this.atoms.remove(atom);
	}

	public Atom getAtom(AID atomId)
    {
	    for (Atom atom : this.atoms) {
		    if (atom.getAID().equals(atomId)) {
			    return atom;
		    }
	    }

    	return null;
    }

    public long getTimestamp()
    {
    	long timestamp = Long.MAX_VALUE;

	    for (Atom atom : this.atoms) {
		    if (atom.getTimestamp() < timestamp) {
			    timestamp = atom.getTimestamp();
		    }
	    }

    	return timestamp;
    }

	public void merge(ParticleConflict conflict) throws ValidationException
	{
		Map<AID, Atom> newAtoms = new HashMap<>();
		for (Atom atom : this.atoms) {
			newAtoms.put(atom.getAID(), atom);
		}

		for (Atom atom : conflict.getAtoms())
		{
			if (!newAtoms.containsKey(atom.getAID())) {
				this.addAtom(atom);
			} else {
				newAtoms.get(atom.getAID()).getTemporalProof().merge(atom.getTemporalProof());
			}
		}
	}
}