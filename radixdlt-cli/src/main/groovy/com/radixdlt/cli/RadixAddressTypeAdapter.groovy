package com.radixdlt.cli

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.radixdlt.client.atommodel.accounts.RadixAddress

class RadixAddressTypeAdapter extends TypeAdapter<RadixAddress> {
    @Override
    void write(JsonWriter out, RadixAddress address) throws IOException {
        out.value(address.toString())
    }

    @Override
    RadixAddress read(JsonReader reader) throws IOException {
        // implement the deserialization
        return RadixAddress.from(reader.nextString())
    }
}