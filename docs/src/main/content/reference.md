# Spring Data DynamoDB

Spring Data DynamoDB brings the Spring Data programming model to Amazon DynamoDB, with first-class
support for **single-table design**: one physical table holding several item kinds, read back
through typed repositories and read-only secondary-index views.

It is deliberately **not an ORM**. DynamoDB is much closer to Cassandra's
`(partition key, clustering columns)` model than to a document store, and the API is shaped to make
that modeling explicit and readable rather than to hide it.

Every example in this guide builds one running domain: an **esports tournament arena**.

## Table of Contents

1. Getting started
   1.1. Dependencies
   1.2. Configuration
   1.3. Creating the table
2. Mapping entities
   2.1. `@Table`, `@PartitionKey`, `@SortKey`
   2.2. `@Column`
   2.3. `@Version`
   2.4. `@InnerClass`
   2.5. `@SortKeyTemplate`
   2.6. `@ItemCollectionView`
3. Secondary index views
   3.1. A typed view
   3.2. A heterogeneous container view
   3.3. Multi-attribute index keys
   3.4. Local secondary indexes
   3.5. Views are read-only
4. Repositories
   4.1. `DynamoDbRepository`
   4.2. Composite ids
   4.3. `SecondaryIndexRepository`
   4.4. Derived queries and the key-condition rules
   4.5. `@AllowScan`
   4.6. Limiting queries: `findFirst` / `findTop`
   4.7. Pagination
   4.8. `ItemCollectionRepository`
5. `@Query` — explicit expressions
   5.1. Key conditions and filters
   5.2. `@Update` — single-item updates
   5.3. PartiQL
6. `DynamoDbOperations` — the template API
   6.1. Writes
   6.2. Reads
   6.3. `IndexQueryBuilder`
7. Lifecycle callbacks and events
8. Custom conversions
9. Exception translation
10. Annotation summary

---

## 1. Getting started

### 1.1. Dependencies

Add the module dependency:

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-data-dynamodb</artifactId>
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

### 1.2. Configuration

Extend `AbstractDynamoDbConfiguration` and enable repositories. The only required override is the
`DynamoDbClient` bean:

```java
@Configuration
@EnableDynamoDbRepositories(basePackageClasses = ArenaItemRepository.class)
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

The base class contributes the rest as overridable `@Bean` methods:

| Bean method                     | Purpose                                                          |
|---------------------------------|------------------------------------------------------------------|
| `dynamoDbClient()`              | The AWS SDK v2 client. Override to configure region/credentials. |
| `dynamoDbTemplate(...)`         | The low-level `DynamoDbOperations` entry point.                  |
| `dynamoDbConverter()`           | Entity ⇄ item mapping.                                           |
| `dynamoDbMappingContext()`      | Entity metadata; scans `getEntityBasePackages()`.                |
| `dynamoDbExceptionTranslator()` | Maps DynamoDB exceptions to Spring's `DataAccessException`.      |
| `customConversions()`           | The `DynamoDbConversions` bean; override the `customConverters()` hook to register converters. |
| `propertyValueConversions()`    | Register per-property `@ValueConverter` implementations.         |

By default entities are discovered by scanning the configuration class's own package. Override
`getEntityBasePackages()` to change that.

`@EnableDynamoDbRepositories` also exposes the main Spring Data repository controls:

| Attribute | Purpose |
|---|---|
| `namedQueriesLocation` | Classpath location of a named-query properties file (section 5.4). |
| `dynamoDbOperationsRef` | Bean name of the `DynamoDbOperations` instance repositories should use; useful when several templates exist. |
| `considerNestedRepositories` | Whether nested repository interfaces are included during scanning. |
| `queryLookupStrategy` | `CREATE` derives only, `USE_DECLARED_QUERY` requires a named or `@Query` declaration, and `CREATE_IF_NOT_FOUND` tries declarations before derivation. |
| `bootstrapMode` | Controls when repository beans are initialized (`DEFAULT`, `DEFERRED`, or `LAZY`). |

### 1.3. Creating the table

This module maps entities; it does **not** create tables. Create `tournament_arena` through your
infrastructure of choice (CDK, Terraform, CloudFormation) with:

* base key: `pk` (HASH) + `sk` (RANGE)
* GSI `GSI1`: `gsi1pk` (HASH) + `gsi1sk` (RANGE)
* LSI `by_region`: `pk` (HASH) + `region` (RANGE)

---

## 2. Mapping entities

### 2.1. `@Table`, `@PartitionKey`, `@SortKey`

The minimum viable entity is a class-level `@Table` plus a partition key.

```java
@Table(tableName = "tournament_arena")
public class Tournament {

    @PartitionKey
    private String pk;          // "TOURNAMENT#winter2026"

    @SortKey
    private String sk;          // "TOURNAMENT#winter2026"

    private String name;
    private String season;
}
```

A DynamoDB **base table** has exactly one partition key and at most one sort key. The verifier
enforces this at bootstrap: an entity with no `@PartitionKey`, or with two `@SortKey` properties,
fails fast when the application context starts rather than on first query. An entity that declares
both `@Table` and `@SecondaryIndex` is likewise rejected — a class is either a base-table entity or
a read-only index view, never both.

`@PartitionKey` is meta-annotated with `@Id`, so Spring Data's `isIdProperty()` resolves the
base-table partition key as the entity's id.

### 2.2. `@Column` — naming the physical attribute

By default an attribute takes the Java property's name. Use `@Column` when they differ:

```java
@Table(tableName = "tournament_arena")
public class Player {

    @PartitionKey
    @Column("pk")
    private String tournamentKey;      // stored as attribute "pk"

    @SortKey
    @Column("sk")
    private String playerKey;          // stored as attribute "sk"

    @Column("display_name")
    private String displayName;
}
```

`@Column` composes with the key annotations: on a key property the key annotation's own `value()`
wins if set, otherwise `@Column` applies, otherwise the property name is used. All three of these
map to the attribute `pk`:

```java
@PartitionKey("pk")         private String tournamentKey;   // key annotation's value()
@PartitionKey @Column("pk") private String tournamentKey;   // @Column
@PartitionKey               private String pk;              // property name
```

### 2.3. `@Version` — optimistic locking

Annotate a numeric property with Spring Data's `@Version`:

```java
@Table(tableName = "tournament_arena")
public class Tournament {

