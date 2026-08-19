/*
 * Copyright 2013-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.awspring.spring.data.dynamodb.core;

import java.util.Arrays;
import java.util.function.Consumer;
import org.springframework.data.domain.ManagedTypes;
import org.springframework.util.Assert;

/**
 * To be implemented for AOT support. Currently useless.
 */
/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class DynamoDbManagedTypes implements ManagedTypes {

	private final ManagedTypes delegate;

	private DynamoDbManagedTypes(ManagedTypes types) {
		this.delegate = types;
	}

	public static DynamoDbManagedTypes from(ManagedTypes managedTypes) {
		return new DynamoDbManagedTypes(managedTypes);
	}

	public static DynamoDbManagedTypes from(Class<?>... types) {
		return fromIterable(Arrays.asList(types));
	}

	public static DynamoDbManagedTypes fromIterable(Iterable<? extends Class<?>> types) {
		Assert.notNull(types, "Types must not be null");
		return from(ManagedTypes.fromIterable(types));
	}

	public static DynamoDbManagedTypes empty() {
		return from(ManagedTypes.empty());
	}

	@Override
	public void forEach(Consumer<Class<?>> action) {
		delegate.forEach(action);
	}

}
