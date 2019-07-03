package org.radix.validation;

import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.ECKeyPair;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.state.StateMap;

/**
 * Provides an abstract implementation of a validatable object.
 **
 * @author Dan Hughes
 */
// TODO remove ValidableObject and implement container for ValidatedAtom
public abstract class ValidatableObject extends BasicContainer implements Validatable
{
	private static final Logger log = Logging.getLogger ();

	private StateMap states;

	protected ValidatableObject()
	{
		super();
	}

	/**
	 * Copy constructor.
	 * @param copy {@link ValidatableObject} to copy values from.
	 */
	public ValidatableObject(ValidatableObject copy) {
		super(copy);
		if (copy.states != null) {
			this.states = new StateMap(copy.states);
		}
	}

	@Override
	public void reset(ECKeyPair accessor)
	{
		super.reset(accessor);

		this.setState(StateDomain.VALIDATION, new State(State.NONE));
	}

	@Override
	public final boolean isValidated(Validator.Mode mode)
	{
		if (getState(StateDomain.VALIDATION).in(State.NONE))
			return false;

		Validator.Mode validatedTo = Validator.Mode.valueOf(getState(StateDomain.VALIDATION).getName().toUpperCase());

		if (validatedTo == null)
			return false;

		if (validatedTo.mode() < mode.mode())
			return false;

		return true;
	}

	@Override
	public final void setValidated(Validator.Mode mode)
	{
		setState(StateDomain.VALIDATION, State.getByName(mode.name().toUpperCase()));
	}

	// STATE //
	@Override
	public State getState(StateDomain domain)
	{
		if (states == null || !states.containsKey(domain))
			return new State(State.NONE);

		return states.get(domain);
	}

	@Override
	public StateMap getStates()
	{
		if (states == null || states.isEmpty())
			return new StateMap();

		return new StateMap(this.states);
	}

	@Override
	public void setState(StateDomain domain, State state)
	{
		if (states == null)
			states = new StateMap();

		this.states.put(domain, state);
	}

	@Override
	public void setStates(StateMap states)
	{
		this.states = new StateMap(states);
	}
}