    @PartitionKey private String pk;
    @SortKey      private String sk;

    @Version
    private Long version;
}
```

`save()` then writes with a condition expression: `attribute_not_exists(version)` for a new item, or
`version = <previous>` for an update. A concurrent modification surfaces as
`OptimisticLockingFailureException`.

Note that DynamoDB's `BatchWriteItem` cannot carry condition expressions, so the template's
`saveAll()` **rejects** entities with a `@Version` property entirely — an
`InvalidDataAccessApiUsageException` is thrown. Use `save()` per item where the guard matters.

### 2.4. `@InnerClass` — nested objects and single-table polymorphism

`@InnerClass` **flattens** an embedded object's columns into the owning item rather than nesting
them under an attribute. That is what makes single-table design work: one physical table holds
several item kinds, and each row populates only the field whose shape it actually is.

```java
@Table(tableName = "tournament_arena")           // one container, four item kinds
public class ArenaItem {

    @PartitionKey @Column("pk") private String pk;
    @SortKey      @Column("sk") private String sk;

    @Column("gsi1pk") private String gsi1pk;     // plain columns; see section 3
    @Column("gsi1sk") private String gsi1sk;

    @InnerClass(startsWith = "TOURNAMENT#") private TournamentData tournament;
    @InnerClass(startsWith = "PLAYER#")     private PlayerData     player;
    @InnerClass(startsWith = "MATCH#")      private MatchData      match;
    @InnerClass(startsWith = "RESULT#")     private ResultData     result;
}
```

Given rows whose `sk` is `TOURNAMENT#winter2026`, `PLAYER#p1`, `MATCH#m1`, `RESULT#m1`, a single
`findByPk("TOURNAMENT#winter2026")` returns four `ArenaItem`s — each with exactly one non-null
payload field, selected by matching its `startsWith` prefix against the row's sort-key value.

Selection rules:

* **`startsWith` / `endsWith`** — populate the field only when the entity's own sort-key value
  matches that prefix/suffix; otherwise leave it `null`.
* **`regex`** — populate the field only when the entity's own sort-key value matches the pattern in
  full. The pattern is checked before `startsWith`/`endsWith`, and combining them is allowed: all
  declared conditions must hold. It is compiled once when the entity is first mapped, so an invalid
  pattern fails at bootstrap rather than on the first read.
* **no marker** — populate when at least one of the embedded type's own columns is present in the
  item with a non-null value (an explicit DynamoDB `NUL` counts as absent). Two embedded types
  sharing a column name both read that same attribute.
* **`serializeAsNestedMap = true`** — opt out of flattening and store the object under a single
  attribute as a nested DynamoDB map (`M`) instead. Useful for a value object that never needs to be
  queried on its own attributes.

```java
public class MatchData {
    private String round;
    private String region;

    @InnerClass(serializeAsNestedMap = true)
    private Venue venue;             // one nested M attribute, not flattened
}
```

The nested map is built by walking the type's declared fields reflectively, so `@Column` names are
**not** applied inside it — attributes are named after the Java fields. `static`, `transient`, and
synthetic fields are skipped. Reading requires a no-arg constructor (visibility does not matter).

The flag applies to single-valued properties only. Collection-typed properties are already stored as
native DynamoDB types (see section 8), so `@InnerClass` has no effect on them either way.

Prefix routing cannot separate hierarchical sort keys where one kind's prefix is a prefix of
another's. With `ORDER#9876` for an order and `ORDER#9876#LINE#abc` for its lines,
`startsWith = "ORDER#"` matches both, so an order-line row populates the order field as well. Use
`regex` to draw that boundary:

```java
@InnerClass(regex = "ORDER#[^#]+")            private OrderData     order;
@InnerClass(regex = "ORDER#[^#]+#LINE#[^#]+") private OrderLineData line;
```

Ambiguity is never checked, whichever marker you use: if two members can match the same sort key,
both are populated. Keep the routes mutually exclusive.

### 2.5. `@SortKeyTemplate` — composed sort keys

Idiomatic single-table sort keys are hierarchical strings (`MATCH#2026-01-18#m1`).
`@SortKeyTemplate` composes one from several properties on write and decomposes it back on read, so
your Java model keeps typed fields while the wire format stays a single overloaded string.

```java
@Table(tableName = "tournament_arena")
@SortKeyTemplate("MATCH#{matchDate}#{matchId}")      // composes the base "sk"
public class Match {

    @PartitionKey private String pk;

    // Do NOT annotate sk with @SortKey — the template owns this column.
    // Declare it as a plain field so the template can write the composed value back.
    private String sk;

    private String matchDate;      // "2026-01-18"
    private String matchId;        // "m1"
}
```

Saving that writes `sk = "MATCH#2026-01-18#m1"`, and reading decomposes it back onto `matchDate` and
`matchId`. The placeholder properties are also persisted as their own ordinary columns. Updates
recompose the templated column so it stays consistent with the underlying properties.

Because reads always decompose from the composed column, the standalone placeholder attributes are
redundant for mapping — they exist so that filter expressions can reference them and so a secondary
index can key on them. Annotate a placeholder `@Derived` to keep it out of the item:

```java
@Table(tableName = "commerce")
@SortKeyTemplate("ORDER#{orderId}#LINE#{lineId}")
public class LineRow {

    @PartitionKey private String pk;
    private String sk;                    // "ORDER#9876#LINE#abc"

    @Derived private String orderId;      // not stored, decomposed from sk on read
    @Derived private String lineId;       // not stored, decomposed from sk on read
}
```

That trades queryability for item size: a `@Derived` property cannot appear in a filter expression
and cannot serve as an index key. Leave the annotation off when you need either.

`@Derived` is rejected at bootstrap on a property that is not a placeholder of some
`@SortKeyTemplate` on the same entity, on a key property, and on a primitive type — in each case the
value could not be recovered on read.

