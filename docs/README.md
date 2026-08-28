# Spring Data DynamoDB — Documentation

This module holds the reference documentation for **Spring Data DynamoDB**, the Spring Data
repository support for Amazon DynamoDB.

Spring Data DynamoDB brings the Spring Data programming model to Amazon DynamoDB, with first-class
support for **single-table design**: one physical table holding several item kinds, read back
through typed repositories and read-only secondary-index views. It is deliberately **not an ORM** —
the API keeps DynamoDB's `(partition key, sort key)` modeling explicit rather than hiding it.

## Contents

- [Reference Guide](src/main/asciidoc/reference.adoc) — the complete guide, covering:
  - Getting started (dependencies, configuration, table creation)
  - Mapping entities (`@Table`, `@PartitionKey`, `@SortKey`, `@Column`, `@Version`, `@InnerClass`,
    `@SortKeyTemplate`, `@ItemCollectionView`, `@ItemCollectionMember`)
  - Secondary index views (typed views, multi-attribute keys, LSIs)
  - Repositories (`DynamoDbRepository`, composite ids, `SecondaryIndexRepository`,
    `ItemCollectionRepository`, derived queries, `@AllowScan`, `findFirst`/`findTop`, pagination)
  - `@Query` — explicit key conditions, filter expressions, and PartiQL
  - `@Update` — single-item update and condition expressions

## Building the docs

This module is part of the `spring-data-dynamodb-parent` multi-module build. It is a documentation
module only — it produces no deployable Maven artifact (both `install` and `deploy` are skipped).

From the repository root:

```bash
# Build the reference guide and aggregated API documentation
make docs

# Validate the whole reactor, including this module
mvn validate

# Build everything except the docs module
mvn -pl spring-data-dynamodb -am install
```

The rendered reference guide is written to
`target/generated-docs/reference/html/reference.html` (relative to this module), and the aggregated
API documentation is written to `../target/site/apidocs/index.html`. The build checks formatting
without applying changes.

## Layout

```
docs/
├── pom.xml                       # docs module (packaging: pom, deploy skipped)
├── README.md                     # this index
└── src/main/asciidoc/
    └── reference.adoc              # full reference guide
```

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE.txt](../LICENSE.txt) file
for details.
