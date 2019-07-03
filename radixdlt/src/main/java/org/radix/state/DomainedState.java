package org.radix.state;

public interface DomainedState 
{
	public State getState(StateDomain stateDomain);
	public StateMap getStates();
	public void setState(StateDomain stateDomain, State state);
	public void setStates(StateMap states);
}
