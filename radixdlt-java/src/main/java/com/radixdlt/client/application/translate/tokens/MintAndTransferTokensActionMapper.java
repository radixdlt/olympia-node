package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TransferredTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class MintAndTransferTokensActionMapper implements StatefulActionToParticleGroupsMapper {
	private final BiFunction<MintedTokensParticle, TransferredTokensParticle, List<ParticleGroup>> mintAndTransferToGroupMapper;

	public MintAndTransferTokensActionMapper() {
		this((mint, transfer) -> Arrays.asList(
			ParticleGroup.of(SpunParticle.up(mint)),
			ParticleGroup.of(SpunParticle.down(mint), SpunParticle.up(transfer))
		));
	}

	public MintAndTransferTokensActionMapper(
		BiFunction<MintedTokensParticle, TransferredTokensParticle, List<ParticleGroup>> mintAndTransferToGroupMapper) {
		this.mintAndTransferToGroupMapper = Objects.requireNonNull(mintAndTransferToGroupMapper);
	}

	@Override
	public Observable<RequiredShardState> requiredState(Action action) {
		if (!(action instanceof MintAndTransferTokensAction)) {
			return Observable.empty();
		}

		MintAndTransferTokensAction mintAndTransferTokensAction = (MintAndTransferTokensAction) action;
		RadixAddress tokenDefinitionAddress = mintAndTransferTokensAction.getTokenDefinitionReference().getAddress();

		return Observable.just(new RequiredShardState(TokenDefinitionsState.class, tokenDefinitionAddress));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintAndTransferTokensAction)) {
			return Observable.empty();
		}

		MintAndTransferTokensAction mintTransferAction = (MintAndTransferTokensAction) action;
		TokenDefinitionReference tokenDefinition = mintTransferAction.getTokenDefinitionReference();

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenDefinitionsState.class::cast)
			.map(TokenDefinitionsState::getState)
			.map(state -> getTokenStateOrError(state, tokenDefinition))
			.map(TokenState::getGranularity)
			.map(TokenUnitConversions::unitsToSubunits)
			.map(granularity -> createMint(mintTransferAction.getAmount(), granularity, tokenDefinition))
			.map(mint -> mintAndTransferToGroupMapper.apply(mint, createTransfer(mint, mintTransferAction)))
			.flatMapObservable(Observable::fromIterable);
	}

	private TokenState getTokenStateOrError(Map<TokenDefinitionReference, TokenState> m, TokenDefinitionReference tokenDefinition) {
		TokenState ts = m.get(tokenDefinition);
		if (ts == null) {
			throw new UnknownTokenException(tokenDefinition);
		}
		return ts;
	}

	private TransferredTokensParticle createTransfer(MintedTokensParticle mint, MintAndTransferTokensAction action) {
		return new TransferredTokensParticle(
			mint.getAmount(),
			mint.getGranularity(),
			action.getTo(),
			System.nanoTime(),
			action.getTokenDefinitionReference(),
			System.currentTimeMillis() / 60000L + 60000L
		);
	}

	private MintedTokensParticle createMint(BigDecimal amount, UInt256 granularity, TokenDefinitionReference tokenDefinition) {
		return new MintedTokensParticle(
			UInt256s.fromBigDecimal(amount),
			granularity,
			tokenDefinition.getAddress(),
			System.currentTimeMillis(),
			tokenDefinition,
			System.currentTimeMillis() / 60000L + 60000);
	}
}
