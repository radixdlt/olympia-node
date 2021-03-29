package com.radixdlt.client.core.fungible;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Helper class for transitioning fungible particles
 * @param <T> input particle class
 * @param <U> output particle class
 */
public final class FungibleTransitionMapper<T extends Particle, U extends Particle> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<UInt256, T> inputCreator;
	private final Function<UInt256, U> outputCreator;

	public FungibleTransitionMapper(
		Function<T, UInt256> inputAmountMapper,
		Function<UInt256, T> inputCreator,
		Function<UInt256, U> outputCreator
	) {
		this.inputAmountMapper = Objects.requireNonNull(inputAmountMapper);
		this.inputCreator = Objects.requireNonNull(inputCreator);
		this.outputCreator = Objects.requireNonNull(outputCreator);
	}

	public List<ParsedInstruction> mapToParticles(
		List<T> currentParticles,
		UInt256 totalAmountToTransfer
	) throws NotEnoughFungiblesException {
		final List<ParsedInstruction> parsedInstructions = new ArrayList<>();
		parsedInstructions.add(ParsedInstruction.up(
			outputCreator.apply(totalAmountToTransfer)
		));
		UInt256 amountLeftToTransfer = totalAmountToTransfer;
		for (T p : currentParticles) {
			parsedInstructions.add(ParsedInstruction.down(p));
			UInt256 particleAmount = inputAmountMapper.apply(p);
			if (particleAmount.compareTo(amountLeftToTransfer) > 0) {
				final UInt256 sendBackToSelf = particleAmount.subtract(amountLeftToTransfer);
				parsedInstructions.add(ParsedInstruction.up(
					inputCreator.apply(sendBackToSelf)
				));
				return parsedInstructions;
			} else if (particleAmount.compareTo(amountLeftToTransfer) == 0) {
				return parsedInstructions;
			}

			amountLeftToTransfer = amountLeftToTransfer.subtract(particleAmount);
		}

		throw new NotEnoughFungiblesException(totalAmountToTransfer, totalAmountToTransfer.subtract(amountLeftToTransfer));
	}
}
