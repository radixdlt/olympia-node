package org.radix.atoms;

import org.radix.exceptions.ValidationException;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;

import java.util.List;
import java.util.Set;

@SuppressWarnings("serial")
public class AtomDependencyNotFoundException extends ValidationException
{
	private final ImmutableList<EUID> dependencies;

	public AtomDependencyNotFoundException(String message, Set<EUID> dependencies, Atom atom)
	{
		super(message);

		this.dependencies = ImmutableList.copyOf(dependencies);
	}

	public List<EUID> getDependencies()
	{
		return this.dependencies;
	}
}
