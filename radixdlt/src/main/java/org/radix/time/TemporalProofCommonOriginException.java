package org.radix.time;

import org.radix.common.Criticality;
import org.radix.exceptions.ValidationException;

@SuppressWarnings("serial")
public class TemporalProofCommonOriginException extends ValidationException
{
	private final TemporalProof invoker;
	private final TemporalProof conflictor;

	public TemporalProofCommonOriginException(TemporalProof conflictor, TemporalProof invoker)
	{
		super("TemporalProofs for Atom "+conflictor.getAID()+" do not share a common origin", Criticality.FATAL);

		this.conflictor = conflictor;
		this.invoker = invoker;
	}

	public TemporalProof getConflictor()
	{
		return this.conflictor;
	}

	public TemporalProof getInvoker()
	{
		return this.invoker;
	}
}
