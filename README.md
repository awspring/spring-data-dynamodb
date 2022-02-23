# Spring Data DynamoDB

Spring Data DynamoDB brings the Spring Data programming model to Amazon DynamoDB, with first-class
support for **single-table design**: one physical table holding several item kinds, read back through
typed repositories and read-only secondary-index views.

It is deliberately **not an ORM**. DynamoDB is much closer to Cassandra's
`(partition key, clustering columns)` model than to a document store, and the API is shaped to make
that modeling explicit and readable rather than to hide it.

For a deep dive into the project, refer to the Spring Data DynamoDB documentation:

| Version                    | Reference Docs                                       | API Docs    |
|----------------------------|------------------------------------------------------|-------------|
| Spring Data DynamoDB 1.0.0 | [Reference Docs](docs/src/main/content/reference.md) | Coming soon |

## Features

- **Spring Data Repository Support**: Familiar Spring Data repository abstractions for DynamoDB
- **Query Methods**: Derive queries from method names, or write expressions explicitly with `@Query`
- **Secondary Index Views**: Read-only, typed views over Global and Local Secondary Indexes
- **Single-Table Design**: Polymorphic containers with `@InnerClass` prefix routing
- **Sort Key Templates**: Compose and decompose sort keys from several properties
- **Keyset Pagination**: `Window<T>` pagination backed by DynamoDB's `LastEvaluatedKey`
- **Optimistic Locking**: `@Version`-based conditional writes
- **PartiQL**: Execute PartiQL statements from repository methods
- **Template API**: `DynamoDbOperations` for work that does not fit a repository method
- **Event Callbacks**: Before/after save, convert, and delete callbacks

## Compatibility with Spring Project Versions

This project has dependency and transitive dependencies on Spring Projects. The table below outlines
the versions that are compatible with Spring Data DynamoDB.

| Spring Data DynamoDB | Spring Boot | Spring Framework | Spring Data Commons | AWS Java SDK | Java |
|----------------------|-------------|------------------|---------------------|--------------|------|
| 1.x                  | 4.0.x       | 7.0.x            | 4.1.x               | 2.x          | 17+  |

## Getting Started

Add the dependency to your project:

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-data-dynamodb</artifactId>
    <version>${spring-data-dynamodb.version}</version>
</dependency>
```

The module builds on the AWS SDK v2 `DynamoDbClient`. If you are not already managing the SDK
version through a BOM, import it:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-java-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Extend `AbstractDynamoDbConfiguration` and enable repositories. The only required override is the
`DynamoDbClient` bean:

```java
@Configuration
@EnableDynamoDbRepositories(basePackageClasses = TournamentRepository.class)
public class ArenaConfiguration extends AbstractDynamoDbConfiguration {

    @Bean
    @Override
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1)
                .build();
    }
}
```

Define your entity. A class-level `@Table` plus a `@PartitionKey` is the minimum:

```java
@Table(tableName = "tournament_arena")
public class Tournament {

    @PartitionKey
    private String pk;          // "TOURNAMENT#winter2026"

    @SortKey
    private String sk;          // "TOURNAMENT#winter2026"

    private String name;
    private String season;

    // getters and setters
}
```

Create a repository:

```java
public interface TournamentRepository extends DynamoDbRepository<Tournament, String> {

    List<Tournament> findByPk(String pk);

    List<Tournament> findByPkAndSkStartingWith(String pk, String prefix);
}
```

> **Note**: this module maps entities, it does **not** create tables. Create the table and its
> indexes through your infrastructure of choice (CDK, Terraform, CloudFormation).

## Advanced Features

### Secondary Index Views

`@SecondaryIndex` is a **class-level** annotation that declares a read-only view over an index. The
view's `@PartitionKey`/`@SortKey` are the *index's* keys, and the module sets `IndexName` on every
read automatically — callers never pass an index name:

```java
@SecondaryIndex(name = "GSI1", tableName = "tournament_arena")
public class PlayerMatchesView {

    @PartitionKey @Column("gsi1pk") private String collectionKey;   // "PT#winter2026#p1"
    @SortKey      @Column("gsi1sk") private String itemKey;         // "MATCH#m1"

