# Spring Data DynamoDB — Examples

A runnable Spring Boot walk-through of the `spring-data-dynamodb` programming model against a single
`Commerce` table. Everything runs locally against [LocalStack](https://www.localstack.cloud/) — no
AWS account required.

## Modeling

The examples are inspired by the AWS re:Invent 2019 talk
[Data modeling with Amazon DynamoDB (CMY304)](https://www.youtube.com/watch?v=DIQVJqiSUkE&t).

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

## Use-case classes

`ExampleRunner` only orchestrates the ordered `ExampleUseCase` beans. Each class demonstrates one
cohesive part of the programming model:

| Order | Use case | Features |
|------:|----------|----------|
| 10 | `RepositoryCrudUseCase` | Repository `save`, per-entity `saveAll`, composite keys, and `@Version` optimistic locking |
| 20 | `TemplateOperationsUseCase` | Generic `DynamoDbTemplate` results, point reads, conditional `insert`, and `BatchWriteItem` through `saveAll` |
| 30 | `DerivedQueryUseCase` | Derived key queries, `findTop`, `OrderBy`, `count`, and `exists` |
| 40 | `ExplicitQueryUseCase` | Explicit `@Query`, explicit/derived scans with `@AllowScan`, expression aliases, and PartiQL |
| 50 | `NamedQueryUseCase` | `namedQueriesLocation`, `dynamodb-named-queries.properties`, and method-level query metadata |
| 60 | `SecondaryIndexUseCase` | Typed `@SecondaryIndex` view and read-only `SecondaryIndexRepository` |
| 70 | `ItemCollectionUseCase` | One-page base-table and index item collections, fixed finders, explicit key conditions, named key conditions, and query limits |
| 80 | `PaginationUseCase` | Caller-controlled repository `Window<T>` continuation and template `DynamoDbPageRequest`/`LastEvaluatedKey` continuation |
| 90 | `EmbeddedUseCase` | Nested-map values and flattened sort-key-routed `@Embedded` rows |
| 100 | `ConversionAndVersionUseCase` | `@ValueConverter`, `@Version`, `@SortKeyTemplate`, and `@Derived` decomposition |
| 110 | `LifecycleUseCase` | Before/after entity callbacks and mapping application events |
| 120 | `UpdateDeleteUseCase` | `@Update` update expressions, state-based template updates, and deletes |

The normal named query is a **scan filter** and therefore its repository method opts in with
`@AllowScan`. The item-collection named query is a **key-condition expression**, because an item
collection must query one partition before folding its rows. Optional method-level `@Query`
metadata supplies aliases and per-request limits for both forms. Item-collection finders fold one response
page; `PaginationUseCase` shows the caller choosing whether to request another page through either a
repository `Window<T>` position or a template `LastEvaluatedKey`.

## Mapping annotations used

- `@Table` / `@PartitionKey` / `@SortKey` / `@Column` — table entities and key/attribute mapping.
- `@ItemCollectionView` + `@ItemCollectionMember` — read-side view that folds many rows of a partition (base
  table or a secondary index) into one typed object, routing each row by its sort key.
- `@Embedded` — either embed a value object as a nested map (`serializeAsNestedMap = true`) or, on
  a container entity, route rows to typed fields by sort-key `startsWith` / `endsWith` / `regex`.
