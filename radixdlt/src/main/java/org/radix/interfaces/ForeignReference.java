package org.radix.interfaces;

import com.radixdlt.common.EUID;

public interface ForeignReference<R> 
{
	public EUID getForeignReferenceID();
	public R setForeignReferenceID(EUID foreignReferenceID);

	public long getForeignClassID();
	public R setForeignClassID(long foreignClassID);
}
