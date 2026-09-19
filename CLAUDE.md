# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4.1.1 / Java 25 REST API (Maven, base package `br.com.erudio`) from the Erudio Udemy course "REST API's RESTful do 0 à AWS e GCP". The README is course marketing only. The app manages People and Books, with JWT auth, file upload/download, CSV/XLSX/PDF import/export and email sending. `Collections/` holds the Postman collection and environment.

The stack was upgraded from Spring Boot 3.4.1 / Java 21 with the requirement that the API behaves exactly as before (same status codes, payloads, field order, date formats, validation). Several settings below exist only for that reason; do not "clean them up".

## Commands

There is no Maven wrapper; use the system `mvn`, with `JAVA_HOME` pointing at a JDK 25.

```bash
mvn spring-boot:run                    # run the app (needs MySQL, see below)
mvn clean package -DskipTests          # build the jar
mvn test                               # everything (needs Docker)
mvn test -Dtest=PersonServicesTest     # one test class
mvn flyway:migrate                     # apply migrations manually (pom hardcodes localhost DB + root/admin123)
```

Tests split into two kinds:
- `unittests/**` (`PersonServicesTest`, `BookServicesTest`, `ObjectMapperTests`) use Mockito and need nothing external.
- `integrationtests/**` and `repository/PersonRepositoryTest` extend `AbstractIntegrationTest`, which starts a **Testcontainers `mysql:9.1.0`** container, so Docker must be running. The app then boots on port **8888** (`src/test/resources/application.yml`, `TestConfigs.SERVER_PORT`) and RestAssured calls it.

The integration tests are order-dependent: `@TestMethodOrder(OrderAnnotation)` with a static `specification` built in the `@Order(0)` `signin` test. Running a single method such as `-Dtest=PersonControllerJsonTest#createTest` fails because the token was never obtained. Run the whole class.

Tests use JUnit 6 (Jupiter) assertions plus Hamcrest, and Jackson 3 (`tools.jackson.*`) to read responses.

## Runtime setup

- MySQL at `localhost:3306`, schema `rest_with_spring_boot_erudio`, `root`/`admin123` (in `application.yml`). Flyway (`src/main/resources/db/migration/V1..V18`) runs at startup, with `ddl-auto: none`. Schema and seed changes go in a new `V<n>__*.sql`; never edit an applied one. Some seed scripts insert random data, so two fresh databases are not identical.
- SMTP credentials come from the env vars `EMAIL_USERNAME` / `EMAIL_PASSWORD` (Gmail SMTP).
- `FileStorageService` creates `file.upload-dir` (`/Code/UploadDir`) in its constructor at startup. On Windows this resolves against the current drive root.
- Swagger UI is served at `/swagger-ui/index.html`.
- Seeded login used by the tests: `leandro` / `admin123`.
- PDF export compiles the `.jrxml` templates at request time. That only works when the classes are on a normal classpath (`mvn spring-boot:run`, an IDE, or an exploded jar); from `java -jar` on the fat jar the compile fails with `cannot find symbol JREvaluator`. The compile also opened an outbound HTTPS connection in one run (a `ConnectException` was the root cause of a failed export), and export failures reach the client as an empty `403`, not a `500`.

## Architecture

Standard layering: `controllers` → `services` → `repository` (Spring Data JPA) → `model` entities. A few cross-cutting pieces span several files:

