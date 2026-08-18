/*
 * Copyright 2013-2026 the original author or authors.
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
package io.awspring.cloud.dynamodb.core.mapping;

import java.util.Comparator;
import java.util.Objects;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public enum DynamoDbPersistentPropertyComparator implements Comparator<DynamoDbPersistentProperty> {

	INSTANCE;

	@Override
	public int compare(DynamoDbPersistentProperty left, DynamoDbPersistentProperty right) {

		if (left == null && right == null) {
			return 0;
		}
		else if (left != null && right == null) {
			return 1;
		}
		else if (left == null) {
			return -1;
		}
		else if (left.equals(right)) {
			return 0;
		}
		return Objects.requireNonNull(left.getColumnName()).compareTo(Objects.requireNonNull(right.getColumnName()));
	}

}
