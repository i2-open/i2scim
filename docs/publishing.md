# Restoring Maven Central publishing

i2scim no longer publishes to Maven Central. The active distribution mechanism is the multi-arch Docker image at `docker.io/independentid/i2scim`. This document is the recipe to restore publishing if a future maintainer wants to ship `i2scim-core` and/or `i2scim-client` as JAR libraries again.

## Why publishing was dropped

The 10-module Maven structure carried publishing infrastructure on every module — GPG signing, Sonatype staging, source/javadoc JAR generation, an OSSRH `<distributionManagement>` block — but only `i2scim-core` and `i2scim-client` are library-shaped. The rest produce a Quarkus application or are scaffolding. Publishing had not been actively used and was adding maintenance overhead to every POM. Dropping it simplified the build; this recipe lets it come back without research.

## Artifacts that would publish if restored

Two artifacts only:

- `com.independentid:i2scim-core` — the protocol library. Now ships canonical SCIM schema JSONs as classpath resources, so external consumers get a self-contained dependency.
- `com.independentid:i2scim-client` — the standalone SCIM client. Pulls schema definitions transitively from `i2scim-core`.

The application module (currently `i2scim-server` after slice 6 lands; was `i2scim-universal` before that) must NOT publish. Its POM should declare `<maven.deploy.skip>true</maven.deploy.skip>` to make accidental `mvn deploy` invocations safe.

## Prerequisites

1. **GPG key for signing.** Configured locally with `gpg --list-secret-keys`. The key ID is what you'll pass as `gpg.keyname`.
2. **OSSRH credentials.** A Sonatype OSSRH account with deploy rights to `com.independentid`. Stored in `~/.m2/settings.xml` under server-id `ossrh`:
   ```xml
   <servers>
     <server>
       <id>ossrh</id>
       <username>YOUR_OSSRH_USERNAME</username>
       <password>YOUR_OSSRH_TOKEN</password>
     </server>
     <!-- Optional: if your GPG key uses a passphrase, store it under the keyname -->
     <server>
       <id>YOUR_GPG_KEYNAME</id>
       <passphrase>YOUR_GPG_PASSPHRASE</passphrase>
     </server>
   </servers>
   ```
3. **Released artifacts must satisfy Sonatype's Central-sync requirements.** The preserved `<licenses>`, `<developers>`, `<scm>`, and `<url>` blocks in the POMs already cover the metadata side. If the sync rules have evolved, check Sonatype's current requirements before publishing.

## XML to paste back

### 1. Parent `pom.xml` — `<distributionManagement>`

Add inside `<project>`, conventionally after `<developers>`:

```xml
<distributionManagement>
    <snapshotRepository>
        <id>ossrh</id>
        <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
    </snapshotRepository>
    <repository>
        <id>ossrh</id>
        <url>https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/</url>
    </repository>
</distributionManagement>
```

### 2. Parent `pom.xml` — `<pluginManagement>` entries

Add to the existing `<build><pluginManagement><plugins>` block:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <version>3.2.7</version>
</plugin>
<plugin>
    <groupId>org.sonatype.plugins</groupId>
    <artifactId>nexus-staging-maven-plugin</artifactId>
    <version>1.7.0</version>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.11.2</version>
    <configuration>
        <release>25</release>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <version>3.3.1</version>
</plugin>
```

### 3. Parent `pom.xml` — `release` profile

Add as a sibling of `<build>`:

```xml
<profiles>
    <profile>
        <id>release</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-gpg-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>sign-artifacts</id>
                            <phase>verify</phase>
                            <goals>
                                <goal>sign</goal>
                            </goals>
                            <configuration>
                                <gpgArguments>
                                    <arg>--pinentry-mode</arg>
                                    <arg>loopback</arg>
                                </gpgArguments>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.sonatype.plugins</groupId>
                    <artifactId>nexus-staging-maven-plugin</artifactId>
                    <extensions>true</extensions>
                    <configuration>
                        <serverId>ossrh</serverId>
                        <nexusUrl>https://s01.oss.sonatype.org/</nexusUrl>
                        <autoReleaseAfterClose>false</autoReleaseAfterClose>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 4. `i2scim-core/pom.xml` and `i2scim-client/pom.xml`

In each library module's `<build><plugins>`, add the source and javadoc plugin executions:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals>
                <goal>jar-no-fork</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

And add a per-module `release` profile that signs with the same GPG key the parent profile sets up:

```xml
<profiles>
    <profile>
        <id>release</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-gpg-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>sign-artifacts</id>
                            <phase>verify</phase>
                            <goals>
                                <goal>sign</goal>
                            </goals>
                            <configuration>
                                <keyname>${gpg.keyname}</keyname>
                                <passphraseServerId>${gpg.keyname}</passphraseServerId>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.sonatype.plugins</groupId>
                    <artifactId>nexus-staging-maven-plugin</artifactId>
                    <extensions>true</extensions>
                    <configuration>
                        <serverId>ossrh</serverId>
                        <nexusUrl>https://s01.oss.sonatype.org/</nexusUrl>
                        <autoReleaseAfterClose>false</autoReleaseAfterClose>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 5. Application module — opt-out

In the application module's POM (currently `i2scim-server`, after slice 6 of the consolidation refactor), add:

```xml
<properties>
    <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

This prevents `mvn deploy -P release` from accidentally pushing the app JAR to OSSRH. Only the libraries publish.

## Releasing

Once the XML is restored:

```bash
# Snapshot deploy
mvn deploy -P release -Dgpg.keyname=YOUR_GPG_KEYNAME

# Staging release
mvn versions:set -DnewVersion=X.Y.Z
mvn clean deploy -P release -Dgpg.keyname=YOUR_GPG_KEYNAME
# Then close + release the staging repository at https://s01.oss.sonatype.org/

# Tag and push
git tag -s vX.Y.Z -m "Release vX.Y.Z"
git push origin vX.Y.Z
```

## Notes

- The `cyclonedx-maven-plugin` in the parent POM produces `target/bom.json` at the `package` phase. It is independent of publishing and stays whether or not you restore Maven Central.
- The Docker image build (`build.sh` / `Dockerfile.jvm`) is independent of publishing.
- `<licenses>`, `<developers>`, `<scm>` blocks in the existing POMs are preserved precisely so they don't have to be reconstructed at restoration time.
