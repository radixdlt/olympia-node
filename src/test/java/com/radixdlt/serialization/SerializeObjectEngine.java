/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.serialization;

import org.junit.Test;

import java.util.function.Supplier;

import static com.radixdlt.serialization.SerializationTestUtilsEngine.testEncodeDecode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

/**
 * Raft of tests for serialization of objects.
 * <p>
 * Note that the tests that round-trip types through the serializer
 * are not run for {@link Polymorphic} types, as these types do not
 * serialize to themselves, but to one of their superclasses.
 *
 * @param <T> The type under test.
 */
public abstract class SerializeObjectEngine<T> {

    private final Class<T> cls;
    private final Supplier<T> factory;
    private final Serialization serialization = Serialization.getDefault();

    protected SerializeObjectEngine(Class<T> cls, Supplier<T> factory) {
        this.cls = cls;
        this.factory = factory;
    }

    @Test
    public void testNONEIsEmpty() throws Exception {
        String s2Json = this.serialization.toJson(factory.get(), DsonOutput.Output.NONE);
        assertEquals("{}", s2Json);
    }

    @Test
    public void testEncodeDecodeALL() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.ALL);
    }

    @Test
    public void testEncodeDecodeAPI() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.API);
    }

    @Test
    public void testEncodeDecodePERSIST() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.PERSIST);
    }

    @Test
    public void testEncodeDecodeWIRE() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.WIRE);
    }
}
