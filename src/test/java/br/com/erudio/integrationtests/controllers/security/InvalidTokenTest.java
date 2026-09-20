package br.com.erudio.integrationtests.controllers.security;

import br.com.erudio.config.TestConfigs;
import br.com.erudio.integrationtests.AuthenticatedIntegrationTest;
import br.com.erudio.integrationtests.dto.AccountCredentialsDTO;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class InvalidTokenTest extends AuthenticatedIntegrationTest {

    @Value("${security.jwt.token.secret-key}")
    private String secretKey;

    private String token(String secret, Date expiresAt) {
        String key = Base64.getEncoder().encodeToString(secret.getBytes());
        return JWT.create()
            .withSubject("leandro")
            .withClaim("roles", List.of("ADMIN"))
            .withIssuedAt(new Date(expiresAt.getTime() - 3_600_000))
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(key.getBytes()));
    }

    private String expiredToken() {
        return token(secretKey, new Date(System.currentTimeMillis() - 60_000));
    }

    private void assertRejected(String bearer) {
        given().spec(anonymous())
            .header(TestConfigs.HEADER_PARAM_AUTHORIZATION, "Bearer " + bearer)
        .when()
            .get("/api/person/v1")
        .then()
            .statusCode(403)
            .body(emptyString());
    }

    private ValidatableResponse refreshRequest(String authorization) {
        return given().spec(anonymous())
            .header(TestConfigs.HEADER_PARAM_AUTHORIZATION, authorization)
            .accept(MediaType.APPLICATION_JSON_VALUE)
        .when()
            .put("/auth/refresh/leandro")
        .then();
    }

    private void assertRefreshRejected(String authorization) {
        refreshRequest(authorization)
            .statusCode(403)
            .body("message", equalTo("Expired or Invalid JWT Token!"));
    }

    private String signin() {
        return given().spec(anonymous())
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(new AccountCredentialsDTO("leandro", "admin123"))
        .when()
            .post("/auth/signin")
        .then()
            .statusCode(200)
        .extract()
            .path("refreshToken");
    }

    @Test
    void anExpiredTokenIsRejectedLikeAMissingOne() {
        assertRejected(expiredToken());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        assertRejected(token("another-secret", new Date(System.currentTimeMillis() + 60_000)));
    }

    @Test
    void aMalformedTokenIsRejected() {
        assertRejected("not-a-jwt");
    }

    @Test
    void aValidTokenIsStillAccepted() {
        given().spec(authenticated())
        .when()
            .get("/api/person/v1")
        .then()
            .statusCode(200);
    }

    @Test
    void anInvalidTokenDoesNotBlockSignin() {
        given().spec(anonymous())
            .header(TestConfigs.HEADER_PARAM_AUTHORIZATION, "Bearer " + expiredToken())
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(new AccountCredentialsDTO("leandro", "admin123"))
        .when()
            .post("/auth/signin")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue());
    }

    @Test
    void anInvalidTokenDoesNotBlockThePublicDocumentation() {
        given().spec(anonymous())
            .header(TestConfigs.HEADER_PARAM_AUTHORIZATION, "Bearer " + expiredToken())
        .when()
            .get("/v3/api-docs")
        .then()
            .statusCode(200);
    }

    @Test
    void anExpiredRefreshTokenIsRejected() {
        assertRefreshRejected("Bearer " + expiredToken());
    }

    @Test
    void aMalformedRefreshTokenIsRejected() {
        assertRefreshRejected("Bearer not-a-jwt");
    }

    @Test
    void aRefreshTokenWithoutTheBearerPrefixIsRejected() {
        assertRefreshRejected(signin());
    }

    @Test
    void aValidRefreshTokenStillWorks() {
        refreshRequest("Bearer " + signin())
            .statusCode(200)
            .body("accessToken", notNullValue());
    }
}
