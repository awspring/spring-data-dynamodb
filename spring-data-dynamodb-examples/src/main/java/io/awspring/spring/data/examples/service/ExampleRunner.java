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
package io.awspring.spring.data.examples.service;

import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.core.EntityWriteResult;
import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.examples.model.AccountNote;
import io.awspring.spring.data.examples.model.AccountProfile;
import io.awspring.spring.data.examples.model.Address;
import io.awspring.spring.data.examples.model.AggregateOrder;
import io.awspring.spring.data.examples.model.Customer;
import io.awspring.spring.data.examples.model.CustomerAccount;
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderByIndex;
import io.awspring.spring.data.examples.model.OrderItem;
import io.awspring.spring.data.examples.model.OrderStatus;
import io.awspring.spring.data.examples.model.PaymentMethod;
import io.awspring.spring.data.examples.repository.AggregateOrderRepository;
import io.awspring.spring.data.examples.repository.CustomerAccountRepository;
import io.awspring.spring.data.examples.repository.CustomerRepository;
import io.awspring.spring.data.examples.repository.OrderByIndexRepository;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import io.awspring.spring.data.examples.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Component
public class ExampleRunner implements CommandLineRunner {

	private static final String CUSTOMER_ID = "12345";
	private static final String ORDER_ID = "1321";
	private static final String CUSTOMER_PK = "CUSTOMER#" + CUSTOMER_ID;
	private static final String ACCOUNT_PK = "ACCOUNT#" + CUSTOMER_ID;
	private static final String PROFILE_SK = "#PROFILE";
	private static final String ORDER_SK = "ORDER#" + ORDER_ID;
	private static final String ORDER_COLLECTION = "ORDER#" + ORDER_ID;
	private static final String ITEM_PREFIX = ORDER_SK + "#ITEM#";
	private static final String TABLE_NAME = "Commerce";
	private static final String STATUS_INDEX = "GSI1";

	private final CustomerRepository customerRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final AggregateOrderRepository aggregateOrderRepository;
	private final OrderByIndexRepository orderByIndexRepository;
	private final CustomerAccountRepository customerAccountRepository;
	private final DynamoDbTemplate template;

	public ExampleRunner(CustomerRepository customerRepository, OrderRepository orderRepository,
			OrderItemRepository orderItemRepository, AggregateOrderRepository aggregateOrderRepository,
			OrderByIndexRepository orderByIndexRepository, CustomerAccountRepository customerAccountRepository,
			DynamoDbTemplate template) {
		this.customerRepository = customerRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.aggregateOrderRepository = aggregateOrderRepository;
		this.orderByIndexRepository = orderByIndexRepository;
		this.customerAccountRepository = customerAccountRepository;
		this.template = template;
	}

	@Override
	public void run(String... args) {
		saveWithRepositories();
		saveWithTemplate();
		pointReadsWithRepositories();
		pointReadWithTemplate();
		queryItemCollectionPolymorphically();
		queryByGlobalSecondaryIndex();
		readCustomerAggregate();
		readOrderAggregateByIndex();
		showInnerClassEmbeddedAddress();
		saveAndReadPolymorphicContainer();
		paginateOrderItems();
		updateAndDelete();
	}

	private void saveWithRepositories() {
		banner("1. Saving through repositories (Spring Data CRUD)");

		customerRepository
				.save(Customer.of(CUSTOMER_ID, "Matej", "matej@example.com", Address.of("1 Ilica", "Zagreb", "HR")));
		orderRepository.save(Order.of(CUSTOMER_ID, ORDER_ID, Instant.now(), OrderStatus.SHIPPED,
				new BigDecimal("129.99"), Address.of("10 Warehouse Rd", "Split", "HR")));
		orderItemRepository
				.saveAll(List.of(OrderItem.of(CUSTOMER_ID, ORDER_ID, "WIDGET-1", "Widget", 2, new BigDecimal("19.99")),
						OrderItem.of(CUSTOMER_ID, ORDER_ID, "GADGET-9", "Gadget", 1, new BigDecimal("89.99"))));

		System.out.println("saved customer + order + 2 items");
	}

