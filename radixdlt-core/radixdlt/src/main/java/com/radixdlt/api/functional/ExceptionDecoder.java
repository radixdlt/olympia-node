/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.functional;

import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.CallDataAccessException;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.DepletedFeeReserveException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.exceptions.InvalidDelegationException;
import com.radixdlt.constraintmachine.exceptions.InvalidHashedKeyException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.InvalidVirtualSubstateException;
import com.radixdlt.constraintmachine.exceptions.LocalSubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.MinimumStakeException;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.MissingProcedureException;
import com.radixdlt.constraintmachine.exceptions.MultipleFeeReserveDepositException;
import com.radixdlt.constraintmachine.exceptions.NotAResourceException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.ReservedSymbolException;
import com.radixdlt.constraintmachine.exceptions.ResourceAllocationAndDestructionException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.constraintmachine.exceptions.SubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.errors.RadixErrors;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.utils.functional.Failure;

import static com.fasterxml.jackson.databind.util.ClassUtil.getRootCause;

public final class ExceptionDecoder {
	private ExceptionDecoder() {
	}

	public static Failure decode(Throwable e) {
		if (e instanceof MempoolFullException) {
			return RadixErrors.UNABLE_TO_ADD_TO_MEMPOOL;
		}

		var rootCause = getRootCause(e);

		//Last resort (handles the case of standalone IllegalStateException as well)
		return RadixErrors.UNABLE_TO_SUBMIT_TX.with(extractMessage(rootCause));
	}

	public static String extractMessage(Throwable throwable) {
		return throwable.getMessage() == null ? "Unknown error" : throwable.getMessage();
	}

	//TODO: use switch expression once available
	private static Failure mapExceptionToFailure(Throwable rootCause) {
		if (rootCause instanceof CallDataAccessException) {
			return RadixErrors.ERROR_CALL_DATA;
		}

		if (rootCause instanceof ConstraintMachineException) {
			return RadixErrors.ERROR_CONSTRAINT_VIOLATION;
		}

		if (rootCause instanceof DefaultedSystemLoanException) {
			return RadixErrors.ERROR_DEFAULT_SYSTEM_LOAN;
		}

		if (rootCause instanceof DepletedFeeReserveException) {
			return RadixErrors.ERROR_NOT_ENOUGH_RESERVE;
		}

		if (rootCause instanceof ExecutionContextDestroyException) {
			return RadixErrors.ERROR_RESERVE_NOT_EMPTY;
		}

		if (rootCause instanceof InvalidDelegationException) {
			return RadixErrors.ERROR_DELEGATION_NOT_ALLOWED;
		}

		if (rootCause instanceof InvalidHashedKeyException) {
			return RadixErrors.ERROR_INVALID_HASHED_KEY;
		}

		if (rootCause instanceof InvalidPermissionException) {
			return RadixErrors.ERROR_INVALID_PERMISSION;
		}

		if (rootCause instanceof InvalidResourceException) {
			return RadixErrors.ERROR_INVALID_RESOURCE;
		}

		if (rootCause instanceof InvalidVirtualSubstateException) {
			return RadixErrors.ERROR_INVALID_VIRTUAL_SUBSTATE;
		}

		if (rootCause instanceof LocalSubstateNotFoundException) {
			return RadixErrors.ERROR_LOCAL_SUBSTATE_NOT_FOUND;
		}

		if (rootCause instanceof MinimumStakeException) {
			return RadixErrors.ERROR_MINIMUM_STAKE;
		}

		if (rootCause instanceof MismatchException) {
			return RadixErrors.ERROR_MISMATCH;
		}

		if (rootCause instanceof MissingProcedureException) {
			return RadixErrors.ERROR_MISSING_PROCEDURE;
		}

		if (rootCause instanceof MultipleFeeReserveDepositException) {
			return RadixErrors.ERROR_MULTIPLE_FEE_RESERVE_DEPOSIT;
		}

		if (rootCause instanceof NotAResourceException) {
			return RadixErrors.ERROR_NOT_A_RESOURCE;
		}

		if (rootCause instanceof NotEnoughResourcesException) {
			return RadixErrors.ERROR_NOT_ENOUGH_RESOURCES;
		}

		if (rootCause instanceof ProcedureException) {
			return RadixErrors.ERROR_PROCEDURE;
		}

		if (rootCause instanceof ReservedSymbolException) {
			return RadixErrors.ERROR_RESERVED_SYMBOL;
		}

		if (rootCause instanceof ResourceAllocationAndDestructionException) {
			return RadixErrors.ERROR_RESOURCE_ALLOCATION_AND_DESTRUCTION;
		}

		if (rootCause instanceof SignedSystemException) {
			return RadixErrors.ERROR_SIGNED_SYSTEM;
		}

		if (rootCause instanceof VirtualSubstateAlreadyDownException) {
			return RadixErrors.ERROR_VIRTUAL_SUBSTATE_ALREADY_DOWN;
		}

		if (rootCause instanceof VirtualParentStateDoesNotExist) {
			return RadixErrors.ERROR_VIRTUAL_PARENT_STATE_DOES_NOT_EXIST;
		}

		if (rootCause instanceof AuthorizationException) {
			return RadixErrors.ERROR_NOT_AUTHORIZED;
		}

		if (rootCause instanceof SubstateNotFoundException) {
			return RadixErrors.ERROR_SUBSTATE_NOT_FOUND;
		}


		return RadixErrors.UNABLE_TO_SUBMIT_TX;
	}
}
