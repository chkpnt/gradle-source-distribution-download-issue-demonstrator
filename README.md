This repository demonstrates the issues around `SourceDistributionProvider` in Gradle
and provides a local mirror infrastructure to reproduce them in a simulated
network-restricted environment.

In order to clone this repository, [`git-lfs`](https://git-lfs.com/) needs to be installed due to the files in [gradle-distributions](gradle-distributions).

# Background

- [#27863](https://github.com/gradle/gradle/issues/27863): SourceDistributionProvider contains hardcoded distributionUrl referring to https://services.gradle.org
  - Partly fixed in 9.4.0-RC-1 (PR [#35963](https://github.com/gradle/gradle/pull/35963)),
    but the fix did not cover projects with included builds or `buildSrc`.
  - The regression due to [#27863](https://github.com/gradle/gradle/issues/27863) is addressed by PR [#37099](https://github.com/gradle/gradle/pull/37099) (commit [`ac7725e`](https://github.com/gradle/gradle/commit/ac7725e0abd4bff2675fa1d61e7573582ae92aeb), pending merge).
- [#36726](https://github.com/gradle/gradle/issues/36726): SourceDistributionProvider doesn't derive url from distributionUrl for included builds
  - GH-issue for the included build issue.
  - Fixed by PR [#36778](https://github.com/gradle/gradle/pull/36778) with target 9.4.0-RC-2.
  - Worked fine (commit [`8e5080a`](https://github.com/gradle/gradle/commit/8e5080abc7b184482862556405eca1856b58d144))
- [#36797](https://github.com/gradle/gradle/issues/36797): Intellij sync fails with Could not find gradle:gradle:9.4.0-rc-1 for offline distribution URLs)
  - The fix for [#27863](https://github.com/gradle/gradle/issues/27863) caused the
    source ZIP URL to always be derived from `distributionUrl`. If that server does not
    serve a source ZIP, the build fails.
  - Addressed by PRs [#36837](https://github.com/gradle/gradle/pull/36837) and
    [#36839](https://github.com/gradle/gradle/pull/36839), which add a fallback to
    `https://services.gradle.org`. Included in 9.4.0-RC-2, but this reintroduced the
    original problem, causing [#27863](https://github.com/gradle/gradle/issues/27863)
    to be reopened.

# How to use this repository

This Compose setup simulates a network-restricted environment where direct internet
access is blocked, as commonly found in corporate or air-gapped CI/CD infrastructures.
It provides an internal mirror for Maven repositories and Gradle distributions , combining [Reposilite](https://reposilite.com/) and
[Apache Traffic Server](https://trafficserver.apache.org/) (ATS) as caching reverse
proxy with [Caddy](https://caddyserver.com/) as the TLS-terminating front end.

Requests to `https://gradle-distributions.internal/distributions/` are routed through Caddy to ATS,
which transparently caches responses from `https://services.gradle.org/distributions/` on disk — avoiding
repeated downloads of large distribution ZIPs across builds. Locally provided
distributions can be placed in `gradle-distributions/` and are served directly under
`https://gradle-distributions.internal/local/` without hitting the upstream at all, simulating a fully offline mirror.

[Reposilite](https://reposilite.com/) acts as a caching Maven repository proxy,
mirroring the following upstream repositories:

| ID                        | Upstream                                                    |
|---------------------------|-------------------------------------------------------------|
| `maven-central`           | `https://repo1.maven.org/maven2`                            |
| `maven-central-snapshots` | `https://central.sonatype.com/repository/maven-snapshots`   |
| `gradle-plugins`          | `https://plugins.gradle.org/m2`                             |
| `gradle-libs`             | `https://repo.gradle.org/gradle/list/libs-releases`         |

All artifacts fetched through Reposilite are stored locally (`store: true`), so
subsequent requests are served from the internal mirror without internet access.

Caddy issues TLS certificates for `gradle-distributions.internal` and
`maven-mirror.internal`, signed by its internal CA. The root CA certificate and the corresponding private key is included
in this repository under [`pki/root.crt`](pki/root.crt) and [`pki/root.key`](pki/root.key).

To start the containers, just execute

```shell
$ podman compose up
```

## DNS Resolution

To resolve the internal domains, add the following to your `/etc/hosts`:

```
127.0.0.1	gradle-distributions.internal maven-mirror.internal
```

This is only required if you need to access the Reposilite web UI at
`https://maven-mirror.internal:8443`. For Gradle itself, it is better to use the JVM
option `-Djdk.net.hosts.file` to keep the internal domains scoped to the JVM — otherwise
Gradle can still reach `https://services.gradle.org` directly:

```shell
$ export _JAVA_OPTIONS=-Djdk.net.hosts.file=$(realpath hosts.txt)

# For IntelliJ on macOS
$ launchctl setenv _JAVA_OPTIONS -Djdk.net.hosts.file=$(realpath hosts.txt)
```

## Examples

It is also recommended to set `GRADLE_USER_HOME` to a directory other than ~/.gradle
to keep the test environment isolated.

This repository includes two examples, [simple-project](examples/simple-project) and [openrewrite](examples/openrewrite) as a more complex example. For the latter, you need to have several JDKs installed. The gradle projects use [`pki/truststore.jks`](pki/truststore.jks) as their trust store, which contains only the internal root CA.
