# Experiment Report

## Empirical Testing of Moqui's Entity Iterator Memory Behavior on MySQL

**Date:** June 26, 2026

**Context:** Verification of read-path safety for the sim-routing cross-database sync engine.

**Objective:**
To empirically test whether Moqui's Entity Engine iterator (`ec.entity.find(...).iterator()`) streams rows from MySQL row-by-row, or whether it causes the driver to buffer the entire result set in the JVM heap, leading to memory exhaustion on large tables.

---

# 1. The Hypothesis Under Test

**Original Source:** [Hypothesis Document](https://github.com/hotwax/sim-routing/blob/main/docs/hypothesis-moqui-iterator-mysql-streaming.md)

## The Current Assumption (H)

When reading from a MySQL source with the default connection configuration, Moqui's `EntityListIterator` (`.iterator()`) streams rows row-by-row from the database and keeps JVM heap usage flat and bounded—independent of how many rows the query matches.

## The Expected Alternative (A)

The engine requests a positive JDBC fetch size (default `100`). MySQL's Connector/J ignores positive fetch sizes and buffers the entire result set into JVM memory before returning the first row (unless `useCursorFetch=true`). Therefore, heap grows with row count, and the iterator is "lazy" only at the API surface.

## Falsification Criteria

* **H is REFUTED** if, against MySQL with the default connection, used heap grows linearly with result-set size or spikes before the first row is consumed, resulting in an `OutOfMemoryError` on constrained heaps.
* **H is SUPPORTED** if used heap stays flat and bounded regardless of row count.

---

# 2. Methodology & Baseline Measurements

To ensure accurate results, I bypassed Gradle (`./gradlew run`) and launched the compiled application directly using the `java` executable. This allowed strict control over the JVM arguments required for the **Decisive Setup**.

## 2.1 Establishing the Unconstrained Memory Baseline

Before applying constraints, I measured the server's default maximum memory capacity using a Groovy shell script.

### Groovy

```groovy
ec.logger.warn("Max Memory: " + (Runtime.getRuntime().maxMemory() >> 20));
```

### Output

```text
15:15:55.064 WARN o.moqui.i.c.LoggerFacadeImpl Max Memory: 3934
```

### Observation

The JVM naturally allocates approximately **3.9 GB** of RAM. This massive buffer would easily swallow the entire dataset without crashing, creating a false impression of streaming. A physical memory trap was therefore required.

---

## 2.2 Data Generation & Table Sizing

I populated `test_large_entity` with over **8.3 million rows** to ensure the table's physical size far exceeded our planned memory constraint.

### SQL Verification

```sql
SELECT table_name AS 'Table Name',
       ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Total Size (MB)'
FROM information_schema.TABLES
WHERE table_schema = 'moqui_experiment'
  AND table_name = 'test_large_entity';
```

### Output

```text
+-------------------+-----------------+
| Table Name        | Total Size (MB) |
+-------------------+-----------------+
| test_large_entity |          566.00 |
+-------------------+-----------------+
```

### Observation

The target table size was approximately **566 MB**. Setting a JVM heap limit of **256 MB** would guarantee a crash if the framework attempted to buffer the entire table.

---

# 3. Code-Verified Facts & Driver Documentation

Before executing the trap, I verified the underlying framework logic and primary source documentation as requested.

### Cursor Type

Confirmed in `EntityFindBase.groovy` that the default result set is **forward-only** and **read-only**:

* `TYPE_FORWARD_ONLY`
* `CONCUR_READ_ONLY`

### Fetch Size Guard

Confirmed in `EntityFindBuilder.java` that the engine forces a positive fetch size:

```java
if (fetchSize != null && fetchSize > 0) {
    ps.setFetchSize(fetchSize);
} else {
    ps.setFetchSize(100);
}
```

This guard physically blocks `Integer.MIN_VALUE` from reaching the driver.

### Driver Documentation

The official MySQL Connector/J documentation confirms:

> "By default, all result sets are completely retrieved and stored in memory... To enable row-by-row streaming, you must set the fetch size to Integer.MIN_VALUE."

---

# 4. The Experiment: Fair-Test Controls & Falsification

## 4.1 The Conditional Control Run (`useCursorFetch=true`)

I first booted the server using Moqui's default `mysql8` profile, which implicitly injects `useCursorFetch=true` into the connection string under the hood.

I executed the `MemoryTest` service against the **8.3 million row** table.

### Output

```text
Time to Create Iterator: 5424 ms
Time to First Row: 67 ms
Memory Before: 68 MB
Memory At First Row: 68 MB
Memory At End: 68 MB
```

### Observation

The system did not crash.

Because `useCursorFetch=true` was active, MySQL utilized a server-side cursor, successfully feeding the data in manageable batches and overriding the driver's default buffering behavior.

---

## 4.2 The Decisive Setup Run (Strict Default Connection)

To test the actual hypothesis (which dictates testing the default connection configuration **without** the `useCursorFetch` crutch), I implemented an inline JDBC configuration in `MoquiDevConf.xml` to forcefully strip away the framework's hidden safety net.

### XML

```xml
<entity-facade query-stats="true">
    <datasource group-name="transactional" database-conf-name="mysql8" schema-name="">
        <inline-jdbc jdbc-driver="com.mysql.cj.jdbc.Driver"
                     jdbc-uri="jdbc:mysql://127.0.0.1:3306/moqui_experiment?useCursorFetch=false&amp;useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC"
                     jdbc-username="root"
                     jdbc-password="my_pass"/>
    </datasource>
</entity-facade>
```

I then booted the server with the strict **256 MB** memory constraint.

### Bash

```bash
java -Xmx256m -jar moqui.war -conf=conf/MoquiDevConf.xml
```

I executed the `MemoryTest` service.

### Output

```text
org.moqui.impl.entity.EntitySqlException: Error finding list of TestLargeEntity by null [S1000]
...
Caused by: java.sql.SQLException: Java heap space
...
Caused by: java.lang.OutOfMemoryError: Java heap space
    ...
    at com.mysql.cj.protocol.a.NativeProtocol.readAllResults(NativeProtocol.java:1713)
```

---

# 5. Final Verdict and Conclusion

## Verdict

**Hypothesis (H) is REFUTED.**

Under default MySQL connection parameters (where `useCursorFetch` is not manually enforced), Moqui's `EntityListIterator` does **not** stream rows.

The `java.lang.OutOfMemoryError` and the presence of `NativeProtocol.readAllResults()` in the stack trace provide definitive empirical proof that the driver buffers the entire **566 MB** result set directly into the **256 MB** JVM heap.

---

## Why This Occurs

The Entity Engine's abstraction layer (`EntityFindBuilder.java`) actively coerces the fetch size to `100`, blocking the `Integer.MIN_VALUE` sentinel required by MySQL to stream.

---

## Business Impact

The assumption that **"I can iterate a huge table safely"** using standard Moqui tools is false for MySQL unless server-side cursors are explicitly enabled in the configuration.

Therefore, the architectural decision to read with raw, streaming JDBC for the **sim-routing sync engine** is justified and necessary to prevent fatal memory spikes during bulk cross-database loads.

---

# 6. Actionable Takeaways

## 1. For the Sync Engine

The **sim-routing sync engine** cannot rely on Moqui's Entity Engine for bulk, cross-database data loads.

It must use raw JDBC connections configured with:

```java
setFetchSize(Integer.MIN_VALUE)
```

to safely stream large tables without risking production server crashes.

---

## 2. For General Development

If developers must iterate over large datasets using the Entity Engine, they must ensure `useCursorFetch=true` is strictly enforced in the connection profile to utilize server-side cursors as a fallback safety measure.

---
