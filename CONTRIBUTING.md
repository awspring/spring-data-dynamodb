# How to contribute to Spring Data DynamoDB

## Checking out and building

To check out the project and build it from source, do the following:

```
git clone https://github.com/awspring/spring-data-dynamodb.git
cd spring-data-dynamodb
./mvnw package
```

To build and install jars into your local Maven cache:

```
./mvnw install
```

For faster builds, we recommend using [Maven Daemon](https://github.com/apache/maven-mvnd) and using following commands:

Build:

```
make build
```

Clean:

```
make clean
```

Format code:

```
make format
```

## Building documentation

Documentation can be built by activating the `docs-classic` profile in the Maven build.

```
make docs
```

It generates:

- reference documentation at `docs/target/generated-docs/reference/html/reference.html`
- API docs at `target/site/apidocs/index.html`

The documentation build runs Spotless verification but does not apply formatting changes.

## **Did you find a bug?**

* **Do not open up a GitHub issue if the bug is a security vulnerability
  in Spring Data DynamoDB**, and instead to refer to our [security policy](https://github.com/awspring/spring-data-dynamodb/blob/main/SECURITY.md).

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/awspring/spring-data-dynamodb/issues).

* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/awspring/spring-data-dynamodb/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, ideally with a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

### **Did you write a patch that fixes a bug?**

* Open a new GitHub pull request with the patch.

* Ensure the PR description clearly describes the problem and solution. Include the relevant issue number if applicable.

### **Do you intend to add a new feature or change an existing one?**

* Suggest your change in the [Issues](https://github.com/awspring/spring-data-dynamodb/issues).

* Start writing code once the issue got approved by project maintainers.

* In addition to actual implemented feature, remember to:

  * update reference docs
  * each public class should have a Javadoc
  * code has to have tests
  * each package has to have `package-info.java` file definining nullability rules
  * each nullable field, method parameter, method return value, if can be null, has to be annotated with `org.jspecify.annotations.Nullable`.


### **Using Gitpod**

To avoid setting up your local development environment, you can use [Gitpod](https://www.gitpod.io/) and develop directly in browser based Visual Studio Code, or [JetBrains Client via JetBrains Gateway](https://www.gitpod.io/docs/ides-and-editors/jetbrains-gateway).

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/awspring/spring-data-dynamodb/)

Thanks!
