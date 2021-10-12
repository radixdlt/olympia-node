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

package com.radixdlt.spec;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.api.rpc.RpcMethodDescriptor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.TreeMap;

public class SpecGeneratorTest {
	@Test
	public void listAllMethods() throws NoSuchFieldException, IllegalAccessException {
		var methods = new TreeMap<String, TreeMap<String, RpcMethodDescriptor<?, ?>>>();

		for (var cls : RpcMethodDescriptor.class.getPermittedSubclasses()) {

			var inst = cls.getDeclaredField("INSTANCE");
			var clsInstance = (RpcMethodDescriptor<?, ?>) inst.get(null);

			methods.compute(clsInstance.endPoint().path(), (path, map) -> merge(path, map, clsInstance));
//
//			var type = (ParameterizedType) clsInstance.getResponseType().getType();
//
//			var mainResultType = ((Class<?>) type.getRawType()).getSimpleName();
//			var innerType = type.getActualTypeArguments()[0];
//
//			var innerTypeName = (innerType instanceof Class) ? ((Class<?>) innerType).getSimpleName()
//															 : ((ParameterizedType) innerType).getTypeName();
//
//			System.out.println("Class: " + cls.getSimpleName() + " " + clsInstance.method() + " at " + clsInstance.endPoint().path() + " for " + clsInstance.getRequestType().getSimpleName() + " and " +
//								   mainResultType + " " + innerTypeName);
		}

		printSpec(methods);
	}

	private void printSpec(TreeMap<String, TreeMap<String, RpcMethodDescriptor<?, ?>>> methods) {
		System.out.print(
			"""
				{
					"openrpc": "1.0.0-rc1",
					"info": {
						"title": "Radix Core JSON-RPC API",
						"version": "3.0.0"
					},
				    "methods": [
				"""
		);

		methods.forEach(this::printEndPoint);

		System.out.println("    ],");

		//TODO: print schemas

		System.out.println(
			"""
				}
				"""
		);
	}

	private TreeMap<String, RpcMethodDescriptor<?, ?>> merge(
		String path, TreeMap<String, RpcMethodDescriptor<?, ?>> map, RpcMethodDescriptor<?, ?> clsInstance
	) {
		var destMap = (map == null) ? new TreeMap<String, RpcMethodDescriptor<?, ?>>() : map;
		destMap.put(clsInstance.method(), clsInstance);
		return destMap;
	}

	private void printEndPoint(String endPoint, TreeMap<String, RpcMethodDescriptor<?, ?>> methodList) {
		methodList.forEach((methodName, methodClass) -> printSingleMethod(methodName, methodClass, endPoint));
	}

	private void printSingleMethod(String methodName, RpcMethodDescriptor<?, ?> methodClass, String endPoint) {
		//System.out.println("\t" + methodName + " -> " + methodClass.getClass().getSimpleName());
		System.out.println("        {");
		System.out.println("            \"name\": \"" + methodName + "\",");
		System.out.println("            \"servers\":\" [");
		System.out.println("                {");
		System.out.println("                    \"name\": \"" + stripLeadingSlash(endPoint) + "\",");
		System.out.println("                    \"url\": \"http://." + endPoint + "\"");
		System.out.println("                }");
		System.out.println("            ],");
		System.out.println("            \"params\": [" + extractParams(methodClass.getRequestType()) + "],");
		System.out.println("        },");
	}

	private String extractParams(Class<?> requestType) {
		var builder = new StringBuilder();

		var method = getJsonCreator(requestType);

		if (method.getParameterCount() > 0) {
			builder.append('\n');
		}

		for (var parameter : method.getParameters()) {
			var property = parameter.getAnnotation(JsonProperty.class);

			builder
				.append("                {\n")
				.append("                    \"name\": \"").append(property.value()).append("\",\n")
				.append("                    \"required\": ").append(property.required()).append(",\n")
				.append("                    \"schema\": {\n")
				.append("                        ").append(decodeTypeSchema(property, parameter)).append("\n")
				.append("                    }\n")
				.append("                },\n");
		}

		if (builder.length() > 0) {
			//append leading spaces
			builder.append("            ");
		}

		return builder.toString();
	}

	private String decodeTypeSchema(JsonProperty property, Parameter parameter) {
		var builder = new StringBuilder();

		if (parameter.getType().isAssignableFrom(long.class)
			|| parameter.getType().isAssignableFrom(int.class)
			|| parameter.getType().isAssignableFrom(Integer.class)
			|| parameter.getType().isAssignableFrom(Long.class)) {
			builder.append("\"type\": \"integer\"");
		} else if (parameter.getType().isAssignableFrom(String.class)) {
			builder.append("\"type\": \"string\"");
		} else if (parameter.getType().isAssignableFrom(boolean.class) || parameter.getType().isAssignableFrom(Boolean.class)) {
			builder.append("\"type\": \"boolean\"");
		} else if (parameter.getType().isAssignableFrom(List.class)){
			var listType = (ParameterizedType) parameter.getParameterizedType();
			var elementType = (Class<?>) listType.getActualTypeArguments()[0];

			builder.append("\"type\": \"array\",\n")
				   .append("                        \"items\": {\n")
				   .append("                            \"$ref\": \"#/components/schemas/").append(elementType.getSimpleName()).append('\n')
				   .append("                        }");
		} else {
			builder.append("\"$ref\": \"#/components/schemas/").append(parameter.getType().getSimpleName()).append('\"');
		}

		return builder.toString();
	}

	private Method getJsonCreator(Class<?> requestType) {
		for (var method : requestType.getDeclaredMethods()) {
			if (method.isAnnotationPresent(JsonCreator.class)) {
				return method;
			}
		}
		throw new IllegalStateException("Class " + requestType + " has no factory method annotated with @JsonCreator");
	}

	private String stripLeadingSlash(String endPoint) {
		return endPoint.startsWith("/") ? endPoint.substring(1) : endPoint;
	}
}