# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4.1.1 / Java 25 REST API (Maven, base package `br.com.erudio`) from the Erudio course "Formação Spring Boot 2026: do Zero ao Deploy na AWS e GCP com Java, Docker e Kubernetes". The README is course marketing only. The app manages People and Books, with JWT auth, file upload/download, CSV/XLSX/PDF import/export and email sending. `Collections/` holds the Postman collection and environment.

The stack was upgraded from Spring Boot 3.4.1 / Java 21 with the requirement that the API behaves exactly as before (same status codes, payloads, field order, date formats, validation). Several settings below exist only for that reason; do not "clean them up". The deliberate exceptions are the `launchDate` of a book (now a `LocalDate`) and the empty `links` that YAML lists used to repeat; both are described below.

## Commands

There is no Maven wrapper; use the system `mvn`, with `JAVA_HOME` pointing at a JDK 25.

```bash
mvn spring-boot:run                    # run the app (needs MySQL, see below)
mvn clean package -DskipTests          # build the jar
mvn test                               # everything (needs Docker)
mvn test -Dtest=PersonServicesTest     # one test class
mvn flyway:migrate                     # apply migrations manually (pom hardcodes localhost DB + root/admin123)
docker compose up -d --build           # app on :8080 + MySQL 9 on :3308 (run "mvn clean package" first, the image copies target/*.jar)
```

Tests split into two kinds:
- `unittests/**` (`PersonServicesTest`, `BookServicesTest`, `ObjectMapperTests`) use Mockito and need nothing external.
- `integrationtests/**` and `repository/PersonRepositoryTest` extend `AbstractIntegrationTest`, which starts a **Testcontainers `mysql:9.1.0`** container, so Docker must be running. The app then boots on port **8888** (`src/test/resources/application.yml`, `TestConfigs.SERVER_PORT`) and RestAssured calls it.

The integration tests are order-dependent: `@TestMethodOrder(OrderAnnotation)` with a static `specification` built in the `@Order(0)` `signin` test. Running a single method such as `-Dtest=PersonControllerJsonTest#createTest` fails because the token was never obtained. Run the whole class.

Tests use JUnit 6 (Jupiter) assertions plus Hamcrest, and Jackson 3 (`tools.jackson.*`) to read responses.

E-mail, upload and the reports have their own tests: unit tests in `unittests/{mail,file,services}` (`EmailServiceTest`, `EmailSenderTest`, `FileStorageServiceTest`, `QRCodeServiceTest`, the CSV/XLSX/PDF exporters and importers, the two factories, `PersonServiceFilesTest`) and integration tests `EmailControllerTest`, `FileControllerTest` and `PersonControllerFilesTest`. New integration tests extend `AuthenticatedIntegrationTest`, which signs in once and hands out `authenticated()` / `anonymous()` request specs.
- `AbstractIntegrationTest` also starts **GreenMail**, an in-process SMTP server on a dynamic port with the account `sender@erudio.test` / `secret`, and points `spring.mail.*` at it (`greenMail()` gives the tests access), so no test can reach a real mail server.
- The test `file.upload-dir` is `target/test-uploads`, not the drive root used by the main config.
- `AbstractIntegrationTest` creates the container with `.withConfigurationOverride("mysql-default-conf")`, the low-memory `my.cnf` bundled in the Testcontainers jar. The deprecated `org.testcontainers.containers.MySQLContainer` applied it implicitly and the current `org.testcontainers.mysql.MySQLContainer` does not. Without it the tests still run, but several books share the title `The Art of Agile Development`, the order among those ties changes, and `findAllTest` in the three `BookController*Test` classes fails (`98.73` instead of `97.21`).
- The surefire `argLine` in `pom.xml` loads Mockito as a `-javaagent` (through the `maven-dependency-plugin` `properties` goal, which exposes `${org.mockito:mockito-core:jar}`) because JDK 21+ warns about Mockito attaching itself at runtime, and passes `--enable-native-access=ALL-UNNAMED` for the JNA that Testcontainers loads. There is no `@{argLine}` in it, so adding JaCoCo later means adding that token.
- The PDF templates download images from `raw.githubusercontent.com` while the report is generated, so the PDF tests are skipped (via `NetworkAssumptions`) when that host is unreachable.
- `people.jrxml` asks for the `Arial` font, which Linux machines (including GitHub Actions `ubuntu-latest`) do not have. `src/main/resources/jasperreports.properties` (`net.sf.jasperreports.awt.ignore.missing.font=true`, read by JasperReports only from the classpath, not from `-D`) keeps the PDF tests from failing with `JRFontNotFoundException`. Windows and macOS never show the problem, so check the PDF tests in a Linux container (`docker run --rm -v <copy-of-the-project>:/work -w /work maven:3.9-eclipse-temurin-25-noble mvn -B test -Dtest=PdfExporterTest`) before blaming the CI.
- Tests that import people (`massCreation`) delete what they created in `@AfterEach`; the other integration tests assume the seeded people are the only ones present.

