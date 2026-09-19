package br.com.erudio.integrationtests.controllers.file;

import br.com.erudio.integrationtests.AuthenticatedIntegrationTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileControllerTest extends AuthenticatedIntegrationTest {

    private static final String BASE = "/api/file/v1";

    private static String uniqueName(String extension) {
        return UUID.randomUUID() + extension;
    }

    private static byte[] upload(String name, byte[] content, String contentType) {
        given().spec(authenticated())
            .multiPart("file", name, content, contentType)
        .when()
            .post(BASE + "/uploadFile")
        .then()
            .statusCode(200);
        return content;
    }

    @Test
    void uploadStoresTheFileAndDescribesIt() {
        String name = uniqueName(".txt");
        byte[] content = "hello upload".getBytes(UTF_8);

        given().spec(authenticated())
            .multiPart("file", name, content, "text/plain")
        .when()
            .post(BASE + "/uploadFile")
        .then()
            .statusCode(200)
            .body("fileName", equalTo(name))
            .body("fileType", equalTo("text/plain"))
            .body("size", equalTo(content.length))
            .body("fileDownloadUri", endsWith(BASE + "/downloadFile/" + name));
    }

    @Test
    void uploadedFileCanBeDownloadedWithTheSameContent() {
        String name = uniqueName(".txt");
        byte[] content = upload(name, "Formação Spring Boot 2026".getBytes(UTF_8), "text/plain");

        byte[] downloaded = given().spec(authenticated())
        .when()
            .get(BASE + "/downloadFile/" + name)
        .then()
            .statusCode(200)
            .header("Content-Disposition", equalTo("attachment; filename=\"" + name + "\""))
            .contentType(startsWith("text/plain"))
        .extract()
            .asByteArray();

        assertArrayEquals(content, downloaded);
    }

    @Test
    void binaryFilesSurviveTheRoundTripByteForByte() {
        String name = uniqueName(".bin");
        byte[] content = new byte[2 * 1024 * 1024];
        new Random(42).nextBytes(content);
        upload(name, content, "application/octet-stream");

        byte[] downloaded = given().spec(authenticated())
        .when()
            .get(BASE + "/downloadFile/" + name)
        .then()
            .statusCode(200)
            .contentType("application/octet-stream")
        .extract()
            .asByteArray();

        assertArrayEquals(content, downloaded);
    }

    @Test
    void theContentTypeOfTheDownloadFollowsTheFileExtension() {
        String csv = uniqueName(".csv");
        upload(csv, "a,b\n1,2\n".getBytes(UTF_8), "text/csv");

        given().spec(authenticated())
        .when()
            .get(BASE + "/downloadFile/" + csv)
        .then()
            .statusCode(200)
            .contentType(startsWith("text/csv"));
    }

    @Test
    void uploadingAnExistingNameReplacesTheFile() {
        String name = uniqueName(".txt");
        upload(name, "first version".getBytes(UTF_8), "text/plain");
        byte[] second = upload(name, "second version".getBytes(UTF_8), "text/plain");

        byte[] downloaded = given().spec(authenticated())
        .when()
            .get(BASE + "/downloadFile/" + name)
        .then()
            .statusCode(200)
        .extract()
            .asByteArray();

        assertArrayEquals(second, downloaded);
    }

    @Test
    void uploadMultipleFilesStoresAllOfThemAndReturnsOneDescriptionEach() {
        String first = uniqueName(".txt");
        String second = uniqueName(".csv");

        given().spec(authenticated())
            .multiPart("files", first, "one".getBytes(UTF_8), "text/plain")
            .multiPart("files", second, "a,b\n".getBytes(UTF_8), "text/csv")
        .when()
            .post(BASE + "/uploadMultipleFiles")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("fileName", contains(first, second))
            .body("fileType", contains("text/plain", "text/csv"))
            .body("fileDownloadUri", contains(
                endsWith(BASE + "/downloadFile/" + first),
                endsWith(BASE + "/downloadFile/" + second)));

        for (String name : new String[]{first, second}) {
            given().spec(authenticated()).when().get(BASE + "/downloadFile/" + name).then().statusCode(200);
        }
    }

    @Test
    void downloadingAFileThatDoesNotExistIsNotFound() {
        String name = uniqueName(".txt");

        given().spec(authenticated())
        .when()
            .get(BASE + "/downloadFile/" + name)
        .then()
            .statusCode(404)
            .body("message", equalTo("File not found " + name));
    }

    @Test
    void aFileNameThatEscapesTheUploadDirectoryIsRejectedAndNothingIsWritten() {
        String tag = UUID.randomUUID().toString();
        String name = "../escaped-" + tag + ".txt";

        given().spec(authenticated())
            .multiPart("file", name, "boom".getBytes(UTF_8), "text/plain")
        .when()
            .post(BASE + "/uploadFile")
        .then()
            .statusCode(500)
            .body("message", equalTo("Could not store file " + name + ". Please try Again!"));

        assertFalse(Files.exists(Path.of("target", "escaped-" + tag + ".txt")));
    }

    @Test
    void uploadWithoutTheFilePartIsABadRequest() {
        given().spec(authenticated())
            .multiPart("somethingElse", uniqueName(".txt"), "x".getBytes(UTF_8), "text/plain")
        .when()
            .post(BASE + "/uploadFile")
        .then()
            .statusCode(400);
    }

    @Test
    void uploadAndDownloadRequireAuthentication() {
        given().spec(anonymous())
            .multiPart("file", uniqueName(".txt"), "x".getBytes(UTF_8), "text/plain")
        .when()
            .post(BASE + "/uploadFile")
        .then()
            .statusCode(403);

        given().spec(anonymous())
            .multiPart("files", uniqueName(".txt"), "x".getBytes(UTF_8), "text/plain")
        .when()
            .post(BASE + "/uploadMultipleFiles")
        .then()
            .statusCode(403);

        given().spec(anonymous())
        .when()
            .get(BASE + "/downloadFile/whatever.txt")
        .then()
            .statusCode(403);
    }
}
