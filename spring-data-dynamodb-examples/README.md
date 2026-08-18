# Spring Data DynamoDB — Examples

A runnable Spring Boot walk-through of the `spring-data-dynamodb` programming model against a
single `Commerce` table modelled in the [Alex DeBrie](https://www.dynamodbbook.com/) single-table
style. Everything runs locally against [LocalStack](https://www.localstack.cloud/) — no AWS account
required.

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker (to run LocalStack)
- `curl` (used by the table-creation script)

## 1. Start LocalStack

LocalStack exposes the DynamoDB API on `http://localhost:4566`.

```bash
docker run --rm -it -p 4566:4566 localstack/localstack
```

Or with Docker Compose:

```yaml
services:
  localstack:
    image: localstack/localstack
    ports:
      - "4566:4566"
    environment:
      - SERVICES=dynamodb
```

```bash
docker compose up
```

## 2. Create the table

The script creates the `Commerce` table (partition key `pk`, sort key `sk`) with two overloaded
global secondary indexes, `GSI1` and `GSI2`.

```bash
bash src/main/resources/create-tables.sh
```

## 3. Run the example

`DynamoDbConfig` points the `DynamoDbClient` at `http://localhost:4566` in region `eu-central-1`
with dummy `test`/`test` credentials. Run the Spring Boot entry point `DynamoDbApplication` from
your IDE (or add the `spring-boot-maven-plugin` and use `mvn spring-boot:run`). On startup the
`ExampleRunner` executes each demonstration in order and prints the results.

## Data model (single table)

A customer's data forms one item collection under `pk = CUSTOMER#<id>`:

| Row      | `pk`             | `sk`                          | `GSI1` (`gsi1pk`/`gsi1sk`)      | `GSI2` (`gsi2pk`/`gsi2sk`)     |
|----------|------------------|-------------------------------|---------------------------------|--------------------------------|
| Customer | `CUSTOMER#<id>`  | `#PROFILE`                    | `EMAIL#<email>` / `#PROFILE`    | —                              |
| Order    | `CUSTOMER#<id>`  | `ORDER#<orderId>`             | `STATUS#<status>` / `<createdAt>` | `ORDER#<orderId>` / `ORDER#<orderId>` |
| OrderItem| `CUSTOMER#<id>`  | `ORDER#<orderId>#ITEM#<sku>`  | `PRODUCT#<sku>` / `ORDER#<orderId>` | `ORDER#<orderId>` / `ITEM#<sku>`  |

- `GSI1` is an inverted index for single-attribute lookups (orders by status, items by product,
  customer by email).
- `GSI2` is an order-collection index: an order header and its line items share
  `gsi2pk = ORDER#<orderId>`, so the whole order can be folded from the index.

## What `ExampleRunner` demonstrates

1. **Repository CRUD** — `save`, `saveAll` (batch write).
2. **Template writes** — `DynamoDbTemplate.save` with the write result.
3. **Repository point reads** — `findById` / `existsById` with `DynamoDbCompositeId.of(pk, sk)`.
4. **Template point read** — `findById(pk, sk, type)`.
5. **Polymorphic item-collection query** — `queryPolymorphic` returns the mixed order + item rows.
6. **GSI query** — orders by status through `GSI1`.
7. **Aggregate on the base table** — `@AggregateTable(partitionKey = "pk", sortKey = "sk")` folds
   the customer partition into a typed `AggregateOrder` (profile + order + items).
8. **Aggregate on an index** — `@AggregateTable(indexName = "GSI2", ...)` folds the `GSI2` order
   collection into `OrderByIndex` (order header + line items).
9. **`@InnerClass` embedded value** — `Customer.address` uses
   `@InnerClass(serializeAsNestedMap = true)` to store the `Address` as a nested map on the same row.
10. **`@InnerClass` write-side container** — `CustomerAccount` (partition `ACCOUNT#<id>`) routes rows
    to typed fields by sort-key prefix (`PROFILE#` / `PAYMENT#` / `NOTE#`). Each `save` writes one
    row; `findByPk` reconstructs every row into whichever field its prefix matches.
11. **Pagination** — page through the order items with `DynamoDbPageRequest` and the returned
    `LastEvaluatedKey`.
12. **Update + delete** — update an order (keeping its GSI attributes in sync) and delete an item.

## Mapping annotations used

- `@Table` / `@PartitionKey` / `@SortKey` / `@Column` — table entities and key/attribute mapping.
- `@AggregateTable` + `@AggregateItem` — read-side view that folds many rows of a partition (base
  table or a secondary index) into one typed object, routing each row by its sort key.
- `@InnerClass` — either embed a value object as a nested map (`serializeAsNestedMap = true`) or, on
  a container entity, route rows to typed fields by sort-key `startsWith` / `endsWith` / `regex`.
