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

import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.ItemCollectionRepository;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.examples.model.OrderItemCollection;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Repository
public interface OrderItemCollectionRepository extends ItemCollectionRepository<OrderItemCollection> {

	@Query(keyConditionExpression = "#pk = :pk AND begins_with(#sk, :prefix)", limit = 25, names = {
			@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk") })
	Optional<OrderItemCollection> findExplicit(@Param("pk") String pk, @Param("prefix") String prefix);

	@Query(limit = 25, names = { @ExpressionName(name = "#pk", value = "pk"),
			@ExpressionName(name = "#sk", value = "sk") })
	Optional<OrderItemCollection> findNamed(@Param("pk") String pk, @Param("prefix") String prefix);
}
