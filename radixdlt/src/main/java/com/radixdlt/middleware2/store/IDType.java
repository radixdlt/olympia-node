package com.radixdlt.middleware2.store;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Longs;

public enum IDType {
    ATOM, PARTICLE_UP, PARTICLE_DOWN, PARTICLE_CLASS, UID, DESTINATION, SHARD;

    public static byte[] toByteArray(IDType type, long value) {
        byte[] typeBytes = new byte[Long.BYTES + 1];
        typeBytes[0] = (byte) type.ordinal();
        System.arraycopy(Longs.toByteArray(value), 0, typeBytes, 1, Long.BYTES);
        return typeBytes;
    }

    public static byte[] toByteArray(IDType type, EUID id) {
        if (id == null) {
            throw new IllegalArgumentException("EUID is null");
        }

        byte[] idBytes = id.toByteArray();
        byte[] typeBytes = new byte[idBytes.length + 1];
        typeBytes[0] = (byte) type.ordinal();
        System.arraycopy(idBytes, 0, typeBytes, 1, idBytes.length);
        return typeBytes;
    }

    public static byte[] toByteArray(IDType type, AID aid) {
        if (aid == null) {
            throw new IllegalArgumentException("AID is null");
        }

        byte[] typeBytes = new byte[AID.BYTES + 1];
        typeBytes[0] = (byte) type.ordinal();
        aid.copyTo(typeBytes, 1);
        return typeBytes;
    }

    public static EUID toEUID(byte[] bytes) {
        byte[] temp = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, temp, 0, temp.length);
        return new EUID(temp);
    }

    public static long toLong(byte[] bytes) {
        byte[] temp = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, temp, 0, temp.length);
        return Longs.fromByteArray(temp);
    }
}