`column` targets an attribute other than `sk` — typically an overloaded GSI attribute:

```java
@Table(tableName = "tournament_arena")
@SortKeyTemplate(value = "MATCH#{matchId}", column = "gsi1sk")
public class Match { … }
```

The annotation is repeatable, so one entity may compose several columns as long as each targets a
**distinct** column:

```java
@SortKeyTemplate("MATCH#{matchDate}#{matchId}")                 // -> sk
@SortKeyTemplate(value = "MATCH#{matchId}", column = "gsi1sk")  // -> gsi1sk
public class Match { … }
```

Two templates targeting the same column, or a template on a column that also has a declared
`@SortKey`, are rejected at bootstrap.

**Limitation.** Decomposition anchors on the literal segments and takes whatever falls between them,
so it is unambiguous only if a placeholder value never contains the next literal. True for ids,
enums and dates; not guaranteed for free text.

### 2.6. `@ItemCollectionView` — read-only item-collection views

`@ItemCollectionView` provides a simpler way to model **Single Table Design for read queries**. It is
designed specifically for **read-only aggregation**: instead of returning one object for every item
and requiring callers to inspect which fields are populated, the annotation groups related entities
from the same partition into one typed item-collection view.

`@ItemCollectionMember` identifies which projection row type should be mapped to each field. Routing is
based on the entity's sort key and can use `startsWith`, `endsWith`, or `regex`:

```java
@ItemCollectionView(
        tableName = "single_table_demo",
        partitionKey = "pk",
        sortKey = "sk"
)
public class CustomerRow {

    @ItemCollectionMember(regex = "ORDER#[^#]+")
    private OrderData order;

    @ItemCollectionMember(regex = "ORDER#[^#]+#LINE#[^#]+")
    private List<OrderLineData> line;
}
```

The classes referenced by `@ItemCollectionMember` are projection row types and do not need
`@Table`. The enclosing `@ItemCollectionView` supplies the physical table and index metadata. Normal
property mapping annotations such as `@Column` and `@InnerClass` remain available when needed:

```java
public class OrderData {

    private String pk;
    private String sk;
    private String customerId;
}
```

```java
public class OrderLineData {

    private String pk;
    private String sk;
    private String productId;
}
```

When the view is queried, the module reads the matching rows and maps each row to the
appropriate `@ItemCollectionMember` field based on its sort-key pattern. This allows a query to return
a single grouped representation instead of requiring application code to iterate over every result
and check which field is populated.

For example, given the following rows:

| PK | SK |
|---|---|
| `CUSTOMER#123` | `ORDER#9876` |
| `CUSTOMER#123` | `ORDER#9876#LINE#1` |
| `CUSTOMER#123` | `ORDER#9876#LINE#2` |

the rows can be grouped into a single item-collection view:

```java
CustomerRow customer = ...;

        customer.getOrder(); // OrderData
        customer.getLine();  // List<OrderLineData>
```

Unlike `@InnerClass`, `@ItemCollectionView` is **read-only and intended for grouping query results**.
It does not embed or flatten the child entities into one DynamoDB item, and it does not change how
the child entities are persisted. A child type may also be an independently writable `@Table` entity,
but item-collection materialization does not require it.

The routing rules follow the same matching semantics as `@InnerClass`:

- **`startsWith`** matches when the sort key starts with the specified prefix.
- **`endsWith`** matches when the sort key ends with the specified suffix.
- **`regex`** must match the entire sort-key value.
- **`sortKey`** selects the physical sort-key attribute whose value is tested by `startsWith`,
  `endsWith`, or `regex`. Set it when an index-backed view has more than one candidate sort-key
  attribute; otherwise the view's declared `sortKey` is used.
- **Multiple conditions** can be combined, in which case all conditions must match.

As with `@InnerClass`, use `regex` when hierarchical sort keys would otherwise cause overlapping
prefix matches. For example, `startsWith = "ORDER#"` would match both `ORDER#9876` and
`ORDER#9876#LINE#1`, while `regex = "ORDER#[^#]+"` matches only the order row.

Unlike `@InnerClass` (whose pattern is compiled and validated at bootstrap), an `@ItemCollectionMember`
regex is compiled on the first item-collection query, so an invalid pattern surfaces then rather than at
application startup.

`@ItemCollectionView` is useful when the primary goal is to **query a single-table design and group its
heterogeneous rows into a convenient read model**, while keeping the underlying `@Table` entities
independent and suitable for normal persistence.

An item-collection view is read through an `ItemCollectionRepository<A>` (see section 4.8), or through the template's
`queryItemCollection(...)` when you need a hand-written key condition:

```java
DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request()
        .withKeyConditionExpression("#pk = :pk")
        .withExpressionAttributeNames(Map.of("#pk", "pk"))
        .withExpressionAttributeValues(Map.of(":pk", "CUSTOMER#123"))
        .build();

EntityQueryResult<CustomerRow> result =
        operations.queryItemCollection(CustomerRow.class, request, DynamoDbPageRequest.of(100));
```

`queryItemCollection` pages through the entire matched partition (following `LastEvaluatedKey`) before
folding the rows, so the view is complete regardless of DynamoDB's 1 MB page limit. A supplied
`DynamoDbPageRequest` controls the initial cursor and the `Limit` sent with **each** DynamoDB request;
that limit is a per-request evaluated-item page size, not a cap on the total folded result. The
operation continues from every returned cursor until DynamoDB reports no next page.


## 3. Secondary index views: `@SecondaryIndex`

Packing every GSI's keys onto the base entity turns one class into key-annotation soup and forces
the *write* model to carry every *read* pattern's concerns. Instead, each secondary index gets its
own small, **read-only view class**.

The base entity stays a clean write model and knows nothing about the indexes — `gsi1pk`/`gsi1sk`
above are plain columns, with no key annotations on them.

### 3.1. A typed view

```java
@SecondaryIndex(name = "GSI1", tableName = "tournament_arena")
public class PlayerMatchesView {

    @PartitionKey @Column("gsi1pk") private String collectionKey;   // "PT#winter2026#p1"
    @SortKey      @Column("gsi1sk") private String itemKey;         // "MATCH#m1"

    private String region;
}
```