## Runtime setup

- MySQL at `localhost:3306`, schema `rest_with_spring_boot_erudio`, `root`/`admin123` (in `application.yml`). Flyway (`src/main/resources/db/migration/V1..V18`) runs at startup, with `ddl-auto: none`. Schema and seed changes go in a new `V<n>__*.sql`; never edit an applied one. Some seed scripts insert random data, so two fresh databases are not identical.
- SMTP credentials come from the env vars `EMAIL_USERNAME` / `EMAIL_PASSWORD` (Gmail SMTP). `email.subject` and `email.message` in `application.yml` are the fallback subject and message (`EmailDefaultsConfig`), used only for the fields a request leaves out or blank; whatever the request sets always wins.
- `FileStorageService` creates `file.upload-dir` (`/Code/UploadDir`) in its constructor at startup. On Windows this resolves against the current drive root.
- Swagger UI is served at `/swagger-ui/index.html`.
- Seeded login used by the tests: `leandro` / `admin123`.
- PDF export compiles the `.jrxml` templates at request time. That only works when the classes are on a normal classpath (`mvn spring-boot:run`, an IDE, or an exploded jar); from `java -jar` on the fat jar it fails for two separate reasons: `javac` cannot read the nested jars (`cannot find symbol JREvaluator`), and `PdfExporter` passes the `books.jasper` sub-report as a file path (`FileNotFoundException: app.jar!/templates/books.jasper`). The `Dockerfile` works around both. Separately, `people.jrxml` asks for the `Arial` font, which exists on Windows but not on Linux (GitHub runners, containers): without `src/main/resources/jasperreports.properties` (`net.sf.jasperreports.awt.ignore.missing.font=true`, which JasperReports reads only from the classpath, not from `-D`) the people report fails with `JRFontNotFoundException`, and so do `PdfExporterTest` and the PDF integration tests. The compile also opened an outbound HTTPS connection in one run (a `ConnectException` was the root cause of a failed export), and export failures reach the client as an empty `403`, not a `500`.

## Architecture

Standard layering: `controllers` → `services` → `repository` (Spring Data JPA) → `model` entities. A few cross-cutting pieces span several files:

