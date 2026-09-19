package br.com.erudio.integrationtests;

import br.com.erudio.config.TestConfigs;
import br.com.erudio.integrationtests.dto.AccountCredentialsDTO;
import br.com.erudio.integrationtests.testcontainers.AbstractIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static io.restassured.RestAssured.given;

/**
 * Base class for integration tests that call the secured endpoints: it signs in once and hands out
 * request specifications with the bearer token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class AuthenticatedIntegrationTest extends AbstractIntegrationTest {

    private static String accessToken;

    protected static RequestSpecification authenticated() {
        return new RequestSpecBuilder()
            .setPort(TestConfigs.SERVER_PORT)
            .addHeader(TestConfigs.HEADER_PARAM_AUTHORIZATION, "Bearer " + accessToken())
            .build();
    }

    protected static RequestSpecification anonymous() {
        return new RequestSpecBuilder()
            .setPort(TestConfigs.SERVER_PORT)
            .build();
    }

    private static synchronized String accessToken() {
        if (accessToken == null) {
            accessToken = given()
                .port(TestConfigs.SERVER_PORT)
                .basePath("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(new AccountCredentialsDTO("leandro", "admin123"))
                .when()
                    .post()
                .then()
                    .statusCode(200)
                .extract()
                    .path("accessToken");
            Assertions.assertNotNull(accessToken, "signin did not return an access token");
        }
        return accessToken;
    }
}
