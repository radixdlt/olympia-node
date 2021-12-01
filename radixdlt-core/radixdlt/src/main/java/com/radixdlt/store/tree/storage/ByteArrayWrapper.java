package com.radixdlt.store.tree.storage;

import java.util.Arrays;
import java.util.Objects;

public class ByteArrayWrapper {

    private final byte[] data;

    private ByteArrayWrapper(byte[] data) {
        Objects.requireNonNull(data);
        this.data = data;
    }

    public static ByteArrayWrapper from(byte[] data) {
        return new ByteArrayWrapper(data);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ByteArrayWrapper that = (ByteArrayWrapper) o;
        return Arrays.equals(getData(), that.getData());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getData());
    }
}