`tableName` is optional: when every registered `@Table` entity resolves to the same physical table,
the view picks it up automatically, and `@SecondaryIndex("GSI1")` is enough. Set it explicitly only
in a multi-table application.

A view's `@PartitionKey`/`@SortKey` are the **index's** keys, and every read the module issues for a
view automatically sets `IndexName` and the resolved `TableName` — callers never pass an index name.

### 3.2. A heterogeneous container view

Views support the same `@InnerClass` prefix routing the base table uses, so one query can
reconstruct heterogeneous rows from an overloaded index:

```java
@SecondaryIndex("GSI1")
public class PlayerInTournamentView {

    @PartitionKey @Column("gsi1pk") private String collectionKey;   // "PT#winter2026#p1"
    @SortKey      @Column("gsi1sk") private String itemKey;

    @InnerClass(startsWith = "PLAYER#") private PlayerData player;
    @InnerClass(startsWith = "MATCH#")  private MatchData  match;
}
```

One `findByCollectionKey("PT#winter2026#p1")` returns the player row and all of that player's match
rows, each reconstructed as the right shape.

### 3.3. Multi-attribute index keys

DynamoDB supports up to **four partition and four sort attributes** on a GSI (a GA feature as of
November 2025), using real domain attributes instead of hand-concatenated synthetic keys. Express
them with `order`:

```java
@SecondaryIndex("by_tournament_region")
public class MatchesByTournamentRegionView {

    @PartitionKey(order = 0) private String tournamentId;
    @PartitionKey(order = 1) private String region;

    @SortKey(order = 0) private String round;      // most general
    @SortKey(order = 1) private String bracket;
    @SortKey(order = 2) private String matchId;    // most specific
}
```

Order the sort attributes from most general to most specific to maximise query flexibility. `order`
values must be contiguous and start at 0; gaps and duplicates are rejected at bootstrap.

Note the naming difference between the two idiomatic styles, both valid:

* **Overloaded index** shared by many item kinds → generic attribute names (`gsi1pk`/`gsi1sk`),
  populated with different meanings per kind.
* **Native multi-attribute key** → real domain names (`tournamentId`, `region`, `round`). Each is a
  genuine composite-key component, like a Cassandra clustering column.

### 3.4. Local secondary indexes

An LSI shares the base table's partition key and adds one alternate sort key. It is
single-attribute only (multi-attribute keys are GSI-only) and must exist at table-creation time.

```java
@SecondaryIndex("by_region")
public class MatchesByRegionView {

    @PartitionKey @Column("pk")     private String tournamentKey;   // the base partition key
    @SortKey      @Column("region") private String region;
}
```

### 3.5. Views are read-only

A DynamoDB index cannot be written, and it has no `GetItem` — only `Query` and `Scan`. The module
enforces both:

```java
operations.save(new PlayerMatchesView(...));               // InvalidDataAccessApiUsageException
operations.findById("MATCH#m1", PlayerMatchesView.class);  // InvalidDataAccessApiUsageException
```

Writes go through the base `@Table` entity that the index projects. `findAll()` and `count()` on a
view are supported and scan the view's own index rather than the base table.

---

## 4. Repositories

### 4.1. `DynamoDbRepository`

```java
public interface ArenaItemRepository extends DynamoDbRepository<ArenaItem, String> {

    List<ArenaItem> findByPk(String pk);

    List<ArenaItem> findByPkAndSkStartingWith(String pk, String prefix);

    boolean existsByPkAndSk(String pk, String sk);

    long countByPk(String pk);
}
```

`DynamoDbRepository<T, ID>` extends `ListCrudRepository`, so `findAll()` and `findAllById(...)`
return `List` rather than `Iterable`, matching the other Spring Data store modules. It adds
`update(entity)` on top of the standard CRUD surface.

Note that the repository's inherited `saveAll(...)` calls `save()` per entity (individual `PutItem`
calls with full `@Version` support). To use `BatchWriteItem` instead, inject `DynamoDbOperations`
and call its `saveAll()` method directly — that path batches at 25 but cannot enforce
optimistic-locking conditions.

`existsById(id)` issues a projection-only `GetItem` that returns only the partition-key attribute
and never triggers entity conversion or lifecycle events.

### 4.2. Composite ids

When the base table has both a partition and a sort key, pass a `DynamoDbCompositeId` where an id is
expected:

```java
ArenaItem item = repository.findById(
        DynamoDbCompositeId.of("TOURNAMENT#winter2026", "MATCH#m1")).orElseThrow();
```

Declare the repository as `DynamoDbRepository<ArenaItem, DynamoDbCompositeId>` for that shape, or
keep a scalar id type when the table's key is the partition key alone. The implementation dispatches
on the id's runtime type, so both work through the same repository contract.

### 4.3. `SecondaryIndexRepository`

A view is backed by a read-only fragment:

```java
public interface PlayerInTournamentViewRepository
        extends SecondaryIndexRepository<PlayerInTournamentView> {

    List<PlayerInTournamentView> findByCollectionKey(String collectionKey);

    Window<PlayerInTournamentView> findWindowByCollectionKey(
            String collectionKey, ScrollPosition position, Limit limit);
}
```

`SecondaryIndexRepository<T>` extends only Spring Data's plain `Repository` marker, so `save`,
`delete` and `findById` are *absent* — calling one is a compile error, not a runtime surprise.
Everything a view can do still works: derived queries, `@Query`, pagination, `count`, `exists`.

### 4.4. Derived queries and the key-condition rules

Derived method names are translated into a DynamoDB `Query` against the entity's key schema. The
rules DynamoDB itself enforces, which the module validates up front rather than letting a malformed
request reach the service:

* **Every partition-key attribute must be supplied, with equality.** You cannot query a subset of a
  multi-attribute partition key, nor use an inequality on one.
* **Sort-key attributes match left-to-right with no gaps.** For sort key `(round, bracket, matchId)`,
  `round` alone is valid and so is `round` + `bracket`; in `round` + `matchId` the skipped `bracket`
  demotes `matchId` from a key condition to a filter expression (the query still runs, just less
  selectively — and if nothing usable remains as a key condition it degrades to a `Scan` and needs
  `@AllowScan`).
