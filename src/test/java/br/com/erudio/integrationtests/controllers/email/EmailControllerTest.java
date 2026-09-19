package br.com.erudio.integrationtests.controllers.email;

import br.com.erudio.integrationtests.AuthenticatedIntegrationTest;
import br.com.erudio.testsupport.MailContent;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The application talks to the in-process SMTP server started by {@link AbstractIntegrationTest},
 * so these tests never reach a real mail server.
 */
class EmailControllerTest extends AuthenticatedIntegrationTest {

    private static final String BASE = "/api/email/v1";
    private static final String ATTACHMENT_URL = BASE + "/withAttachment";
    private static final String SENDER = "sender@erudio.test";

    // the "email:" defaults of application.yml
    private static final String DEFAULT_SUBJECT = "Default Subject";
    private static final String DEFAULT_MESSAGE = "Default Message";

    @BeforeEach
    void emptyTheMailboxes() throws Exception {
        greenMail().purgeEmailFromAllMailboxes();
    }

    // ---------------------------------------------------------------- helpers

    private static void sendSimple(Map<String, Object> request) {
        given().spec(authenticated())
            .contentType("application/json")
            .body(request)
        .when()
            .post(BASE)
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(equalTo("e-Mail sent with success!"));
    }

    private static io.restassured.response.ValidatableResponse sendWithAttachment(String requestJson, String attachmentName, byte[] attachment) {
        return given().spec(authenticated())
            .multiPart("emailRequest", requestJson)
            .multiPart("attachment", attachmentName, attachment, "application/octet-stream")
        .when()
            .post(ATTACHMENT_URL)
        .then()
            .log().ifValidationFails();
    }

    private MimeMessage onlyMessage() {
        assertTrue(greenMail().waitForIncomingEmail(5000, 1), "the e-mail never arrived at the SMTP server");
        MimeMessage[] messages = greenMail().getReceivedMessages();
        assertEquals(1, messages.length);
        return messages[0];
    }

    private static List<String> recipientsOf(MimeMessage message) throws Exception {
        return Arrays.stream(message.getRecipients(Message.RecipientType.TO)).map(Address::toString).toList();
    }

    // ---------------------------------------------------------------- simple e-mail with the values of the request

    @Test
    void theSubjectAndTheBodyOfTheRequestArriveAsTheyWereSent() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test", "subject", "Welcome to the course", "body", "<h1>Hello Ada</h1><p>See you in class.</p>"));

