package com.radixdlt.constraintmachine;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {

	final class ProcedureResult<I extends Particle, O extends Particle> {
		private final CMAction cmAction;
		private final UsedData used;
		private final String errorMessage;
		private final WitnessValidator<I> inputWitnessValidator;
		private final WitnessValidator<O> outputWitnessValidator;

		private ProcedureResult(
			CMAction cmAction,
			UsedData used,
			WitnessValidator<I> inputWitnessValidator,
			WitnessValidator<O> outputWitnessValidator,
			String errorMessage
		) {
			this.cmAction = cmAction;
			this.used = used;
			this.inputWitnessValidator = inputWitnessValidator;
			this.outputWitnessValidator = outputWitnessValidator;
			this.errorMessage = errorMessage;
		}

		public WitnessValidator<I> getInputWitnessValidator() {
			return inputWitnessValidator;
		}

		public WitnessValidator<O> getOutputWitnessValidator() {
			return outputWitnessValidator;
		}

		public static <I extends Particle, O extends Particle> ProcedureResult<I, O> popInput(
			UsedData outputUsed,
			WitnessValidator<I> inputWitnessValidator
		) {
			return new ProcedureResult<>(CMAction.POP_INPUT, outputUsed, inputWitnessValidator, null, null);
		}

		public static <I extends Particle, O extends Particle> ProcedureResult<I, O> popOutput(
			UsedData inputUsed,
			WitnessValidator<O> outputWitnessValidator
		) {
			return new ProcedureResult<>(CMAction.POP_OUTPUT, inputUsed, null, outputWitnessValidator, null);
		}

		public static <I extends Particle, O extends Particle> ProcedureResult<I, O> popInputOutput(
			WitnessValidator<I> inputWitnessValidator,
			WitnessValidator<O> outputWitnessValidator
		) {
			return new ProcedureResult<>(CMAction.POP_INPUT_OUTPUT, null, inputWitnessValidator, outputWitnessValidator, null);
		}

		public static <I extends Particle, O extends Particle> ProcedureResult<I, O> error(String errorMessage) {
			return new ProcedureResult<>(CMAction.ERROR, null, null, null, errorMessage);
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public CMAction getCmAction() {
			return cmAction;
		}

		public UsedData getUsed() {
			return used;
		}

		public ProcedureResult<Particle, Particle> toGeneric() {
			return new ProcedureResult<>(
				cmAction,
				used,
				(i, w) -> inputWitnessValidator.validate((I) i, w),
				(o, w) -> outputWitnessValidator.validate((O) o, w),
				errorMessage
			);
		}
	}

	ProcedureResult<I, O> execute(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);
}
