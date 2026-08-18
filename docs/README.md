# Spring Data DynamoDB — Documentation

This module holds the reference documentation for **Spring Data DynamoDB**, the Spring Data
repository support for Amazon DynamoDB.

Spring Data DynamoDB brings the Spring Data programming model to Amazon DynamoDB, with first-class
support for **single-table design**: one physical table holding several item kinds, read back
through typed repositories and read-only secondary-index views. It is deliberately **not an ORM** —
the API keeps DynamoDB's `(partition key, sort key)` modeling explicit rather than hiding it.

## Contents

- [Reference Guide](src/main/content/reference.md) — the complete guide, covering:
  - Getting started (dependencies, configuration, table creation)
  - Mapping entities (`@Table`, `@PartitionKey`, `@SortKey`, `@Column`, `@Version`, `@InnerClass`,
    `@SortKeyTemplate`, `@AggregateTable`, discriminators)
  - Secondary index views (typed and polymorphic, multi-attribute keys, LSIs)
  - Repositories (`DynamoDbRepository`, composite ids, `SecondaryIndexRepository`,
    `AggregateRepository`, derived queries, `@AllowScan`, `findFirst`/`findTop`, pagination)
  - `@Query` — explicit key conditions, filter expressions, `@Modifying` updates, and PartiQL

## Building the docs

This module is part of the `spring-data-dynamodb-parent` multi-module build. It is a documentation
module only — it produces no deployable Maven artifact (both `install` and `deploy` are skipped).

From the repository root:

```bash
# Validate the whole reactor, including this module
mvn validate

# Build everything except the docs module
mvn -pl spring-data-dynamodb -am install
```

## Layout

```
docs/
├── pom.xml                       # docs module (packaging: pom, deploy skipped)
├── README.md                     # this index
└── src/main/content/
    └── reference.md              # full reference guide
```

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE.txt](../LICENSE.txt) file
for details.
