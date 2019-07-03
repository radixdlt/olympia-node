package org.radix.common.ID;

import com.radixdlt.common.EUID;

public interface ID
{
	public EUID getUID();
	public void setUID(EUID id);
}