        MimeMessage message = onlyMessage();
        assertEquals("Welcome to the course", message.getSubject());
        assertEquals(SENDER, message.getFrom()[0].toString());
        assertEquals(List.of("ada@erudio.test"), recipientsOf(message));
        assertEquals("<h1>Hello Ada</h1><p>See you in class.</p>", MailContent.of(message).html());
        assertTrue(MailContent.of(message).attachments().isEmpty());
    }

    @Test
    void accentsSurviveTheTripThroughTheMailServer() throws Exception {
        sendSimple(Map.of("to", "joao@erudio.test", "subject", "Formação Spring Boot 2026", "body", "<p>Olá, João! Até a próxima aula.</p>"));

        MimeMessage message = onlyMessage();
        assertEquals("Formação Spring Boot 2026", message.getSubject());
        assertEquals("<p>Olá, João! Até a próxima aula.</p>", MailContent.of(message).html());
    }

    @Test
    void severalRecipientsSeparatedBySemicolonAllReceiveTheMessage() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test; bob@erudio.test ;carol@erudio.test", "subject", "Team", "body", "Hi all"));

        assertTrue(greenMail().waitForIncomingEmail(5000, 3));
        MimeMessage[] messages = greenMail().getReceivedMessages();
        assertEquals(3, messages.length);
        List<String> everyone = List.of("ada@erudio.test", "bob@erudio.test", "carol@erudio.test");
        for (MimeMessage message : messages) {
            assertEquals(everyone, recipientsOf(message), "each copy names all the recipients");
            assertEquals("Team", message.getSubject());
        }
    }

    // ---------------------------------------------------------------- simple e-mail: the defaults are only a fallback

    @Test
    void theDefaultsAreUsedOnlyForTheFieldsTheRequestLeavesOut() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test"));

        MimeMessage message = onlyMessage();
        assertEquals(DEFAULT_SUBJECT, message.getSubject());
        assertEquals(DEFAULT_MESSAGE, MailContent.of(message).html());
    }

    @Test
    void onlyTheSubjectInformedKeepsTheSubjectAndDefaultsTheMessage() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test", "subject", "My own subject"));

        MimeMessage message = onlyMessage();
        assertEquals("My own subject", message.getSubject());
        assertEquals(DEFAULT_MESSAGE, MailContent.of(message).html());
    }

    @Test
    void onlyTheBodyInformedKeepsTheBodyAndDefaultsTheSubject() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test", "body", "My own body"));

        MimeMessage message = onlyMessage();
        assertEquals(DEFAULT_SUBJECT, message.getSubject());
        assertEquals("My own body", MailContent.of(message).html());
    }

    @Test
    void blankValuesCountAsNotInformed() throws Exception {
        sendSimple(Map.of("to", "ada@erudio.test", "subject", "   ", "body", ""));

        MimeMessage message = onlyMessage();
        assertEquals(DEFAULT_SUBJECT, message.getSubject());
        assertEquals(DEFAULT_MESSAGE, MailContent.of(message).html());
    }

    // ---------------------------------------------------------------- simple e-mail: errors

    @Test
    void anInvalidRecipientFailsAndNothingIsDelivered() {
        given().spec(authenticated())
            .contentType("application/json")
            .body(Map.of("to", "not-an-address@@", "subject", "x", "body", "y"))
        .when()
            .post(BASE)
        .then()
            .statusCode(500);

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    @Test
    void aMalformedJsonBodyIsABadRequest() {
        given().spec(authenticated())
            .contentType("application/json")
            .body("{not json")
        .when()
            .post(BASE)
        .then()
            .statusCode(400);

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    // ---------------------------------------------------------------- e-mail with attachment

    @Test
    void theAttachmentArrivesWithTheSubjectAndBodyOfTheRequest() throws Exception {
        byte[] report = "id,name\n1,Ada\n2,Alan\n".getBytes(UTF_8);

        sendWithAttachment(
            "{\"to\":\"ada@erudio.test\",\"subject\":\"Monthly report\",\"body\":\"<p>The report is attached.</p>\"}",
            "report.csv", report)
            .statusCode(200)
            .body(equalTo("e-Mail with attachment sent successfully!"));

        MimeMessage message = onlyMessage();
        assertEquals("Monthly report", message.getSubject());
        assertEquals(List.of("ada@erudio.test"), recipientsOf(message));
        MailContent content = MailContent.of(message);
        assertEquals("<p>The report is attached.</p>", content.html());
        assertEquals(List.of("report.csv"), List.copyOf(content.attachments().keySet()));
        // a text attachment is sent as text, and SMTP turns its line breaks into CRLF (binary files are exact, see below)
        assertEquals(
            new String(report, UTF_8),
            new String(content.attachments().get("report.csv"), UTF_8).replace("\r\n", "\n"));
    }

    @Test
    void aBinaryAttachmentSurvivesByteForByte() throws Exception {
        byte[] binary = new byte[300 * 1024];
        new Random(7).nextBytes(binary);

        sendWithAttachment("{\"to\":\"ada@erudio.test\",\"subject\":\"Binary\",\"body\":\"see file\"}", "data.bin", binary)
            .statusCode(200);

        MailContent content = MailContent.of(onlyMessage());
        assertArrayEquals(binary, content.attachments().get("data.bin"));
    }

    @Test
    void theAttachmentEmailFallsBackToTheDefaultsWhenTheRequestHasNoSubjectOrBody() throws Exception {
        sendWithAttachment("{\"to\":\"ada@erudio.test\"}", "notes.txt", "notes".getBytes(UTF_8))
            .statusCode(200);

        MimeMessage message = onlyMessage();
        assertEquals(DEFAULT_SUBJECT, message.getSubject());
        MailContent content = MailContent.of(message);
        assertEquals(DEFAULT_MESSAGE, content.html());
        assertArrayEquals("notes".getBytes(UTF_8), content.attachments().get("notes.txt"));
    }

    @Test
    void theAttachmentEmailKeepsAnySubjectTheCallerSets() throws Exception {
        sendWithAttachment("{\"to\":\"ada@erudio.test\",\"subject\":\"Only my subject\"}", "notes.txt", "n".getBytes(UTF_8))
            .statusCode(200);

        MimeMessage message = onlyMessage();
        assertEquals("Only my subject", message.getSubject());
        assertEquals(DEFAULT_MESSAGE, MailContent.of(message).html());
    }

    // ---------------------------------------------------------------- e-mail with attachment: errors

    @Test
    void anInvalidRequestJsonIsRejectedAndNothingIsDelivered() {
        sendWithAttachment("{not json", "notes.txt", "n".getBytes(UTF_8))
            .statusCode(500)
            .body("message", equalTo("Error parsing email request JSON!"));

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    @Test
    void unknownFieldsInTheRequestJsonAreRejected() {
        sendWithAttachment("{\"to\":\"ada@erudio.test\",\"cc\":\"bob@erudio.test\"}", "notes.txt", "n".getBytes(UTF_8))
            .statusCode(500)
            .body("message", equalTo("Error parsing email request JSON!"));

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    @Test
    void anInvalidRecipientIsRejectedAndNothingIsDelivered() {
        sendWithAttachment("{\"to\":\"not-an-address@@\",\"subject\":\"x\"}", "notes.txt", "n".getBytes(UTF_8))
            .statusCode(500);

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    @Test
    void theAttachmentPartIsRequired() {
        given().spec(authenticated())
            .multiPart("emailRequest", "{\"to\":\"ada@erudio.test\"}")
        .when()
            .post(ATTACHMENT_URL)
        .then()
            .statusCode(400);

        assertEquals(0, greenMail().getReceivedMessages().length);
    }

    @Test
    void theRequestPartIsRequired() {
        given().spec(authenticated())
            .multiPart("attachment", "notes.txt", "n".getBytes(UTF_8), "text/plain")
        .when()
            .post(ATTACHMENT_URL)
        .then()
            .statusCode(400);
    }

    @Test
    void theAttachmentEndpointOnlyAcceptsMultipart() {
        given().spec(authenticated())
            .contentType("application/json")
            .body("{\"to\":\"ada@erudio.test\"}")
        .when()
            .post(ATTACHMENT_URL)
        .then()
            .statusCode(415);
    }

    // ---------------------------------------------------------------- security

    @Test
    void bothEndpointsRequireAuthentication() {
        given().spec(anonymous())
            .contentType("application/json")
            .body(Map.of("to", "ada@erudio.test"))
        .when()
            .post(BASE)
        .then()
            .statusCode(403);

        given().spec(anonymous())
            .multiPart("emailRequest", "{\"to\":\"ada@erudio.test\"}")
            .multiPart("attachment", "notes.txt", "n".getBytes(UTF_8), "text/plain")
        .when()
            .post(ATTACHMENT_URL)
        .then()
            .statusCode(403);

        assertEquals(0, greenMail().getReceivedMessages().length);
    }
}