	private void saveWithTemplate() {
		banner("2. Saving through DynamoDbTemplate");

		EntityWriteResult<OrderItem> result = template
				.save(OrderItem.of(CUSTOMER_ID, ORDER_ID, "CABLE-3", "Cable", 3, new BigDecimal("4.99")));

		System.out.println("template.save -> " + result.getEntity().getSk());
	}

	private void pointReadsWithRepositories() {
		banner("3. Point reads through repositories (composite id)");

		Optional<Customer> customer = customerRepository.findById(DynamoDbCompositeId.of(CUSTOMER_PK, PROFILE_SK));
		customer.ifPresent(
				c -> System.out.println("findById customer -> " + c.getUsername() + " <" + c.getEmail() + ">"));

		boolean orderExists = orderRepository.existsById(DynamoDbCompositeId.of(CUSTOMER_PK, ORDER_SK));
		System.out.println("order exists -> " + orderExists);
	}

	private void pointReadWithTemplate() {
		banner("4. Point read through DynamoDbTemplate");

		Order order = template.findById(CUSTOMER_PK, ORDER_SK, Order.class);
		System.out.println("template.findById order -> status=" + (order != null ? order.getStatus() : null));
		if (order != null && order.getShippingAddress() != null) {
			System.out.println("   shipping -> " + order.getShippingAddress().getCity());
		}
	}

	private void queryItemCollectionPolymorphically() {
		banner("5. Item-collection query (polymorphic single-table read)");

		DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request()
				.withKeyConditionExpression("#pk = :pk AND begins_with(#sk, :order)")
				.withExpressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
				.withExpressionAttributeValues(Map.of(":pk", CUSTOMER_PK, ":order", ORDER_SK)).build();

		List<Object> rows = template.queryPolymorphic(TABLE_NAME, request, null).getEntity();
		System.out.println("order-detail rows (order + items) -> " + rows.size());
		rows.forEach(row -> System.out.println("   " + row.getClass().getSimpleName()));
	}

	private void queryByGlobalSecondaryIndex() {
		banner("6. GSI query (orders by status via " + STATUS_INDEX + ")");

		DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request().withIndexName(STATUS_INDEX)
				.withKeyConditionExpression("#g = :status").withExpressionAttributeNames(Map.of("#g", "gsi1pk"))
				.withExpressionAttributeValues(Map.of(":status", "STATUS#" + OrderStatus.SHIPPED)).build();

		List<Order> shipped = template.query(Order.class, request, null).getEntity();
		System.out.println("SHIPPED orders -> " + shipped.size());
		shipped.forEach(o -> System.out.println("   " + o.getSk() + " total=" + o.getTotal()));
	}

	private void readCustomerAggregate() {
		banner("7. Aggregate read on the base table (@AggregateTable)");

		Optional<AggregateOrder> whole = aggregateOrderRepository.findByPartitionKey(CUSTOMER_PK);
		whole.ifPresent(a -> {
			System.out.println("customer -> " + (a.getCustomer() != null ? a.getCustomer().getUsername() : null));
			System.out.println("order    -> " + (a.getOrder() != null ? a.getOrder().getStatus() : null));
			System.out.println("items    -> " + (a.getOrderItemList() != null ? a.getOrderItemList().size() : 0));
		});

		Optional<AggregateOrder> itemsOnly = aggregateOrderRepository
				.findByPartitionKeyAndSortKeyStartingWith(CUSTOMER_PK, ITEM_PREFIX);
		itemsOnly.ifPresent(a -> System.out.println(
				"items-only aggregate -> " + (a.getOrderItemList() != null ? a.getOrderItemList().size() : 0)));
	}