* **At most one inequality, and it must be last.** `>`, `>=`, `BETWEEN` and `begins_with` are all
  inequalities in this sense.

```java
// valid: full partition key, leading sort-key subset
List<MatchesByTournamentRegionView> findByTournamentIdAndRegionAndRound(
        String tournamentId, String region, String round);

// "matchId" is demoted to a filter expression because "bracket" is skipped
List<MatchesByTournamentRegionView> findByTournamentIdAndRegionAndRoundAndMatchId(...);
```

Inequality predicates on a declared `@SortKey` are emitted as sort-key conditions, not filter
expressions: `…AndCreatedAtGreaterThanEqual`, `…AndCreatedAtBetween` and `…AndCreatedAtStartingWith`
all become part of the `KeyConditionExpression`. An inequality on a *partition*-key attribute cannot
be a key condition, so such a method degrades to a `Scan` and requires `@AllowScan`. Prefix ranges
over a `@SortKeyTemplate`-composed key work as expected.

### 4.5. `@AllowScan` — no silent full-table scans

If no index can serve a derived method as a `Query`, it would have to become a full-table `Scan`.
Rather than doing that silently, the module **rejects the method at bootstrap**:

```java
public interface ArenaItemRepository extends DynamoDbRepository<ArenaItem, String> {

    // fails at startup: "region" is not a partition key on any index
    List<ArenaItem> findByRegion(String region);

    // explicit, reviewable opt-in
    @AllowScan
    List<ArenaItem> findByRegion(String region);
}
```

The failure happens when the application context starts, not on first invocation — a scan-shaped
method can never reach production unnoticed.

### 4.6. Limiting queries: `findFirst` / `findTop`

Spring Data's derived limiting keywords are honoured. `findFirstBy…` and `findTopBy…` apply a
`Limit` of 1 to the underlying `Query`, and `findTop<N>By…` applies a limit of `N`:

```java
Match findFirstByPk(String pk);           // Limit 1; returns the first match or null

List<Match> findTop3ByPkOrderBySkDesc(String pk);   // Limit 3
```

A single-entity limiting method (`findFirstBy…` returning `Match`) returns the first result and does
**not** throw when several rows match — the limit truncates the result set at the source.

A derived `OrderBy` may reference only the selected index's sort key. Ascending order maps to
DynamoDB's `ScanIndexForward=true`; descending order maps to `ScanIndexForward=false`. Ordering by a
non-sort-key attribute is rejected when the repository method is bootstrapped rather than being
emulated with an in-memory sort.

### 4.7. Pagination

DynamoDB pages by an opaque `LastEvaluatedKey`, not a numeric offset, so `Window<T>` is the
supported paginated return type. Both `Page<T>` and `Slice<T>` are rejected at bootstrap:

* `Page<T>` would require a total count, which DynamoDB cannot provide without reading the whole
  table.
* `Slice<T>` is backed by an offset-based `Pageable`; DynamoDB paginates by keyset, so
  `Slice.nextPageable()` cannot advance. Use `Window<T>` instead.

```java
Window<PlayerInTournamentView> page = repository.findWindowByCollectionKey(
        "PT#winter2026#p1", ScrollPosition.keyset(), Limit.of(25));

if (page.hasNext()) {
    ScrollPosition next = page.positionAt(page.size() - 1);
    Window<PlayerInTournamentView> more =
            repository.findWindowByCollectionKey("PT#winter2026#p1", next, Limit.of(25));
}
```

Only a keyset `ScrollPosition` is accepted (`ScrollPosition.keyset()`, `.forward(...)`,
`.backward(...)`); an offset position raises `InvalidDataAccessApiUsageException`.

DynamoDB returns a single resume cursor per page (`LastEvaluatedKey`), which points *after* the last
item, so a position is only available for the final element: call
`positionAt(window.size() - 1)`, as above. Any other index raises `IllegalStateException` rather than
returning the page-end cursor, which would silently skip the rows in between. Use
`window.hasPosition(index)` if you want to probe without catching.

### 4.8. `ItemCollectionRepository`

An `@ItemCollectionView` (section 2.6) is read through an `ItemCollectionRepository<V>`, the read-only
counterpart to `SecondaryIndexRepository`:

```java
public interface CustomerItemCollectionRepository extends ItemCollectionRepository<CustomerRow> {
}
```

It contributes a fixed set of partition-oriented finders — there are no derived query methods, since
an item-collection view always folds one partition (or one index collection) into a single object:

```java
Optional<CustomerRow> whole    = repository.findByPartitionKey("CUSTOMER#123");
Optional<CustomerRow> point    = repository.findByPartitionKeyAndSortKey("CUSTOMER#123", "ORDER#9876");
Optional<CustomerRow> range    = repository.findByPartitionKeyAndSortKeyBetween(
                                     "CUSTOMER#123", "ORDER#0000", "ORDER#9999");
Optional<CustomerRow> prefixed = repository.findByPartitionKeyAndSortKeyStartingWith(
                                     "CUSTOMER#123", "ORDER#9876#LINE#");
boolean populated              = repository.existsByPartitionKey("CUSTOMER#123");
```

The `Optional`-returning finders issue a `Query`, page through the whole result following
`LastEvaluatedKey`, and fold the rows onto the view's `@ItemCollectionMember` fields.
`findByPartitionKeyAndSortKey` narrows to a single item, `…StartingWith` to a `begins_with` prefix,
and `…Between` to a sort-key range. `existsByPartitionKey` instead uses `Select=COUNT` with a
per-request limit of one and stops at the first matching item; if a filter removes an evaluated item,
it follows the cursor until a match is counted or DynamoDB reports no next page.

For a key condition the fixed finders cannot express, add a `@Query` method whose expression is
passed straight through to `queryItemCollection`:

