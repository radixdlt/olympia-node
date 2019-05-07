package com.radix.regression.doublespend;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.radix.common.tuples.Pair;

public interface DoubleSpendTestConditions {
	class PostConsensusCondition {
		private final Condition<Map<ShardedAppStateId, ApplicationState>> condition;
		private final Set<Pair<String, ShardedAppStateId>> stateRequired;

		public PostConsensusCondition(
			Set<Pair<String, ShardedAppStateId>> stateRequired,
			Condition<Map<ShardedAppStateId, ApplicationState>> condition
		) {
			this.stateRequired = stateRequired;
			this.condition = condition;
		}

		public Condition<Map<ShardedAppStateId, ApplicationState>> getCondition() {
			return condition;
		}

		public Set<Pair<String, ShardedAppStateId>> getStateRequired() {
			return stateRequired;
		}
	}

	List<Action> initialActions();
	List<List<Action>> conflictingActions();
	PostConsensusCondition postConsensusCondition();
}
