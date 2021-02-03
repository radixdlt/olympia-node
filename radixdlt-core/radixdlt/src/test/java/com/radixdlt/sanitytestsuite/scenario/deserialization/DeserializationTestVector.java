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

package com.radixdlt.sanitytestsuite.scenario.deserialization;


import com.radixdlt.sanitytestsuite.model.SanityTestVector;

import java.util.Map;

import static com.radixdlt.sanitytestsuite.scenario.deserialization.DeserializationTestVector.Expected;
import static com.radixdlt.sanitytestsuite.scenario.deserialization.DeserializationTestVector.Input;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class DeserializationTestVector extends SanityTestVector<Input, Expected> {
    public static final class Expected {
        public Map<String, Object> arguments;
    }

    public static final class Input {
        public Map<String, Object> json;
        public Map<String, Object> dson;
        public String typeSerialization;
    }
}
// CHECKSTYLE:ON