- **Entity ↔ DTO mapping**: services never expose entities. They convert with the static `ObjectMapper.parseObject(...)` in `mapper/` (a Dozer wrapper, not Jackson's `ObjectMapper`). DTOs extend HATEOAS `RepresentationModel`, and services add links in a private `addHateoasLinks(dto)` using `linkTo(methodOn(XController.class)...)`. Adding or renaming a controller method means updating those links too. Paged results go through `PagedResourcesAssembler` (`buildPagedModel`).
- **OpenAPI docs live in interfaces**: each controller implements a `controllers/docs/*ControllerDocs` interface that carries the springdoc annotations. Put doc annotations there, not on the controller.
- **Content negotiation**: endpoints produce JSON, XML and YAML (`WebConfig.configureContentNegotiation`, `tools.jackson.dataformat` xml and yaml). The `withjson/`, `withxml/` and `withyaml/` integration-test packages mirror each other, so an endpoint change usually needs all three updated. The YAML converter is built in `config/JacksonConfig`.
- **Import/export strategy factories**: `FileExporterFactory` picks `CsvExporter` / `XlsxExporter` / `PdfExporter` from the request's `Accept` header (custom constants in `file/exporter/MediaTypes`). `FileImporterFactory` picks `CsvImporter` / `XlsxImporter` from the filename extension. Both look the implementation up as a bean via `ApplicationContext`. To add a format, add a `@Component` implementation and a branch in the factory. `PersonController.exportPage` also maps Accept type to file extension itself.
- **PDF export**: `person.jrxml` embeds the `books` subreport and a QR code from `QRCodeService` (ZXing); templates are in `src/main/resources/templates/`.
- **Security** (`config/SecurityConfig`, `security/jwt/*`): stateless JWT via auth0 `java-jwt`, with `JwtTokenFilter` added before `UsernamePasswordAuthenticationFilter`. `/auth/signin`, `/auth/refresh/**`, `/auth/createUser`, `/swagger-ui/**` and `/v3/api-docs/**` are public. `/api/**` requires authentication and `/users` is denied. `UserService` is the `UserDetailsService`, and `model/User` is the `UserDetails`. Passwords are PBKDF2 (custom params in `SecurityConfig.passwordEncoder`). `Startup.generateHashedPassword()` (call commented out in `main`) prints hashes for seeding users in migrations.
- **CORS** origins come from `cors.originPatterns` in `application.yml`, applied in `WebConfig`. `PersonControllerCorsTest` checks it.
- **Error handling**: `exception/hadler/CustomEntityResponseHandler` (the package name is misspelled `hadler`) maps custom exceptions to HTTP statuses. Anything unmapped becomes a 500 with an `ExceptionResponse` body.
- **Dependency injection** is mostly field `@Autowired`, which is what the Mockito `@InjectMocks` unit tests rely on.

## Settings that preserve the pre-upgrade behavior

- `src/test/resources/application.yml` is a full copy of the main one, not an overlay. Any change to the settings below must be made in both files, otherwise the tests run a different configuration from production.
- `spring.jackson.use-jackson2-defaults: true` keeps the JSON/XML output as it was: declaration order instead of Jackson 3's alphabetical order, and dates as `+00:00` instead of `Z`.
- `config/JacksonConfig` covers what that flag does not: it moves the HATEOAS `links` after the DTO's own properties (Jackson 3 puts inherited ones first; `CollectionModel` is excluded because it always had `links` first), and it builds the YAML converter (declaration order, dates as epoch milliseconds, unknown properties ignored). Spring Boot only configures the JSON and XML mappers.
- `CustomEntityResponseHandler.createResponseEntity` sets `type: about:blank` on every `ProblemDetail`, because Spring Framework 7 no longer defaults it and the field would disappear from the error payloads.
- `spring.jpa.properties.hibernate.check_nullability: true` is required. springdoc 3 pulls in Bean Validation, and Hibernate then silently turns its own not-null check off, so a `Book` with a null `title` was persisted instead of failing.
- `springdoc.api-docs.version: openapi_3_0` keeps the OpenAPI 3.0 document (springdoc 3 defaults to 3.1). The neighbouring `spring-doc:` block uses a prefix springdoc never reads (it binds `springdoc.*`), so it has no effect; renaming it to `springdoc:` would start applying its `paths-to-match` and `use-root-path` and change behavior.
- Boot 4 split its auto-configuration into modules, so the raw libraries no longer auto-configure. Keep `spring-boot-starter-flyway` (migrations do not run with plain `flyway-core`), `spring-boot-starter-hateoas` (springdoc fails to start without it) and `spring-boot-starter-webmvc`.

## Dependency versions

Versions are inherited from the Spring Boot parent wherever it manages them (Flyway, Jackson, Hibernate, Testcontainers, JUnit, Mockito, the MySQL driver used by the Flyway plugin via `${mysql.version}`). Only libraries the BOM does not manage carry an explicit version property in `pom.xml`: springdoc, REST Assured, POI, commons-csv, JasperReports, ZXing, java-jwt and Dozer.

## Notes

- `spring-boot-devtools` is on the runtime classpath.
- `TestLogController` (`/api/test/v1`) is a demo endpoint for exercising log levels.
