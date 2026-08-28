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
package io.awspring.spring.data.examples.repository;

import io.awspring.spring.data.dynamodb.repository.AllowScan;
import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.examples.model.OrderItem;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Repository
public interface OrderItemRepository extends DynamoDbRepository<OrderItem, DynamoDbCompositeId> {

	List<OrderItem> findTop2ByPkAndOrderId(String pk, String orderId);

	Window<OrderItem> findWindowByPkAndOrderId(String pk, String orderId, ScrollPosition position, Limit limit);

	@AllowScan
	@Query(filterExpression = "#name = :name", names = @ExpressionName(name = "#name", value = "productName"))
	List<OrderItem> scanByProductName(@Param("name") String productName);

	@Query(partiQl = "SELECT * FROM \"Commerce\" WHERE \"pk\" = ? AND begins_with(\"sk\", ?)")
	List<OrderItem> findWithPartiQl(String pk, String skPrefix);
}
