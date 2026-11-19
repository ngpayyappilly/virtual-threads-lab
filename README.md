# virtual-threads-lab

A Threads-style social API (users, posts, comments) built to **run on Java 25 virtual threads and make their behaviour visible**: metrics, traces and logs flow to Grafana, and a k6 load test plus a couple of demo endpoints let you watch virtual threads under load, and watch them get pinned.

- **Spring Boot 4.1** on **Java 25** (Gradle), Spring MVC on Tomcat
- **Virtual threads** for every request (`spring.threads.virtual.enabled=true`)
- **PostgreSQL 17** with **Flyway** migrations, Spring Data JPA / Hibernate
- **Micrometer + OpenTelemetry**: metrics, traces and logs pushed over OTLP to Grafana (Prometheus, Tempo, Loki), plus an Actuator Prometheus endpoint
- **OpenAPI / Swagger UI**, RFC 9457 problem responses
- **Docker**: dev container with hot reload and remote debugging, production-style image, k6 load generator
- **Tests** run against a real Postgres via Testcontainers

## Contents

1. [Quick start](#quick-start)
2. [Service URLs](#service-urls)
3. [API reference](#api-reference)
4. [Monitoring virtual threads](#monitoring-virtual-threads) (the main event)
5. [Load testing](#load-testing)
6. [Logging and tracing](#logging-and-tracing)
7. [Configuration and profiles](#configuration-and-profiles)
8. [Database](#database)
9. [Testing and building](#testing-and-building)
10. [Docker details](#docker-details)
11. [Project layout](#project-layout)
12. [Troubleshooting](#troubleshooting)

## Quick start

Requirements: Docker (with Compose), and a **JDK 25** if you want to build or run on the host (Gradle picks it up through toolchains).

### Dev mode in Docker (recommended)

```bash
docker compose --profile dev up        # add -d to run in the background
```

This starts three containers: the app, Postgres and Grafana (`grafana/otel-lgtm`).

- The app runs `./gradlew bootRun` against your mounted sources and recompiles on change, so Spring Boot DevTools restarts it automatically (a few seconds per edit).
- Remote debugging: attach a JVM debugger to `localhost:5005`.
- The `dev` profile turns on debug logging for the app, one-line SQL logging and all Actuator endpoints.

Stop with `docker compose --profile dev down` (add `-v` to also delete the database and Gradle caches).

### On the host

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew bootRun
```

Spring Boot's docker-compose support starts Postgres and Grafana for you and wires the datasource to them. The services behind compose profiles (`dev`, `prod`, `load`) are not started.

### Production-style image

```bash
docker compose --profile prod up --build
```

Builds the multi-stage `Dockerfile` (JRE image, non-root user, Spring Boot layers) and runs it with the `prod` profile: **JSON (ECS) logs**, and only the `health`, `info`, `metrics` and `prometheus` Actuator endpoints. The dev and prod containers both use port 8080, so run one at a time.

### Try it

```bash
curl -s localhost:8080/api/v1/system/thread
# {"name":"tomcat-handler-2","virtual":true,"threadId":76}

curl -s "localhost:8080/api/v1/posts?size=5"
```

`"virtual":true` confirms the request was served by a virtual thread.

## Service URLs

| What | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Prometheus scrape endpoint | http://localhost:8080/actuator/prometheus |
| Actuator metrics | http://localhost:8080/actuator/metrics |
| Grafana | http://localhost:3000 (the default `grafana/otel-lgtm` login is `admin` / `admin` if you are prompted) |
| Postgres | `localhost:5432`, database / user / password `vtlab` |
| Remote debug (dev container) | `localhost:5005` |

The `prod` profile exposes only `health`, `info`, `metrics` and `prometheus`. The `dev` profile exposes every Actuator endpoint.

## API reference

Base path `/api/v1`. All bodies are JSON. Interactive docs are in Swagger UI.

### Users

| Method | Path | Description |
|---|---|---|
| GET | `/users` | List users (paginated) |
| GET | `/users/{id}` | Get a user |
| POST | `/users` | Create a user, returns `201` with a `Location` header |
| PUT | `/users/{id}` | Replace a user |
| DELETE | `/users/{id}` | Delete a user **and their posts and comments** (`204`) |

```json
{ "username": "alice", "email": "alice@example.com", "displayName": "Alice", "bio": "Loves virtual threads" }
```

Validation: `username` 3-30 chars of letters, digits, `_` or `.`; `email` valid and at most 255 chars; `displayName` at most 60; `bio` at most 160. Usernames and emails are unique, ignoring case.

### Posts

| Method | Path | Description |
|---|---|---|
| GET | `/posts?authorId=` | List posts, newest first, optionally by author |
| GET | `/posts/{id}` | Get a post (includes `commentCount`) |
| POST | `/posts` | Create a post, body `{ "authorId": 1, "content": "..." }` |
| PUT | `/posts/{id}` | Edit content, body `{ "content": "..." }` |
| DELETE | `/posts/{id}` | Delete a post **and its comments** |

`content` is required and at most 500 characters.

### Comments

| Method | Path | Description |
|---|---|---|
| GET | `/posts/{postId}/comments` | List comments on a post, oldest first |
| GET | `/posts/{postId}/comments/{id}` | Get a comment |
| POST | `/posts/{postId}/comments` | Body `{ "authorId": 1, "content": "..." }` |
| PUT | `/posts/{postId}/comments/{id}` | Edit content, body `{ "content": "..." }` |
| DELETE | `/posts/{postId}/comments/{id}` | Delete a comment |

### System / demo endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/system/thread` | Reports which thread served the request: `{name, virtual, threadId}` |
| GET | `/system/slow?millis=500` | Sleeps (simulated blocking I/O). A virtual thread unmounts from its carrier while it sleeps. Capped at 10 s. |
| GET | `/system/pinned?millis=100` | Sleeps **in a native frame** (a libc `qsort` whose comparator is a Java callback that sleeps), which pins the virtual thread to its carrier. A deliberate demo of a pinning bug; `synchronized` no longer pins on Java 24+. Capped at 10 s. |

`/system/slow` and `/system/pinned` exist to generate interesting thread behaviour. Remove them before turning this into a real service.

### Pagination

List endpoints take `page` (0-based), `size` (default 20, max 100) and `sort` (e.g. `sort=createdAt,desc`). Responses look like:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 2, "totalPages": 1 }
```

### Errors

Errors use RFC 9457 `application/problem+json`:

| Status | When | Example |
|---|---|---|
| 400 | Validation failed | `{"title":"Validation error","errors":{"username":"size must be between 3 and 30"}}` |
| 404 | Resource not found | `{"title":"Resource not found","detail":"User with id 9999 not found"}` |
| 409 | Duplicate username or email, or a data integrity violation | `{"title":"Conflict","detail":"Username 'alice' is already taken"}` |

### Example session

```bash
curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
  -d '{"username":"carol","email":"carol@example.com"}'
curl -s -X POST localhost:8080/api/v1/posts -H 'Content-Type: application/json' \
  -d '{"authorId":3,"content":"first thread"}'
curl -s -X POST localhost:8080/api/v1/posts/3/comments -H 'Content-Type: application/json' \
  -d '{"authorId":1,"content":"welcome!"}'
curl -s localhost:8080/api/v1/posts/3
```

## Monitoring virtual threads

### Why virtual threads need their own metrics

Micrometer's standard JVM metrics (`jvm.threads.live`, `daemon`, `peak`, ...) only describe **platform** threads. A virtual thread is cheap and there can be hundreds of thousands of them, so they need dedicated metrics. On Java 25 the picture is much better than on 21, and this app adds the pieces the JDK does not expose directly:

| Signal | Metric | Source | What it tells you |
|---|---|---|---|
| Virtual threads running or ready to run | `jvm.threads.virtual.live{scheduling.status=mounted\|queued}` | Micrometer, from the JDK's `VirtualThreadSchedulerMXBean` (**Java 25**) | `mounted`: running on a carrier right now. `queued`: ready to run but waiting for a free carrier. A growing `queued` means the carriers are the bottleneck. |
| Carrier pool | `jvm.threads.virtual.parallelism`, `jvm.threads.virtual.pool.size` | Micrometer, same MXBean (**Java 25**) | Target and current number of carrier threads (one per CPU core by default). |
| Virtual threads started / ended | `jvm.threads.virtual.started`, `jvm.threads.virtual.ended` | this app, from the `jdk.VirtualThreadStart` / `jdk.VirtualThreadEnd` JFR events | Thread creation rate (`rate(started)`): roughly one per request here. |
| **All** virtual threads not yet finished | `jvm.threads.virtual.alive` | this app (`started - ended`) | Includes **parked** threads, i.e. the ones waiting on I/O, which is where virtual threads spend most of their time. About 1 s behind (JFR batches events). |
| Pinned virtual threads | `jvm.threads.virtual.pinned` | Micrometer, `jdk.VirtualThreadPinned` JFR event | Times a virtual thread blocked while stuck to its carrier, and for how long. |
| Failed virtual thread starts | `jvm.threads.virtual.submit.failed` | Micrometer, `jdk.VirtualThreadSubmitFailed` JFR event | Scheduler failures. Should stay at 0. |
| Requests in flight, by thread type | `vtlab.http.requests.active{thread.type}` | this app (servlet filter) | How many HTTP requests are being served on virtual vs platform threads right now. |
| Platform thread count | `jvm.threads.live` | Micrometer | Stays flat under load when virtual threads are doing their job. |

`parked` threads are `alive - mounted - queued`. Pinned events shorter than the JFR event's threshold (20 ms by default) are not reported.

The scheduler meters (`live`, `parallelism`, `pool.size`) only exist on Java 25; on Java 21 they are absent. The start/end counters cost two JFR events per virtual thread; set `app.virtual-threads.lifecycle-events.enabled=false` to turn them off.

### Metric names: Actuator vs Grafana

The same metric has a different name depending on how it is read, because OTLP adds units and Prometheus-style naming differs:

| Signal | `/actuator/prometheus` | Grafana (OTLP) |
|---|---|---|
| In-flight requests | `vtlab_http_requests_active{thread_type=...}` | `vtlab_http_requests_active{thread_type=...}` |
| Mounted / queued | `jvm_threads_virtual_live_threads{scheduling_status=...}` | `jvm_threads_virtual_live{scheduling_status=...}` |
| Carrier pool | `jvm_threads_virtual_parallelism`, `jvm_threads_virtual_pool_size_threads` | `jvm_threads_virtual_parallelism`, `jvm_threads_virtual_pool_size` |
| Started / ended | `jvm_threads_virtual_started_threads_total`, `..._ended_threads_total` | `jvm_threads_virtual_started_total`, `jvm_threads_virtual_ended_total` |
| Alive (incl. parked) | `jvm_threads_virtual_alive_threads` | `jvm_threads_virtual_alive` |
| Pinned count | `jvm_threads_virtual_pinned_seconds_count` | `jvm_threads_virtual_pinned_milliseconds_count` |
| Pinned time | `..._pinned_seconds_sum` / `_max` | `..._pinned_milliseconds_sum` / `jvm_threads_virtual_pinned_max_milliseconds` |
| Submit failed | `jvm_threads_virtual_submit_failed_total` | `jvm_threads_virtual_submit_failed_total` |
| HTTP latency | `http_server_requests_seconds_*` | `http_server_requests_milliseconds_*` |
| Posts created | `vtlab_posts_total` | `vtlab_posts_created_total` |

Every metric is tagged `application="virtual-threads-lab"`.

### Watching it in Grafana

1. Open http://localhost:3000, then **Explore**.
2. Choose the **Prometheus** data source, switch the editor to **Code** mode, paste a query and run it. Set the time range to the last 15 minutes. Metrics arrive about a minute behind, since they are pushed once a minute.
3. Start a [load test](#load-testing) so there is something to see.

Useful queries (Grafana names):

| Question | PromQL |
|---|---|
| How many requests are on virtual threads right now? | `vtlab_http_requests_active` |
| Virtual threads running on carriers / waiting for one | `jvm_threads_virtual_live{scheduling_status="mounted"}`, `jvm_threads_virtual_live{scheduling_status="queued"}` |
| All virtual threads alive (incl. parked) | `jvm_threads_virtual_alive` |
| Parked virtual threads (waiting on I/O) | `sum(jvm_threads_virtual_alive) - sum(jvm_threads_virtual_live{scheduling_status=~"mounted|queued"})` |
| Carrier utilisation (1 = every carrier busy) | `sum(jvm_threads_virtual_live{scheduling_status="mounted"}) / sum(jvm_threads_virtual_parallelism)` |
| Virtual threads created per second | `rate(jvm_threads_virtual_started_total[1m])` |
| Peak in-flight requests over the last 10 min | `max_over_time(vtlab_http_requests_active[10m])` |
| Are platform threads staying flat? | `jvm_threads_live` |
| Is anything getting pinned (events per second)? | `sum(rate(jvm_threads_virtual_pinned_milliseconds_count[1m]))` |
| Longest pin, in ms | `jvm_threads_virtual_pinned_max_milliseconds` |
| Average pin time, in ms | `rate(jvm_threads_virtual_pinned_milliseconds_sum[1m]) / rate(jvm_threads_virtual_pinned_milliseconds_count[1m])` |
| Scheduler failures | `jvm_threads_virtual_submit_failed_total` |
| Requests per second by endpoint | `sum by (uri) (rate(http_server_requests_milliseconds_count[1m]))` |
| p95 latency of the slow endpoint | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_milliseconds_bucket{uri="/api/v1/system/slow"}[1m])))` |
| DB connections in use / waiting | `hikaricp_connections_active`, `hikaricp_connections_pending` |
| CPU and heap | `process_cpu_usage`, `jvm_memory_used_bytes{area="heap"}` |

A good first dashboard: graph `jvm_threads_virtual_alive` and `vtlab_http_requests_active` (by `thread_type`) next to `jvm_threads_live` and `jvm_threads_virtual_live` (by `scheduling_status`). Under load the first two climb into the hundreds while platform threads stay flat and only a handful of virtual threads are mounted at once.

`rate()` needs at least two pushed samples, so a burst shorter than about two minutes can read 0. Use a sustained load test, or look at the raw counters.

### Reading the results

- **In-flight virtual requests high, `jvm_threads_live` flat**: virtual threads are working. Hundreds of blocked requests are not costing platform threads.
- **`platform` in-flight is always 0**: nothing is being served on a platform thread. If it is not 0, virtual threads are off or something is running outside the request threads.
- **`alive` much larger than `mounted`**: most virtual threads are parked, waiting on I/O. That is the normal, healthy shape for an I/O-bound service. In a reference run, 146 threads were alive while at most 15 were mounted.
- **`mounted` equal to `parallelism`**: every carrier is busy. If `queued` is also above 0, virtual threads are waiting for a carrier, so the service is CPU-bound or carriers are being held (pinning, long CPU work).
- **Pinned count rising**: code blocks while a native frame (or a class initializer) is on the stack. With the `/system/pinned` demo you will see this immediately. Each pin holds a carrier thread, so enough pinned requests starve the whole scheduler.
- **Pinned `max` large**: one blocked native call can hold a carrier for that long.
- **`hikaricp_connections_pending` above 0**: virtual threads removed the thread limit, so the next limit is the database connection pool (10 connections by default). Many virtual threads queue for a connection. Size the pool or limit concurrency deliberately.
- **Latency of `/system/slow` near the sleep time even at high load**: nothing is queued. With platform threads, requests beyond Tomcat's 200 worker threads would wait.

### Pinning on Java 25

A virtual thread is **pinned** when it blocks but cannot unmount from its carrier thread, so the carrier is blocked too. What pins depends on the Java version:

| Blocking while... | Java 21 | Java 24+ (incl. 25) |
|---|---|---|
| inside a `synchronized` block or method | pins | **does not pin** (JEP 491) |
| a native method or foreign function frame is on the stack | pins | pins |
| a class initializer (`static {}`) is running | pins | pins |

That is why `/system/pinned` blocks inside a native `qsort` callback instead of `synchronized`: on Java 25 a `synchronized` demo would produce nothing. The test `synchronizedBlockingDoesNotPinOnJava25` pins that behaviour down.

Typical real-world sources of pinning on 25 are native libraries that call back into Java, JNI code, and blocking work inside static initializers.

To find the culprit when `jvm_threads_virtual_pinned_*` moves:

- **JFR**: record the `jdk.VirtualThreadPinned` event; it includes the stack trace and duration. For example start the app with `-XX:StartFlightRecording=filename=pins.jfr,settings=profile` and inspect it with `jfr print --events jdk.VirtualThreadPinned pins.jfr`.
- **`-Djdk.tracePinnedThreads`**: this used to print a stack trace on every pin, but it produces no output on Java 25 (checked). Use JFR.
- **Fix**: move the blocking call out of the static initializer or the native callback, or give it a dedicated platform-thread executor.

### Virtual thread dumps

Thread dumps from `jstack` do not include virtual threads. Use `jcmd`'s JSON dump:

```bash
docker compose exec app-dev sh -c \
  'PID=$(jcmd | grep VirtualThreadsLabApplication | cut -d" " -f1); \
   jcmd $PID Thread.dump_to_file -format=json /tmp/threads.json'
docker compose cp app-dev:/tmp/threads.json ./threads.json
```

Take one while a load test is running to see the virtual threads and their stacks.

### Comparing with platform threads

Turn virtual threads off and rerun the same load test:

```bash
VIRTUAL_THREADS=false docker compose --profile dev up -d app-dev   # Tomcat worker pool, 200 threads
docker compose --profile dev up -d app-dev                         # back to virtual threads
```

Check `curl localhost:8080/api/v1/system/thread`: it returns `"virtual":false` with a thread named `http-nio-8080-exec-N`. With platform threads, expect `vtlab_http_requests_active` to show up under `thread_type="platform"` and be capped by the Tomcat pool, `jvm_threads_live` to climb toward the pool size, and latency on `/system/slow` to grow once concurrent requests exceed the pool. (I have not benchmarked this comparison; run it and see.) The same switch is `spring.threads.virtual.enabled` in `application.yaml`.

## Load testing

The k6 script `loadtest/stress.js` ramps up three scenarios in parallel for about 90 seconds:

| Scenario | Traffic | What it shows |
|---|---|---|
| `crud` | Full flow: create user, post, comment, read, edit, delete | Realistic API load: Tomcat, JPA, the connection pool |
| `slow` | `GET /system/slow?millis=500` | Many concurrent blocked requests, the virtual thread sweet spot |
| `pinned` | `GET /system/pinned?millis=100` (10 constant users) | Pinning events in `jvm_threads_virtual_pinned_*` |

```bash
docker compose --profile dev --profile load run --rm k6
docker compose --profile dev --profile load run --rm -e PEAK_VUS=1000 k6   # more users per scenario
docker compose --profile dev --profile load run --rm -e PINNED=false k6   # skip the pinning scenario
```

k6 prints a summary (request rate, failures, latency percentiles). Each `crud` iteration deletes its own user, which cascades to its posts and comments, so the database does not grow.

Reference run on a laptop (Postgres in Docker, `PEAK_VUS=100`): about 2,560 requests per second with no failures, a peak of 114 requests in flight on virtual threads, and at most 6 database connections in use. Your numbers will differ.

## Logging and tracing

Every request is traced (sampling probability 1.0) and every log line carries the trace and span IDs.

**Text format (all profiles except `prod`):**

```
2026-11-19T05:32:57.257Z  INFO virtual-threads-lab [traceId=0f1e58c0e4869dff2931859e890d1c9b spanId=d0224383d92d4cf1] [tomcat-handler-2] c.c.v.user.UserService : Created user id=3 username=logger1
```

The thread name in brackets shows which thread logged: `tomcat-handler-N` for virtual threads.

**JSON (`prod` profile):** one ECS-format object per line with `traceId`, `spanId`, `log.level`, `log.logger`, `process.thread.name`, `message` and `service.name`.

**What gets logged:**
- an access line per API request (`POST /api/v1/users -> 409 (7 ms)`), skipping actuator, Swagger and API docs
- INFO for create / update / delete in the services, WARN for rejected requests
- SQL statements, one line each, in the `dev` profile (`org.hibernate.SQL`)

**In Grafana:**
- **Logs**: Explore, choose **Loki**, query `{service_name="virtual-threads-lab"}`. Logs are shipped over OTLP by an OpenTelemetry Logback appender (`logback-spring.xml`).
- **Traces**: Explore, choose **Tempo**. A log line's `trace_id` links to its trace.

## Configuration and profiles

| Setting | Where | Notes |
|---|---|---|
| `spring.threads.virtual.enabled` | `application.yaml` | Virtual threads for requests and task executors. In the containers, `VIRTUAL_THREADS=false` turns it off. |
| `spring.datasource.*` | `application.yaml` | Defaults to `localhost:5432/vtlab`. Overridden by docker-compose support on the host and by `SPRING_DATASOURCE_*` in the containers. |
| `spring.jpa.hibernate.ddl-auto` | `application.yaml` | `validate`. Flyway owns the schema. |
| `management.*` | `application.yaml` | Exposed endpoints, tags, latency histograms and SLO buckets for `http.server.requests` |
| `app.virtual-threads.lifecycle-events.enabled` | `application.yaml` | Turns the JFR-based `jvm.threads.virtual.started/ended/alive` meters on (default) or off |
| `app.sample-data.enabled` | `application.yaml` | Seeds two users, two posts and two comments into an empty database. Does nothing if users exist. |
| `management.otlp.metrics.export.url`, `management.opentelemetry.*` | container env in `compose.yaml` | Points OTLP metrics, traces and logs at the `grafana-lgtm` container |

Profiles:

- **`dev`** (dev container): debug logging for the app, SQL logging, all Actuator endpoints, DevTools polling.
- **`prod`** (prod container): JSON logs.
- **`test`** (tests): OTLP export disabled, sample data off.

## Database

PostgreSQL 17, schema managed by **Flyway** (`src/main/resources/db/migration`). Hibernate only validates that the entities match.

```
users (id, username, email, display_name, bio, created_at, updated_at)
posts (id, author_id -> users, content, created_at, updated_at)
comments (id, post_id -> posts, author_id -> users, content, created_at, updated_at)
```

Deleting a user or a post cascades to the rows that depend on it at the database level (`on delete cascade`). Usernames and emails are unique on `lower(...)`, so `Alice` and `alice` collide.

Add a migration as `V2__description.sql`; never edit an applied one. Data lives in the `postgres-data` Docker volume, so it survives restarts and is wiped by `docker compose down -v`.

Connect from the host:

```bash
docker compose exec postgres psql -U vtlab -d vtlab
```

## Testing and building

```bash
./gradlew test            # integration tests, needs Docker (Testcontainers starts a throwaway Postgres)
./gradlew clean build     # tests + jar at build/libs/virtual-threads-lab-0.0.1-SNAPSHOT.jar
java -jar build/libs/virtual-threads-lab-0.0.1-SNAPSHOT.jar
```

What the tests cover:

- full user / post / comment lifecycle, validation errors, duplicate detection and cascading deletes
- the Actuator metrics and Prometheus endpoints, including the virtual thread metrics
- a real server on a random port confirming requests run on virtual threads
- the virtual thread metrics: scheduler gauges, started/ended counters, `alive` counting parked threads, the native pinning demo being recorded as pinned, and `synchronized` blocking *not* being recorded
- the application context loading against real Postgres with Flyway

## Docker details

`compose.yaml` services:

| Service | Profile | Purpose |
|---|---|---|
| `postgres` | always | Database, with a health check |
| `grafana-lgtm` | always | Grafana, Prometheus, Tempo and Loki with an OTLP receiver |
| `app-dev` | `dev` | `gradlew bootRun` on mounted sources, hot reload, debug port 5005 |
| `app` | `prod` | Image built from `Dockerfile`, JSON logs |
| `k6` | `load` | Load generator running `loadtest/stress.js` |

The compose project is named `virtual-threads-lab`, so container and volume names do not depend on the folder name. Because the app services sit behind profiles, running `./gradlew bootRun` on the host only starts Postgres and Grafana.

The dev container keeps its Gradle state in named volumes (`gradle-home`, `app-gradle`, `app-build`), separate from your host's `.gradle` and `build` folders.

## Project layout

```
src/main/java/com/cloudwalkers/virtualthreadslab/
  user/ post/ comment/     entity, repository, service, controller and DTOs per resource
  common/                  exception handler (problem responses), pagination, request logging filter
  system/                  thread demo endpoints, virtual thread metrics, OTel log appender wiring, sample data
src/main/resources/
  application*.yaml        base, dev and prod config
  logback-spring.xml       text logs (dev) / ECS JSON (prod) + OTLP appender
  db/migration/            Flyway migrations
src/test/                  integration tests + Testcontainers config
loadtest/stress.js         k6 scenarios
compose.yaml, Dockerfile   local stack and production image
```

## Troubleshooting

- **`./gradlew clean` fails to delete `build/`**: the dev container mounts a volume over `build/`. Stop it first: `docker compose --profile dev stop app-dev`.
- **Tests fail with `error while creating mount source path ... docker.raw.sock`**: Testcontainers' cleanup helper (Ryuk) cannot start on Docker Desktop for Mac when `~/.testcontainers.properties` sets `tc.host` to `docker.raw.sock`. The build disables Ryuk for tests (`TESTCONTAINERS_RYUK_DISABLED`). Removing the `tc.host` line also fixes it.
- **`WARNING: A restricted method ... --enable-native-access`**: the `/system/pinned` demo uses the foreign function API. The Gradle run and test tasks and the Dockerfile pass `--enable-native-access=ALL-UNNAMED`; add it to any other way you launch the jar.
- **Port 8080 or 5432 already in use**: stop the other container (`app-dev` and `app` both use 8080) or the local service on that port.
- **No metrics in Grafana**: metrics are pushed once a minute, so wait a minute and widen the time range. After an app restart, old series linger for up to 5 minutes.
- **A rate query shows 0**: `rate()` needs two samples. Run a sustained load test.
- **The dev app does not pick up a change**: file events through Docker bind mounts can be slow; DevTools polls every second. A change to `build.gradle` needs `docker compose --profile dev restart app-dev`.
- **Everything restarts empty**: `docker compose down -v` deletes the Postgres volume. The sample data is recreated on the next start.

## Notes

- The database credentials (`vtlab` / `vtlab`) are for local development only. Move them to a secret store before deploying anywhere real.
- The `/system/slow` and `/system/pinned` endpoints are demos. Remove them from a real service.
