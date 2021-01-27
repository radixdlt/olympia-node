package com.radix.regression.doublespend;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.radixdlt.utils.Pair;
import org.assertj.core.api.Condition;

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

	class BatchedActions {
		private final ImmutableList<Action> actions;
		public BatchedActions(Action... actions) {
			this.actions = ImmutableList.copyOf(actions);
		}

		public BatchedActions(Action action) {
			this.actions = ImmutableList.of(action);
		}

		public List<Action> getActions() {
			return actions;
		}
	}

	List<BatchedActions> initialActions();
	List<List<BatchedActions>> conflictingActions();
	PostConsensusCondition postConsensusCondition();
}
