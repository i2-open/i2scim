# Decision Log - Java 25 Upgrade

This log tracks key decisions made during the upgrade to Java 25 and Quarkus 3.34.3. and diagnosis of other issues.  For each new issue start a new table.


| Date | Decision | Rationale |
| :--- | :--- | :--- |
| 2026-04-14 | Upgrade to Java 25 (LTS) | Support latest LTS, improve performance, and maintain security posture. |
| 2026-04-14 | Upgrade to Quarkus 3.34.3 | Latest stable version (as of April 2026) with Jakarta EE 11 support. |
| 2026-04-14 | Migrate to Apache HttpClient 5.4.1 | Address CVEs in older 4.5.x versions and align with modern standards. |
| 2026-04-14 | Update Jose4j to 0.9.6+ | Fix known JWE decompression DoS vulnerability. |
| 2026-04-14 | Use eclipse-temurin:25 as base image | Standardize on Temurin distribution for Java 25. |
| 2026-04-14 | Migrate to Micrometer Metrics | Replace deprecated SmallRye Metrics (MicroProfile) with recommended Micrometer for Quarkus 3.x. |
| 2026-04-14 | Reorder POM Tags | Ensure `<modelVersion>` is the first child of `<project>` to fix "invalid POM" resolution issues in submodules. |
| 2026-04-14 | Standardize Test Exceptions | Use `throws Exception` in JUnit tests to simplify handling of HttpClient 5 checked exceptions. |
| 2026-04-15 | Disable Polling Retries on Shutdown | Prevent long delays during test cleanup and profile switching when the target server is already down. |
| 2026-04-15 | Standardize on RESTEasy Reactive | Resolved Quarkus capability conflict in `pkg-i2scim-prov-mongodb` by replacing Classic RESTEasy with RESTEasy Reactive (`quarkus-rest-jackson`). |
| 2026-04-15 | Exclude K8S Load Tests | Explicitly excluded `LoadScimClusterTest` from the standard build to prevent stalls caused by connection attempts to non-existent cluster endpoints. |
| 2026-04-15 | Fix Race in `PollStream` | Resolved `ConcurrentModificationException` in `PollStream.pollEvents` by using `CopyOnWriteArrayList` for tracking acknowledgments and pending operations in `SignalsEventHandler`, and reduced initialization sleep during tests to prevent stalls. |
