/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

/**
 * Represents a vote timeout; data to sign for a timeout signature.
 */
@Immutable
@SerializerId2("consensus.vote_timeout")
public class VoteTimeout {
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
    SerializerDummy serializer = SerializerDummy.DUMMY;

    private final View view;

    @JsonProperty("epoch")
    @DsonOutput(DsonOutput.Output.ALL)
    private final long epoch;

    @JsonCreator
    public VoteTimeout(
            @JsonProperty("view") View view,
            @JsonProperty("epoch") long epoch
    ) {
        this.view = Objects.requireNonNull(view);
        this.epoch = epoch;
    }

    public static VoteTimeout of(Vote vote) {
        return new VoteTimeout(vote.getView(), vote.getEpoch());
    }

    public View getView() {
        return view;
    }

    public long getEpoch() {
        return epoch;
    }

    @JsonProperty("view")
    @DsonOutput(DsonOutput.Output.ALL)
    private Long getSerializerView() {
        return this.view.number();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VoteTimeout that = (VoteTimeout) o;
        return epoch == that.epoch
                && Objects.equals(view, that.view);
    }

    @Override
    public int hashCode() {
        return Objects.hash(view, epoch);
    }

    @Override
    public String toString() {
        return String.format("%s{epoch=%s view=%s}",
                getClass().getSimpleName(), getEpoch(), getView());
    }
}