```java
public interface CustomerItemCollectionRepository extends ItemCollectionRepository<CustomerRow> {

    @Query(keyConditionExpression = "#pk = :pk AND begins_with(#sk, :prefix)",
           names = { @ExpressionName(name = "#pk", value = "pk"),
                     @ExpressionName(name = "#sk", value = "sk") })
    Optional<CustomerRow> loadOrderLines(@Param("pk") String pk, @Param("prefix") String prefix);
}
```

Derived query methods and `@Update` methods are both rejected at bootstrap on an
`ItemCollectionRepository` — only the fixed finders and read-only `@Query` methods are allowed, because an
item-collection view is read-only and `@ItemCollectionView` never changes how the underlying `@Table`
entities are written. When the view is index-backed (`@ItemCollectionView(indexName = "GSI2", …)`),
the same finders run against that index, and `partitionKey`/`sortKey` name the index's key
attributes.

---

## 5. `@Query` — explicit expressions

When derivation cannot express a query, write the DynamoDB expression yourself.

### 5.1. Key conditions and filters

```java
@SecondaryIndex(name = "GSI1", tableName = "tournament_arena")
public class MatchByDate {

    @PartitionKey @Column("gsi1pk") private String tournament;
    @SortKey      @Column("gsi1sk") private String matchDate;
}

public interface MatchByDateRepository extends SecondaryIndexRepository<MatchByDate> {

    @Query(keyConditionExpression = "#pk = :pk AND #sk BETWEEN :from AND :to",
           indexName = "GSI1",
           names = { @ExpressionName(name = "#pk", value = "gsi1pk"),
                     @ExpressionName(name = "#sk", value = "gsi1sk") })
    List<MatchByDate> findInDateRange(@Param("pk") String pk,
                                      @Param("from") String from,
                                      @Param("to") String to);
}
```

Parameters bind by name: the `:from` placeholder in the expression resolves to the argument
annotated `@Param("from")` — declare the `@Param` name **without** the leading colon.
`@ExpressionName` maps a `#alias` to a real attribute name, which also sidesteps DynamoDB's reserved
words (`region`, `year`, `status`, …). `@ExpressionValue` supplies a value inline, evaluated as a SpEL expression (a quoted literal such as `'ACTIVE'` is the common case).

`keyConditionExpression` is an escape hatch: it bypasses the module's key-condition validation
entirely, so `indexName` must always be set explicitly when you use it (the escape hatch does not
auto-select an index), and correctness is yours. `ItemCollectionRepository` `@Query` methods are exempt
from this requirement.

A base-table `DynamoDbRepository` rejects `@Query(indexName=...)`; use a derived method for base-table
key queries. For an explicit key condition on a typed `SecondaryIndexRepository`, the caller is
responsible for naming the same physical index represented by its `@SecondaryIndex` view. Filter-only
queries on a typed view do not need `indexName`: they scan the index declared by the view and still
require `@AllowScan` or `allowScan = true`.

Other attributes: `consistentRead`, `limit`, and `allowScan` (the `@Query`-side equivalent of
`@AllowScan`). Declared filter-only queries and normal named queries without scan opt-in are rejected
when the repository is created, matching the fail-fast behavior of derived scan queries.

For non-PartiQL read queries, a `long`/`Long` return type executes a DynamoDB count and a
`boolean`/`Boolean` return type executes an existence check. Other supported return types materialize
entities normally.

### 5.2. `@Update` — single-item updates

```java
@Update(updateExpression = "SET #winner = :winner",
       conditionExpression = "attribute_exists(#pk)",
       names = { @ExpressionName(name = "#winner", value = "winner"),
                 @ExpressionName(name = "#pk", value = "pk") })
void recordWinner(@Param("pk") String pk, @Param("sk") String sk,
                  @Param("winner") String winner);
```

`@Update` owns `updateExpression`, `conditionExpression`, `names`, and `values`; do not combine it
with `@Query`, which is read-only. Create new items with repository `save`/`insert` or the corresponding
`DynamoDbOperations` methods.

A `@Update` method is always an `UpdateItem` against one item — never a `Query` or `Scan`. The
partition and sort key are resolved from the method's `@Param`-annotated arguments. There are no
derived `deleteByX`/`updateByX` methods: an update operation is always explicit.

### 5.3. PartiQL

```java
@Query(partiQl = "SELECT * FROM tournament_arena WHERE pk = ?")
List<ArenaItem> findByPkWithPartiQl(String pk);
```

Values bind positionally. Supported return types are `List<T>`, `Optional<T>` and a single `T`;
`Window` pagination over PartiQL is not implemented yet. PartiQL is not supported for
`ItemCollectionRepository` methods because those methods must fold paged query rows into a view; such
a declaration is rejected at repository bootstrap.

### 5.4. Named queries

Set `namedQueriesLocation` on `@EnableDynamoDbRepositories` to a classpath properties resource. Each
property key is the domain class's simple name plus the repository method name:

```properties
Customer.findNamedByEmail=#email = :email
OrderItemCollection.findNamed=#pk = :pk AND begins_with(#sk, :prefix)
```

The expression has repository-specific semantics:

- On a normal `DynamoDbRepository` or `SecondaryIndexRepository`, it is a **filter expression** and
  executes as a `Scan`. The repository method must opt in with `@AllowScan`. A typed secondary-index
  repository scans its declared index, not the base table.
- On an `ItemCollectionRepository`, it is a **key-condition expression**. Item-collection folding
  requires a partition query and therefore cannot use a scan filter.

Named parameters bind exactly as they do for inline `@Query` expressions. An optional method-level
`@Query` annotation can supply metadata while the properties file supplies the expression text:
`names`, `values`, `limit` and `consistentRead` are retained. `indexName` is also retained for an
item-collection query. A typed secondary-index repository always scans the index declared by its
`@SecondaryIndex` view, while a base-table repository rejects method-level `indexName`. Explicit
aliases support nonconventional mappings such as `#partition -> pk`; otherwise a conventional
placeholder such as `#pk` resolves to `pk`.

Named queries are read-only; combining one with `@Update` is rejected during repository bootstrap.
Resolution follows `queryLookupStrategy` exactly:

