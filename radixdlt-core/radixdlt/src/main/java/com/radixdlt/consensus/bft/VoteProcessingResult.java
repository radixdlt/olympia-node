/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;

import java.util.Objects;

/**
 * The result of processing a received vote.
 */
public interface VoteProcessingResult {

    static VoteAccepted accepted() {
        return VoteAccepted.INSTANCE;
    }

    static VoteRejected rejected(VoteRejected.VoteRejectedReason reason) {
        return new VoteRejected(reason);
    }

    static QuorumReached quorum(ViewVotingResult result) {
        return new QuorumReached(result);
    }

    static QuorumReached qcQuorum(QuorumCertificate qc) {
        return quorum(ViewVotingResult.qc(qc));
    }

    static QuorumReached tcQuorum(TimeoutCertificate tc) {
        return quorum(ViewVotingResult.tc(tc));
    }

    /**
     * Signifies that a vote has been accepted, but the quorum hasn't been reached.
     */
    final class VoteAccepted implements VoteProcessingResult {

        public static final VoteAccepted INSTANCE = new VoteAccepted();

        private VoteAccepted() {
        }

        @Override
        public String toString() {
            return "VoteAccepted";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && getClass() == o.getClass();
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /**
     * Signifies that a vote has been rejected.
     */
    final class VoteRejected implements VoteProcessingResult {
        public enum VoteRejectedReason {
            INVALID_AUTHOR, DUPLICATE_VOTE
        }

        private final VoteRejectedReason reason;

        public VoteRejected(VoteRejectedReason reason) {
            this.reason = Objects.requireNonNull(reason);
        }

        public VoteRejectedReason getReason() {
            return this.reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VoteRejected that = (VoteRejected) o;
            return reason == that.reason;
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason);
        }

        @Override
        public String toString() {
            return String.format("VoteRejected{reason=%s}", reason);
        }
    }

    /**
     * Signifies that a vote has been accepted and quorum has been reached.
     */
    final class QuorumReached implements VoteProcessingResult {

        private final ViewVotingResult viewVotingResult;

        public QuorumReached(ViewVotingResult viewVotingResult) {
            this.viewVotingResult = Objects.requireNonNull(viewVotingResult);
        }

        public ViewVotingResult getViewVotingResult() {
            return this.viewVotingResult;
        }

        @Override
        public String toString() {
            return String.format("QuorumReached{votingResult=%s}", viewVotingResult);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QuorumReached that = (QuorumReached) o;
            return Objects.equals(viewVotingResult, that.viewVotingResult);
        }

        @Override
        public int hashCode() {
            return Objects.hash(viewVotingResult);
        }
    }
}
