/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.sanitytestsuite.scenario.serialization;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

import java.util.Map;

import static com.radixdlt.sanitytestsuite.scenario.serialization.SerializationTestVector.Expected;
import static com.radixdlt.sanitytestsuite.scenario.serialization.SerializationTestVector.Input;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SerializationTestVector extends SanityTestVector<Input, Expected> {
    public static final class Expected {
        public String jsonPrettyPrinted;
        public Map<String, Object> dson;
    }

    public static final class Input {
        public Map<String, Object> arguments;
        public String typeSerialization;
    }
}
// CHECKSTYLE:ON