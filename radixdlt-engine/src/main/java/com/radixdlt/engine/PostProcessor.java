/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.engine;

import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.store.EngineStore;

import java.util.List;

/**
 * Post-processes executed tx batch.
 * May be used for verification (throw PostProcessorException)
 * or extending the metadata with additional info before persisting (f.e.: forks hashes).
 *
 * @param <M> class of metadata
 */
public interface PostProcessor<M> {
	/**
	 * @return new metadata to be stored
	 */
	default M process(M metadata, EngineStore<M> engineStore, List<REProcessedTxn> txns) throws PostProcessorException {
		return metadata;
	}

	static <M> PostProcessor<M> combine(PostProcessor<M>... postProcessors) {
		return new PostProcessor<>() {
			@Override
			public M process(M metadata, EngineStore<M> engineStore, List<REProcessedTxn> txns) throws PostProcessorException {
				var result = metadata;
				for (var postProcessor: postProcessors) {
					result = postProcessor.process(result, engineStore, txns);
				}
				return result;
			}
		};
	}

	static <M> PostProcessor<M> empty() {
		return new PostProcessor<>() {};
	}
}