	private void readOrderAggregateByIndex() {
		banner("8. Aggregate read on an index (@AggregateTable indexName = GSI2)");

		Optional<OrderByIndex> view = orderByIndexRepository.findByPartitionKey(ORDER_COLLECTION);
		view.ifPresent(v -> {
			System.out.println("order -> " + (v.getOrder() != null ? v.getOrder().getStatus() : null));
			System.out.println("items -> " + (v.getItems() != null ? v.getItems().size() : 0));
		});
	}

	private void showInnerClassEmbeddedAddress() {
		banner("9. @InnerClass embedded value (serializeAsNestedMap)");

		Optional<Customer> customer = customerRepository.findById(DynamoDbCompositeId.of(CUSTOMER_PK, PROFILE_SK));
		customer.map(Customer::getAddress).ifPresent(address -> System.out
				.println("address -> " + address.getStreet() + ", " + address.getCity() + ", " + address.getCountry()));
	}

	private void saveAndReadPolymorphicContainer() {
		banner("10. @InnerClass write-side container (polymorphic rows via startsWith)");

		customerAccountRepository.save(CustomerAccount.profile(CUSTOMER_ID, AccountProfile.of("Matej", "GOLD")));
		customerAccountRepository.save(CustomerAccount.payment(CUSTOMER_ID, "pm-1", PaymentMethod.of("VISA", "4242")));
		customerAccountRepository.save(CustomerAccount.payment(CUSTOMER_ID, "pm-2", PaymentMethod.of("AMEX", "0005")));
		customerAccountRepository.save(CustomerAccount.note(CUSTOMER_ID, "n-1", AccountNote.of("VIP customer")));

		List<CustomerAccount> rows = customerAccountRepository.findByPk(ACCOUNT_PK);
		System.out.println("account rows -> " + rows.size());
		rows.forEach(row -> {
			if (row.getProfile() != null) {
				System.out.println(
						"   PROFILE -> " + row.getProfile().getDisplayName() + " (" + row.getProfile().getTier() + ")");
			}
			else if (row.getPayment() != null) {
				System.out.println(
						"   PAYMENT -> " + row.getPayment().getBrand() + " ****" + row.getPayment().getLast4());
			}
			else if (row.getNote() != null) {
				System.out.println("   NOTE    -> " + row.getNote().getText());
			}
		});
	}

	private void paginateOrderItems() {
		banner("11. Pagination (page through the order items)");

		DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request()
				.withKeyConditionExpression("#pk = :pk AND begins_with(#sk, :prefix)")
				.withExpressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
				.withExpressionAttributeValues(Map.of(":pk", CUSTOMER_PK, ":prefix", ITEM_PREFIX)).build();

		Map<String, Object> cursor = null;
		int page = 0;
		do {
			DynamoDbPageRequest pageRequest = (cursor == null) ? DynamoDbPageRequest.of(2)
					: DynamoDbPageRequest.of(2, cursor);
			EntityQueryResult<List<OrderItem>> result = template.query(OrderItem.class, request, pageRequest);
			cursor = result.getLastEvaluatedKey();
			System.out.println("page " + (++page) + " -> " + result.getEntity().size() + " item(s)");
		}
		while (cursor != null && !cursor.isEmpty());
	}

	private void updateAndDelete() {
		banner("12. Update + delete");

		Order order = template.findById(CUSTOMER_PK, ORDER_SK, Order.class);
		if (order != null) {
			order.setStatus(OrderStatus.DELIVERED);
			order.setGsi1pk("STATUS#" + OrderStatus.DELIVERED);
			template.update(order);
			System.out.println("order updated -> " + OrderStatus.DELIVERED);
		}

		orderItemRepository.delete(OrderItem.of(CUSTOMER_ID, ORDER_ID, "CABLE-3", "Cable", 3, new BigDecimal("4.99")));
		System.out.println("deleted item CABLE-3");
	}

	private static void banner(String title) {
		System.out.println();
		System.out.println("=== " + title + " ===");
	}
}