- **Entity ↔ DTO mapping**: services never expose entities. They convert with the static `ObjectMapper.parseObject(...)` in `mapper/` (a Dozer wrapper, not Jackson's `ObjectMapper`). DTOs extend HATEOAS `RepresentationModel`, and services add links in a private `addHateoasLinks(dto)` using `linkTo(methodOn(XController.class)...)`. Adding or renaming a controller method means updating those links too. Paged results go through `PagedResourcesAssembler` (`buildPagedModel`).
- **OpenAPI docs live in interfaces**: each controller implements a `controllers/docs/*ControllerDocs` interface that carries the springdoc annotations. Put doc annotations there, not on the controller.
- **Content negotiation**: endpoints produce JSON, XML and YAML (`WebConfig.configureContentNegotiation`, `tools.jackson.dataformat` xml and yaml). The `withjson/`, `withxml/` and `withyaml/` integration-test packages mirror each other, so an endpoint change usually needs all three updated. The YAML converter is built in `config/JacksonConfig`.
- **Import/export strategy factories**: `FileExporterFactory` picks `CsvExporter` / `XlsxExporter` / `PdfExporter` from the request's `Accept` header (custom constants in `file/exporter/MediaTypes`). `FileImporterFactory` picks `CsvImporter` / `XlsxImporter` from the filename extension. Both look the implementation up as a bean via `ApplicationContext`. To add a format, add a `@Component` implementation and a branch in the factory. `PersonController.exportPage` also maps Accept type to file extension itself.
- **PDF export**: `person.jrxml` embeds the `books` subreport and a QR code from `QRCodeService` (ZXing); templates are in `src/main/resources/templates/`. The sub-report the person PDF really renders is the precompiled binary `books.jasper` (loaded by path), not `books.jrxml`, so after editing `books.jrxml` it must be recompiled (`JasperCompileManager.compileReportToFile("src/main/resources/templates/books.jrxml", "src/main/resources/templates/books.jasper")`, with the JasperReports 7.0.8 classpath, e.g. from `mvn dependency:build-classpath`). Its `launchDate` field is a `java.time.LocalDate`. `people.jasper` and `person.jasper` are stale leftovers: both are compiled from the `.jrxml` at request time. A "not found" export asked with `Accept: application/pdf` only cannot render its JSON error and ends as an empty 403; add `application/json` to the `Accept` header to see the 404.
- **E-mail**: `EmailController` → `EmailService` → `EmailSender` (builds the MIME message; sender address is `spring.mail.username`). The request's `body` is the HTML message. For `/withAttachment` the upload is copied to a temporary file that is deleted afterwards, and the recipient sees the uploaded file's original name. The `emailRequest` part is parsed with Jackson 2 defaults, so unknown fields are rejected. `EmailSender` is a singleton holding the state of the message being built, so it is not safe for concurrent requests.
- **Security** (`config/SecurityConfig`, `security/jwt/*`): stateless JWT via auth0 `java-jwt`, with `JwtTokenFilter` added before `UsernamePasswordAuthenticationFilter`. `/auth/signin`, `/auth/refresh/**`, `/auth/createUser`, `/swagger-ui/**` and `/v3/api-docs/**` are public. `/api/**` requires authentication and `/users` is denied. `UserService` is the `UserDetailsService`, and `model/User` is the `UserDetails`. Passwords are PBKDF2 (custom params in `SecurityConfig.passwordEncoder`). `Startup.generateHashedPassword()` (call commented out in `main`) prints hashes for seeding users in migrations.
- **CORS** origins come from `cors.originPatterns` in `application.yml`, applied in `WebConfig`. `PersonControllerCorsTest` checks it.
- **Error handling**: `exception/hadler/CustomEntityResponseHandler` (the package name is misspelled `hadler`) maps custom exceptions to HTTP statuses. Anything unmapped becomes a 500 with an `ExceptionResponse` body.
- **Dependency injection** is mostly field `@Autowired`, which is what the Mockito `@InjectMocks` unit tests rely on.

## Settings that preserve the pre-upgrade behavior

- `src/test/resources/application.yml` is a full copy of the main one, not an overlay. Any change to the settings below must be made in both files, otherwise the tests run a different configuration from production.
- `spring.jackson.use-jackson2-defaults: true` keeps the JSON/XML output as it was: declaration order instead of Jackson 3's alphabetical order, and dates as `+00:00` instead of `Z`.
- `config/JacksonConfig` covers what that flag does not: it moves the HATEOAS `links` after the DTO's own properties (Jackson 3 puts inherited ones first; `CollectionModel` is excluded because it always had `links` first), and it builds the YAML converter (declaration order, dates as epoch milliseconds, unknown properties ignored, and a mix-in on `EntityModel` that leaves out its own empty `links`). Spring Boot only configures the JSON and XML mappers. The mix-in fixes the original output, where every item of a YAML list ended with a second `links: []` after its real `links:` (a duplicate key: SnakeYAML warns and a parser that keeps the last one reads the empty list); JSON (HAL `_links`) and XML never had the duplicate.
- `Book.launchDate` and `BookDTO.launchDate` are `java.time.LocalDate` (they were `java.util.Date` with `@Temporal(DATE)`, which Hibernate 7 deprecates). The column is still `datetime(6)`. This is the one intentional change to the API contract: responses now carry `"2017-11-29"` where they carried `2017-11-29T02:00:00.000+00:00` (JSON/XML) or epoch milliseconds (YAML), and requests are accepted only as `yyyy-MM-dd`; offset date-times (`...Z`, `...+00:00`) and epoch numbers are rejected with 400. `BookDTO.getLaunchDate` has `@JsonFormat(shape = STRING)` so the YAML mapper, which writes dates as timestamps, does not emit an array. The test DTO needs `LocalDateXmlAdapter` because the XML tests serialize the request with JAXB, which cannot marshal `LocalDate` (it wrote `<launchDate/>` and the API answered 500).
- `CustomEntityResponseHandler.createResponseEntity` sets `type: about:blank` on every `ProblemDetail`, because Spring Framework 7 no longer defaults it and the field would disappear from the error payloads.
- `spring.jpa.properties.hibernate.check_nullability: true` is required. springdoc 3 pulls in Bean Validation, and Hibernate then silently turns its own not-null check off, so a `Book` with a null `title` was persisted instead of failing.
- `springdoc.api-docs.version: openapi_3_0` keeps the OpenAPI 3.0 document (springdoc 3 defaults to 3.1). `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are set to `true` (their default) only because springdoc 3 logs a warning at startup when they are left implicit. `paths-to-match` and `swagger-ui.use-root-path` live in the same `springdoc:` block (they used to sit under a `spring-doc:` key springdoc never read; fixing it changed nothing observable).
- Boot 4 split its auto-configuration into modules, so the raw libraries no longer auto-configure. Keep `spring-boot-starter-flyway` (migrations do not run with plain `flyway-core`), `spring-boot-starter-hateoas` (springdoc fails to start without it) and `spring-boot-starter-webmvc`.

## Dependency versions

Versions are inherited from the Spring Boot parent wherever it manages them (Flyway, Jackson, Hibernate, Testcontainers, JUnit, Mockito, the MySQL driver used by the Flyway plugin via `${mysql.version}`). Only libraries the BOM does not manage carry an explicit version property in `pom.xml`: springdoc, REST Assured, POI, commons-csv, JasperReports, ZXing, java-jwt and Dozer.

## Docker and CI

- `Dockerfile` (`eclipse-temurin:25-jdk`) does not run `java -jar`: it unpacks `target/*.jar` into `/app` and starts `br.com.erudio.Startup` with `/app/BOOT-INF/classes:/app/BOOT-INF/lib/*` as classpath, so the two fat-jar PDF problems above do not happen. If those are ever fixed in the application, the image can go back to `COPY target/*.jar app.jar` + `ENTRYPOINT ["java","-jar","/app.jar"]`. The unpacking uses a BuildKit bind mount of `target/`, so `.dockerignore` must keep `!target/*.jar`.
- `docker-compose.yml`: `db` is `mysql:9` (host port 3308, since the developer's own MySQL owns 3306) with a healthcheck, and `app` waits for it (`condition: service_healthy`); without that the app restarts about 5 times while MySQL initializes. The skill template's `command: mysqld --default_authentication_plugin=mysql_native_password` must not be used: MySQL 9 removed the option and the container crash-loops. E-mail credentials are not in the compose file; add `EMAIL_USERNAME` / `EMAIL_PASSWORD` to the `app` environment if you need to send mail.
- `.github/workflows/continuous-deployment.yml` (the file the README badge points to) runs on push to `main`: Docker Hub login, Java 25 (temurin) setup, `mvn clean package` (runs all tests; the integration tests need Docker, which `ubuntu-latest` has), `docker compose build`, `docker compose up -d`, a wait loop on `http://localhost:8080/swagger-ui/index.html`, the Postman collection with `npx --yes newman@6.2.2` (Node comes with the runner; the version is pinned because the collection uses `pm.execution.skipRequest()`), the container logs on failure, `docker compose down -v` (always), and only then the push of `${DOCKER_USERNAME}/rest-with-spring-boot-erudio` tagged `latest` and `${{ github.run_id }}`. A failing Postman test therefore blocks the push. It needs the repository secrets `DOCKER_USERNAME` and `DOCKER_ACCESS_TOKEN`. Validate edits with `actionlint` (`docker run --rm -v <repo>:/repo -w /repo rhysd/actionlint`).
- `.claude/` (project skills and local settings) is git-ignored on purpose so the skills are not published.

## Postman

`Collections/` holds the Postman collection, its environment and `files/` (upload samples); `Collections/README.md` explains how to use and run them. Every request has `pm.test` assertions, and the whole collection runs green (50 requests including the 2 automatic signins, 92 test scripts, 171 assertions) with `npx newman run Collections/*.postman_collection.json -e Collections/SPRING_BOOT_JAVA_ERUDIO.postman_environment.json --working-dir Collections` against a running API. Keep it in sync with the API: a new or changed endpoint needs its request, its tests and, when it needs a value, an environment variable.

- A collection-level pre-request script signs in by itself when there is no valid token (skipped for `/auth/*`), so any request or folder runs alone. Only `/auth/*` requests and the ones that deliberately send no token do not depend on it.
- POST requests store `personId` / `bookId` in the environment; PUT, PATCH, DELETE and the "deleted" GET use them. The mass-creation tests delete the people they create. `Create new Users` generates a new username on every run (there is no way to delete users).
- The two e-mail requests that really send are skipped through `pm.execution.skipRequest()` unless the environment has `sendEmails=true`; the other e-mail requests (invalid recipient, malformed JSON, wrong content type) need no SMTP.
- Multipart requests must not carry a `Content-Type` header, and their `src` paths are relative (`files/...`), resolved against Postman's working directory (newman `--working-dir`).
- `massCreation` returns a plain list, so its links are in `links` (array), while single and paged responses use HAL `_links`.
- The book bodies send `launchDate` as `yyyy-MM-dd`: the API rejects offset date-times with 400, so the collection cannot be copied verbatim from the Kotlin twin, which still sends `2024-01-01T00:00:00.000+00:00`. Apart from that and `info.name` / `info.description`, the collection is the Kotlin one.
- PDF export only works on an exploded jar (see Runtime setup), so run the API with `docker compose up -d --build` (the `Dockerfile` unpacks the jar) or from `BOOT-INF/classes;BOOT-INF/lib/*`, never `java -jar`. Port 8080 is often taken on the developer's machine: publish another port and pass `--env-var baseUrl=http://localhost:<port>` to newman.
- After editing the collection, replay it with newman (full run, each folder alone, and a second full run) before committing; the saved response examples under each request are still the generic OpenAPI mocks, except the Auth ones.

## Notes

- The owner dislikes comments: do not add code comments (Java, XML, YAML, Dockerfile, ignore files); put explanations in this file or in the commit message instead.
- `spring-boot-devtools` is on the runtime classpath.
- `TestLogController` (`/api/test/v1`) is a demo endpoint for exercising log levels.
