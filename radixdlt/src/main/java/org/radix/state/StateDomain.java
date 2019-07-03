package org.radix.state;

public enum StateDomain
{
	NETWORK(0),
	LOCAL(1),
	EXEC(2),
	VALIDATION(3);

	private final int domain;

	StateDomain(int domain) { this.domain = domain; }

	public int domain() { return domain; }

	public static StateDomain get(int domain)
	{
		for (StateDomain d : values())
		{
			if (d.domain() == domain)
				return d;
		}

		return null;
	}
}

