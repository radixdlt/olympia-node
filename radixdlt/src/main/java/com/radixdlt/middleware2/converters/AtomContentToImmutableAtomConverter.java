package com.radixdlt.middleware2.converters;

import com.radixdlt.AtomContent;
import com.radixdlt.middleware.ImmutableAtom;

public interface AtomContentToImmutableAtomConverter<T extends AtomContent> {
    ImmutableAtom convert(T atomContent);
    T convert(ImmutableAtom immutableAtom);
}
