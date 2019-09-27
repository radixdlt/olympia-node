package com.radixdlt.ledger;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.serialization.mapper.JacksonJsonObjectStringDeserializer;
import com.radixdlt.serialization.mapper.JacksonJsonObjectStringSerializer;

public class LedgerJsonJacksonModule extends SimpleModule {
    static final String INDEX_STR_VALUE = ":idx:";

    public LedgerJsonJacksonModule() {
        SimpleModule jsonModule = new SimpleModule();
        jsonModule.addSerializer(LedgerIndex.class, new JacksonJsonObjectStringSerializer<>(
                LedgerIndex.class,
                INDEX_STR_VALUE,
                LedgerIndex::toHexString
        ));
        jsonModule.addDeserializer(LedgerIndex.class, new JacksonJsonObjectStringDeserializer<>(
                LedgerIndex.class,
                INDEX_STR_VALUE,
                LedgerIndex::from
        ));
    }
}