    private String region;
}
```

`tableName` is optional when every registered `@Table` entity resolves to the same physical table.
A class is either a base-table entity or an index view — never both.

### Sort Key Templates

`@SortKeyTemplate` is declared on the **class** and composes a sort key from several properties on
write, decomposing it back on read:

```java
@Table(tableName = "tournament_arena")
@SortKeyTemplate("MATCH#{matchDate}#{matchId}")
public class Match {

    @PartitionKey
    private String pk;

    @SortKey
    private String sk;              // composed: "MATCH#2026-02-01#m1"

    private LocalDate matchDate;
    private String matchId;
}
```

### `@Query` — explicit expressions

When derivation cannot express a query, write the DynamoDB expression yourself. Parameters bind by
name via `@Param` (declared **without** the leading colon), and `@ExpressionName` maps a `#alias` to
a real attribute name, sidestepping DynamoDB's reserved words:

```java
public interface MatchRepository extends DynamoDbRepository<Match, String> {

    @Query(keyConditionExpression = "#pk = :pk AND #sk BETWEEN :from AND :to",
           names = { @ExpressionName(name = "#pk", value = "pk"),
                     @ExpressionName(name = "#sk", value = "sk") })
    List<Match> findInDateRange(@Param("pk") String pk,
                                @Param("from") String from,
                                @Param("to") String to);

    @Query(filterExpression = "#region = :region",
           indexName = "GSI1",
           names = @ExpressionName(name = "#region", value = "region"))
    List<Match> findByRegionOnIndex(@Param("region") String region);
}
```

PartiQL statements bind values positionally:

```java
@Query(partiQl = "SELECT * FROM tournament_arena WHERE pk = ?")
List<ArenaItem> findByPkWithPartiQl(String pk);
```

### Pagination

DynamoDB pages by an opaque `LastEvaluatedKey`, not a numeric offset, so `Window<T>` is the supported
paginated return type. `Page<T>` and `Slice<T>` are **rejected at bootstrap**: `Page<T>` needs a total
count DynamoDB cannot provide without reading the whole table, and `Slice<T>` relies on offset-based
paging that cannot advance over a keyset cursor.

```java
Window<PlayerInTournamentView> page = repository.findWindowByCollectionKey(
        "PT#winter2026#p1", ScrollPosition.keyset(), Limit.of(25));

if (page.hasNext()) {
    ScrollPosition next = page.positionAt(page.size() - 1);
    Window<PlayerInTournamentView> more =
            repository.findWindowByCollectionKey("PT#winter2026#p1", next, Limit.of(25));
}
```

### Template API

Inject `DynamoDbOperations` (implemented by `DynamoDbTemplate`) for work that does not fit a
repository method:

```java
operations.save(match);                    // PutItem; honours @Version
operations.insert(match);                  // PutItem, fails if the key exists
operations.saveAll(List.of(m1, m2, m3));   // BatchWriteItem, chunked at 25
operations.update(match);                  // UpdateItem from the entity's current state
operations.delete(match);
```

## Building from Source

This is a multi-module Maven project:

```
├── pom.xml                  # parent aggregator
├── spring-data-dynamodb/    # the library
└── docs/                    # reference documentation
```

```bash
# Build everything
mvn install

# Build just the library
mvn -pl spring-data-dynamodb -am install
```

Integration tests run against [LocalStack](https://localstack.cloud) via Testcontainers, so a running
Docker daemon is required for the full test suite.

Code formatting is enforced by Spotless, which runs automatically during the `compile` phase. See
[SPOTLESS.md](SPOTLESS.md) for details.

## Getting in Touch

- [Discussions on Github](https://github.com/matejnedic/spring-data-dynamodb/discussions) - the best way to discuss anything Spring Data DynamoDB related
- [Issues on Github](https://github.com/matejnedic/spring-data-dynamodb/issues) - for bug reports and feature requests

Maintainer:

- Matej Nedic [Twitter](https://twitter.com/MatejNedic1)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE.txt](LICENSE.txt) file for details.
