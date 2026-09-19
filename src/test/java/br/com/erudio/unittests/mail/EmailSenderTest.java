package br.com.erudio.unittests.mail;

import br.com.erudio.config.EmailConfig;
import br.com.erudio.mail.EmailSender;
import br.com.erudio.testsupport.MailContent;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    private static final String SENDER = "sender@erudio.test";

    @Mock
    private JavaMailSender mailSender;

    private EmailConfig config;
    private EmailSender sender;

    @BeforeEach
    void setUp() {
        config = new EmailConfig();
        config.setUsername(SENDER);
        sender = new EmailSender(mailSender);

        Session session = Session.getInstance(new Properties());
        lenient().when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(session));
    }

    private MimeMessage sentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private static List<String> addressesOf(Address[] addresses) {
        return Arrays.stream(addresses).map(Address::toString).toList();
    }

    @Test
    void sendsAnHtmlMessageFromTheConfiguredAccount() throws Exception {
        sender.to("ada@erudio.test").withSubject("Welcome").withMessage("<h1>Hello Ada</h1>").send(config);

        MimeMessage message = sentMessage();
        assertEquals("Welcome", message.getSubject());
        assertEquals(List.of(SENDER), addressesOf(message.getFrom()));
        assertEquals(List.of("ada@erudio.test"), addressesOf(message.getRecipients(Message.RecipientType.TO)));
        assertEquals("<h1>Hello Ada</h1>", MailContent.of(message).html());
    }

    @Test
    void sendsToSeveralRecipientsSeparatedBySemicolonIgnoringSpaces() throws Exception {
        sender.to("ada@erudio.test; bob@erudio.test ;carol@erudio.test")
            .withSubject("Team")
            .withMessage("Hi all")
            .send(config);

        assertEquals(
            List.of("ada@erudio.test", "bob@erudio.test", "carol@erudio.test"),
            addressesOf(sentMessage().getRecipients(Message.RecipientType.TO)));
    }

    @Test
    void attachesTheGivenFile(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("report.csv"), "id,name\n1,Ada\n", UTF_8);

        sender.to("ada@erudio.test").withSubject("Report").withMessage("See attachment")
            .attach(file.toString())
            .send(config);

        MailContent content = MailContent.of(sentMessage());
        assertEquals("See attachment", content.html());
        assertEquals(1, content.attachments().size());
        assertEquals("id,name\n1,Ada\n", new String(content.attachments().get("report.csv"), UTF_8));
    }

    @Test
    void theRecipientSeesTheGivenAttachmentNameInsteadOfTheNameOfTheFile(@TempDir Path tempDir) throws Exception {
        Path temporary = Files.writeString(tempDir.resolve("attachment8291746352report.csv"), "id\n1\n", UTF_8);

        sender.to("ada@erudio.test").withSubject("Report").withMessage("See attachment")
            .attach(temporary.toString(), "Monthly report.csv")
            .send(config);

        MailContent content = MailContent.of(sentMessage());
        assertEquals(List.of("Monthly report.csv"), List.copyOf(content.attachments().keySet()));
        assertEquals("id\n1\n", new String(content.attachments().get("Monthly report.csv"), UTF_8));
    }

    @Test
    void aBlankAttachmentNameFallsBackToTheNameOfTheFile(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("data.txt"), "x", UTF_8);

        sender.to("ada@erudio.test").withSubject("Data").withMessage("See attachment")
            .attach(file.toString(), "  ")
            .send(config);

        assertEquals(List.of("data.txt"), List.copyOf(MailContent.of(sentMessage()).attachments().keySet()));
    }

    @Test
    void sendsNoAttachmentWhenNoneWasGiven() throws Exception {
        sender.to("ada@erudio.test").withSubject("Plain").withMessage("No files").send(config);

        assertTrue(MailContent.of(sentMessage()).attachments().isEmpty());
    }

    @Test
    void keepsTheAccentsOfTheSubjectAndTheBody() throws Exception {
        sender.to("ada@erudio.test").withSubject("Formação Spring Boot").withMessage("<p>Olá, João!</p>").send(config);

        MimeMessage message = sentMessage();
        assertEquals("Formação Spring Boot", message.getSubject());
        assertEquals("<p>Olá, João!</p>", MailContent.of(message).html());
    }

    @Test
    void canBeReusedForAnotherMessageAfterASend() throws Exception {
        sender.to("ada@erudio.test").withSubject("First").withMessage("1").send(config);
        sender.to("bob@erudio.test").withSubject("Second").withMessage("2").send(config);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        assertEquals("First", captor.getAllValues().get(0).getSubject());
        assertEquals("Second", captor.getAllValues().get(1).getSubject());
    }

    @Test
    void rejectsAnInvalidRecipientAddress() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> sender.to("not-an-address@@"));

        assertInstanceOf(AddressException.class, exception.getCause());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void propagatesAFailureOfTheMailServer() {
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(MimeMessage.class));

        MailSendException exception = assertThrows(MailSendException.class,
            () -> sender.to("ada@erudio.test").withSubject("Down").withMessage("x").send(config));

        assertEquals("connection refused", exception.getMessage());
    }
}
