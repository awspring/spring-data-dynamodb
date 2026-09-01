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

import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Update;
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderStatus;
import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Repository
public interface OrderRepository extends DynamoDbRepository<Order, DynamoDbCompositeId> {

	List<Order> findByPkOrderBySkDesc(String pk);

	List<Order> findTop1ByPkOrderBySkDesc(String pk);

	long countByPk(String pk);

	boolean existsByPk(String pk);

	@Update(updateExpression = "SET #status = :status, #gsi1pk = :gsi1pk", names = {
			@ExpressionName(name = "#status", value = "status"), @ExpressionName(name = "#gsi1pk", value = "gsi1pk") })
	Order changeStatus(@Param("pk") String pk, @Param("sk") String sk, @Param("status") OrderStatus status,
			@Param("gsi1pk") String gsi1pk);
}
