package org.radix.state;

import java.util.HashMap;

@SuppressWarnings("serial")
public class StateMap extends HashMap<StateDomain, State>
{
	public StateMap() 
	{ 
		super(1); 
	}
	
	public StateMap(StateMap stateMap) 
	{ 
		super(1);
		
		this.putAll(stateMap); 
	}
}
