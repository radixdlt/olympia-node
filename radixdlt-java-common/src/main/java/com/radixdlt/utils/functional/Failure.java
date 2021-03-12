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

package com.radixdlt.utils.functional;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.StringJoiner;

public interface Failure {
    FailureType type();
    String message();

    default <T> Result<T> asResult() {
        return Result.fail(this);
    }

    static Failure failure(final FailureType type, final String format, final Object... params) {
        return failure(type, MessageFormat.format(format, params));
    }

    static Failure failure(final FailureType type, final String message) {
        return new Failure() {
            @Override
            public FailureType type() {
                return type;
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, message);
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) {
                    return true;
                }

                return (obj instanceof Failure)
                       && (Objects.equals(((Failure) obj).type(), type) && Objects.equals(((Failure) obj).message(), message));
            }

            @Override
            public String toString() {
                return new StringJoiner(", ", "Error(", ")")
                        .add(type.toString())
                        .add(message)
                        .toString();
            }
        };
    }
}