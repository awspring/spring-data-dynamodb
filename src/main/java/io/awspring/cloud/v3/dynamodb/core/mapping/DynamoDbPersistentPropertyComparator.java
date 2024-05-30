package io.awspring.cloud.v3.dynamodb.core.mapping;

import java.util.Comparator;
import java.util.Objects;

public enum DynamoDbPersistentPropertyComparator implements Comparator<DynamoDbPersistentProperty> {

	/**
	 * Comparator instance.
	 */
	INSTANCE;

	@Override
	public int compare(DynamoDbPersistentProperty left, DynamoDbPersistentProperty right) {

		if (left == null && right == null) {
			return 0;
		} else if (left != null && right == null) {
			return 1;
		} else if (left == null) {
			return -1;
		} else if (left.equals(right)) {
			return 0;
		}
		return Objects.requireNonNull(left.getColumnName()).compareTo(Objects.requireNonNull(right.getColumnName()));
	}

}