- `CREATE` ignores named and `@Query` declarations and derives a PartTree query from the method name.
  Custom `ItemCollectionRepository` methods therefore cannot use this mode because item-collection
  views expose no key properties for PartTree derivation.
- `USE_DECLARED_QUERY` checks the named-query properties first, then method-level `@Query`, and fails
  repository bootstrap when neither declaration exists.
- `CREATE_IF_NOT_FOUND` (the default) checks the named-query properties first, then method-level
  `@Query`, and finally falls back to a derived query. This is also why a named item-collection query
  resolves before the unsupported-derived-query guard.

---

## 6. `DynamoDbOperations` — the template API

Inject `DynamoDbOperations` (implemented by `DynamoDbTemplate`) for work that does not fit a
repository method.

```java
@Service
public class ArenaService {

    private final DynamoDbOperations operations;

    ArenaService(DynamoDbOperations operations) {
        this.operations = operations;
    }
}
```

### 6.1. Writes

```java
operations.save(match);                    // PutItem; honours @Version
operations.insert(match);                  // PutItem, fails if the key exists -> DuplicateKeyException
operations.saveAll(List.of(m1, m2, m3));   // BatchWriteItem, chunked at 25, retries unprocessed items
operations.update(match);                  // UpdateItem from the entity's current state
operations.delete(match);
operations.delete(Match.class, "TOURNAMENT#winter2026", "MATCH#m1");
```

`saveAll()` accepts entities of mixed types: items are grouped by their resolved table name and
dispatched as one or more `BatchWriteItem` requests (chunked at 25). Unprocessed items are retried
with exponential backoff. `@Version` entities are rejected because `BatchWriteItem` cannot carry
condition expressions.

### 6.2. Reads

```java
// two-key lookup: the template takes the partition and sort key as separate arguments
Match match = operations.findById("TOURNAMENT#winter2026", "MATCH#m1", Match.class);

// single-key table
Tournament tournament = operations.findById("TOURNAMENT#winter2026", Tournament.class);

// projection-only existence check (no conversion, no events)
boolean exists = operations.existsById("TOURNAMENT#winter2026", "MATCH#m1", Match.class);

List<Match> all = operations.findAll(Match.class);     // auto-paginated Scan
long count      = operations.count(Match.class);
```

`DynamoDbCompositeId` (section 4.2) is a *repository*-level convenience — the template exposes the
two keys directly instead.

### 6.3. `IndexQueryBuilder` — typed, validated queries

```java
EntityQueryResult<List<MatchesByTournamentRegionView>> result =
        operations.query(MatchesByTournamentRegionView.class, "by_tournament_region")
                .partition("tournamentId", "winter2026")
                .partition("region", "NA-EAST")
                .sortEq("round", "SEMIFINALS")
                .sortBeginsWith("bracket", "UP")     // inequality -> must be last
                .limit(50)
                .execute();
```

The builder validates the section 4.4 rules at build time and aliases every attribute name through
`ExpressionAttributeNames`, so reserved words never need special handling. It also rejects null key
values, duplicate partition-key assignments, and filter placeholders that collide with the
auto-generated key placeholders. Available conditions: `sortEq`, `sortLt`, `sortLe`, `sortGt`,
`sortGe`, `sortBetween`, `sortBeginsWith`, plus `filterExpression`, `scanIndexForward`,
`consistentRead`, `exclusiveStartKey` and `limit`.

---

## 7. Lifecycle callbacks and events

Spring Data callbacks may inspect and replace the entity. Application events are read-only
notifications consumed with `@EventListener`.

### 7.1. Callbacks

| Callback | Applies to |
|---|---|
| `DynamoDbBeforeConvertCallback` | `save`, `insert`, and `saveAll`, before Java-to-item conversion |
| `DynamoDbBeforeSaveCallback` | `save`, `insert`, and `saveAll`, after `BeforeConvert` and before conversion |
| `DynamoDbAfterSaveCallback` | `save`, `insert`, and `saveAll`, after a successful write |
| `DynamoDbAfterConvertCallback` | Standard entity reads after item-to-Java conversion |

Callbacks return the entity that continues through the operation, so they may replace or modify it.
`update()` and `delete()` do not invoke update/delete callbacks; those operations publish events only.

```java
@Component
public class MatchAuditCallback implements DynamoDbBeforeSaveCallback<Match> {

    @Override
    public Match onBeforeSave(Match entity, String tableName) {
        entity.setUpdatedAt(Instant.now().toString());
        return entity;
    }
}
```

### 7.2. Application events

| Event | Emitted when |
|---|---|
| `DynamoDbBeforeSaveEvent` | before a `save`, `insert`, or `saveAll` write |
| `DynamoDbAfterSaveEvent` | after a `save`, `insert`, or `saveAll` write succeeds |
| `DynamoDbBeforeUpdateEvent` | before `UpdateItem` |
| `DynamoDbAfterUpdateEvent` | after `UpdateItem` succeeds |
| `DynamoDbBeforeDeleteEvent` | before `DeleteItem` |
| `DynamoDbAfterDeleteEvent` | after `DeleteItem` succeeds |
| `DynamoDbAfterConvertEvent` | after a standard entity read and its `AfterConvert` callback |

All events extend `DynamoDbMappingEvent<T>`, whose source is the entity (or entity class for an
expression-based update) and whose `tableName` is the resolved physical table. There is no
`DynamoDbBeforeConvertEvent`; before-convert behavior is callback-only.

```java
@Component
public class MatchAuditListener {

    @EventListener
    public void onAfterSave(DynamoDbAfterSaveEvent<Match> event) {
        log.info("Saved {} to {}", event.getSource(), event.getTableName());
    }
}
```

### 7.3. Execution order

For `save()` and `insert()`:

1. `DynamoDbBeforeConvertCallback`
2. `DynamoDbBeforeSaveCallback`
3. `DynamoDbBeforeSaveEvent`
4. Java-to-DynamoDB-item conversion and SDK `PutItem`
5. `DynamoDbAfterSaveCallback`
6. `DynamoDbAfterSaveEvent`

