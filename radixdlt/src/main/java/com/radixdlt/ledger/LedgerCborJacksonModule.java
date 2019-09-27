package com.radixdlt.ledger;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.serialization.mapper.JacksonCborObjectBytesDeserializer;
import com.radixdlt.serialization.mapper.JacksonCborObjectBytesSerializer;

public class LedgerCborJacksonModule extends SimpleModule {
    static final byte INDEX_VALUE = 0x10;

    public LedgerCborJacksonModule() {
        addSerializer(LedgerIndex.class, new JacksonCborObjectBytesSerializer<>(
                LedgerIndex.class,
                INDEX_VALUE,
                LedgerIndex::asKey
        ));
        addDeserializer(LedgerIndex.class, new JacksonCborObjectBytesDeserializer<>(
                LedgerIndex.class,
                INDEX_VALUE,
                LedgerIndex::from
        ));
    }
}
