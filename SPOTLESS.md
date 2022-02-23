# Spotless Code Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) for code formatting and license header management.

## What Spotless Does

- Applies Apache 2.0 license headers to all Java files (from `license-header.txt`)
- Removes unused imports
- Trims trailing whitespace
- Ensures files end with a newline

## Maven Commands

### Check formatting
```bash
mvn spotless:check
```

### Apply formatting
```bash
mvn spotless:apply
```

### Build with automatic checking
Spotless check runs automatically during the `compile` phase:
```bash
mvn compile
```

If the check fails, run `mvn spotless:apply` to fix formatting issues automatically.

## Configuration

The Spotless configuration is in `pom.xml` under the `spotless-maven-plugin` section. The license header template is in `license-header.txt`.

### License Header

The license header is automatically applied to all Java files and includes:
- Copyright year (dynamically set to current year for new files)
- Apache License 2.0 reference

The template in `license-header.txt` uses `$YEAR` placeholder which Spotless replaces with the current year.
