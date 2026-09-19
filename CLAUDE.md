# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 3.4.1 / Java 21 REST API (Maven, base package `br.com.erudio`) from the Erudio Udemy course "REST API's RESTful do 0 à AWS e GCP". The README is course marketing only. The app manages People and Books, with JWT auth, file upload/download, CSV/XLSX/PDF import/export and email sending. `Collections/` holds the Postman collection and environment.

## Commands

There is no Maven wrapper; use the system `mvn`.

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

## Runtime setup

- MySQL at `localhost:3306`, schema `rest_with_spring_boot_erudio`, `root`/`admin123` (in `application.yml`). Flyway (`src/main/resources/db/migration/V1..V18`) runs at startup, with `ddl-auto: none`. Schema and seed changes go in a new `V<n>__*.sql`; never edit an applied one.
- SMTP credentials come from the env vars `EMAIL_USERNAME` / `EMAIL_PASSWORD` (Gmail SMTP).
- `FileStorageService` creates `file.upload-dir` (`/Code/UploadDir`) in its constructor at startup. On Windows this resolves against the current drive root.
- Swagger UI is served at `/swagger-ui/index.html`.
- Seeded login used by the tests: `leandro` / `admin123`.

## Architecture

Standard layering: `controllers` → `services` → `repository` (Spring Data JPA) → `model` entities. A few cross-cutting pieces span several files:

- **Entity ↔ DTO mapping**: services never expose entities. They convert with the static `ObjectMapper.parseObject(...)` in `mapper/` (a Dozer wrapper, not Jackson's `ObjectMapper`). DTOs extend HATEOAS `RepresentationModel`, and services add links in a private `addHateoasLinks(dto)` using `linkTo(methodOn(XController.class)...)`. Adding or renaming a controller method means updating those links too. Paged results go through `PagedResourcesAssembler` (`buildPagedModel`).
- **OpenAPI docs live in interfaces**: each controller implements a `controllers/docs/*ControllerDocs` interface that carries the springdoc annotations. Put doc annotations there, not on the controller.
- **Content negotiation**: endpoints produce JSON, XML and YAML (`WebConfig.configureContentNegotiation`, `jackson-dataformat-xml` and `-yaml`). The `withjson/`, `withxml/` and `withyaml/` integration-test packages mirror each other, so an endpoint change usually needs all three updated. `serialization/converter/YamlJackson2HttpMessageConverter` is not referenced anywhere in the code.
- **Import/export strategy factories**: `FileExporterFactory` picks `CsvExporter` / `XlsxExporter` / `PdfExporter` from the request's `Accept` header (custom constants in `file/exporter/MediaTypes`). `FileImporterFactory` picks `CsvImporter` / `XlsxImporter` from the filename extension. Both look the implementation up as a bean via `ApplicationContext`. To add a format, add a `@Component` implementation and a branch in the factory. `PersonController.exportPage` also maps Accept type to file extension itself.
- **PDF export**: JasperReports compiles the `.jrxml` templates in `src/main/resources/templates/` at request time. `person.jrxml` embeds the `books` subreport and a QR code from `QRCodeService` (ZXing).
- **Security** (`config/SecurityConfig`, `security/jwt/*`): stateless JWT via auth0 `java-jwt`, with `JwtTokenFilter` added before `UsernamePasswordAuthenticationFilter`. `/auth/signin`, `/auth/refresh/**`, `/auth/createUser`, `/swagger-ui/**` and `/v3/api-docs/**` are public. `/api/**` requires authentication and `/users` is denied. `UserService` is the `UserDetailsService`, and `model/User` is the `UserDetails`. Passwords are PBKDF2 (custom params in `SecurityConfig.passwordEncoder`). `Startup.generateHashedPassword()` (call commented out in `main`) prints hashes for seeding users in migrations.
- **CORS** origins come from `cors.originPatterns` in `application.yml`, applied in `WebConfig`. `PersonControllerCorsTest` checks it.
- **Error handling**: `exception/hadler/CustomEntityResponseHandler` (the package name is misspelled `hadler`) maps custom exceptions to HTTP statuses. Anything unmapped becomes a 500 with an `ExceptionResponse` body.
- **Dependency injection** is mostly field `@Autowired`, which is what the Mockito `@InjectMocks` unit tests rely on.

## Notes

- `spring-boot-devtools` is on the runtime classpath.
- `TestLogController` (`/api/test/v1`) is a demo endpoint for exercising log levels.
