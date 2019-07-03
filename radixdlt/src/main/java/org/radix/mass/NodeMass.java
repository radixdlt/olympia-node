package org.radix.mass;

import java.util.Objects;

import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt384;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("mass.node_mass")
public final class NodeMass extends BasicContainer
{
	@Override
	public short VERSION() { return 100; }

	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	private EUID	NID;

	@JsonProperty("mass")
	@DsonOutput(Output.ALL)
	private UInt384 mass;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private int 	planck;

	public NodeMass()
	{
		super();
	}

	public NodeMass(EUID NID, UInt384 mass, int planck)
	{
		if (NID.equals(EUID.ZERO))
			throw new IllegalArgumentException("NID is ZERO");

		if (planck < 0)
			throw new IllegalArgumentException("Planck is negative");

		this.NID = NID;
		this.mass = Objects.requireNonNull(mass);
		this.planck = planck;
	}

	public EUID getNID()
	{
		return NID;
	}

	public UInt384 getMass()
	{
		return mass;
	}

	public int getPlanck()
	{
		return planck;
	}
	
	@Override
	public boolean equals(Object other)
	{
		if (other == null) 
			return false;
		
		if (other == this) 
			return true;
		
		if ((other instanceof NodeMass) == false)
			return false;

		if (this.NID.equals(((NodeMass)other).NID) == false)
			return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		return this.NID.hashCode();
	}
}