For `saveAll()`, steps 1–3 run for every entity first. The chunked `BatchWriteItem` requests then
complete, including retries for unprocessed items, before steps 5–6 run for every entity. Hooks are
emitted per entity, not once for the batch.

An entity-based `update()` publishes `BeforeUpdate`, executes `UpdateItem`, then publishes
`AfterUpdate`. An expression-based update follows the same event order and then converts the returned
`ALL_NEW` attributes, invoking `AfterConvert` and publishing `AfterConvertEvent`. A delete publishes
`BeforeDelete`, executes `DeleteItem`, then publishes `AfterDelete`.

Standard entity reads (`findById`, query, scan, PartiQL, and index-query paths) perform item-to-Java
conversion, invoke `DynamoDbAfterConvertCallback`, and then publish `DynamoDbAfterConvertEvent` for
each converted entity. Item-collection folding uses its dedicated converter path and does not emit
per-row after-convert hooks.

---

## 8. Custom conversions

Register converters for types the module does not handle natively by overriding `customConverters()`:

```java
@Configuration
public class ArenaConfiguration extends AbstractDynamoDbConfiguration {

    @Override
    protected List<?> customConverters() {
        return List.of(new SeedToAttributeValueConverter(), new AttributeValueToSeedConverter());
    }
}
```

For a single property, `@ValueConverter` with a `PropertyValueConverter` takes precedence over the
global conversion service:

```java
public class Match {

    @ValueConverter(BracketConverter.class)
    private Bracket bracket;
}
```

Enums are stored as their `name()`. Collections map to native DynamoDB types: a `Set<String>`
becomes a String Set (`SS`), a `Set<Number>` a Number Set (`NS`), a `Set<byte[]>` a Binary Set
(`BS`), and a `List` an `L`. A `Set` whose elements are of mixed types falls back to an `L`. No JSON
serialization is involved anywhere; `@InnerClass(serializeAsNestedMap = true)` stores a nested map
(`M`), not a JSON string.

DynamoDB cannot store an empty `SS`/`NS`/`BS`, so an **empty `Set` is written as an empty `L`**. It
still reads back as an empty `Set`, because the read path dispatches on the declared property type.
The consequence is that the stored attribute type depends on cardinality: non-empty is `SS`, empty is
`L`. Consumers that switch on the raw attribute type — Streams processors, other-language readers —
need to handle both.

A `null` property is written as an explicit `NUL` attribute rather than being omitted. A `null`
`@InnerClass` member writes nothing at all. If you rely on sparse secondary indexes or
`attribute_not_exists(...)` conditions, keep those attributes off entities that persist them as
`null`.

---

## 9. Exception translation

Every SDK call is routed through a `DynamoDbExceptionTranslator`, so DynamoDB failures arrive as
Spring's `DataAccessException` hierarchy. The first four rows below are produced by the template's
write path, which intercepts `ConditionalCheckFailedException` before translation; the remaining
rows are the default translator:

| DynamoDB condition                                                                    | Spring exception                          |
|---------------------------------------------------------------------------------------|-------------------------------------------|
| version condition failed on `save` / `update`                                         | `OptimisticLockingFailureException`       |
| `insert` onto an existing key                                                         | `DuplicateKeyException`                   |
| other failed condition expression on a write                                          | `DataIntegrityViolationException`         |
| a view used for a write or `findById`                                                 | `InvalidDataAccessApiUsageException`      |
| `DuplicateItemException`                                                               | `DuplicateKeyException`                   |
| `TransactionConflictException` / a bare `ConditionalCheckFailedException`             | `ConcurrencyFailureException`             |
| `ProvisionedThroughputExceededException` / `ItemCollectionSizeLimitExceededException` | `TransientDataAccessResourceException`    |
| `RequestLimitExceededException`                                                       | `NonTransientDataAccessResourceException` |
| `ResourceNotFoundException` (table or index missing)                                  | `BadStatementGrammarException`            |
| any other `AwsServiceException`                                                       | `UncategorizedDynamoDbException`          |

Override `dynamoDbExceptionTranslator()` to supply your own.

---

## 10. Annotation summary

| Annotation                    | Target        | Purpose                                                                |
|-------------------------------|---------------|------------------------------------------------------------------------|
| `@Table`                      | type          | Marks a base-table entity. `value`/`tableName` select the physical table. |
| `@SecondaryIndex`             | type          | Marks a read-only index view. `name`/`value`, `tableName`              |
| `@PartitionKey`               | field/method  | Partition-key component. `value`, `order`. Repeatable                  |
| `@SortKey`                    | field/method  | Sort-key component. `value`, `order`. Repeatable                       |
| `@Column`                     | field/method  | Physical attribute name. `value`, `isStatic`                           |
| `@InnerClass`                 | field         | Flattened embedded object. `startsWith`, `endsWith`, `regex`, `serializeAsNestedMap` |
| `@ItemCollectionView`        | type          | Read-only view over a partition or index. `tableName`, `partitionKey`, `sortKey`, `indexName` |
| `@ItemCollectionMember`      | field         | Routes matched rows onto an item-collection member. `startsWith`, `endsWith`, `regex`, `sortKey` |
| `@SortKeyTemplate`            | type          | Composed sort key. `value`, `column`. Repeatable                       |
| `@Derived`                    | field/method  | Reconstructed on read, never written. Only on `@SortKeyTemplate` placeholders |
| `@Version`                    | field         | Optimistic locking (Spring Data)                                       |
| `@Query`                      | method        | Explicit key condition / filter / PartiQL read query                    |
| `@Update`                     | method        | Single-item update / condition expression                              |
| `@AllowScan`                  | method        | Opts a derived method into a full-table `Scan`                         |
| `@ExpressionName`             | (in `@Query`) | Maps `#alias` to an attribute name                                     |
| `@ExpressionValue`            | (in `@Query`) | Supplies a constant `:value`                                           |
| `@EnableDynamoDbRepositories` | type          | Enables repository scanning                                            |

> The **Target** column lists each annotation's primary usage site. The key and attribute
> annotations also allow `ElementType.ANNOTATION_TYPE` (and `@Column` additionally `PARAMETER`) so
> they can be composed into custom meta-annotations.

---
