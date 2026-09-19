package br.com.erudio.unittests.services;

import br.com.erudio.config.EmailConfig;
import br.com.erudio.config.EmailDefaultsConfig;
import br.com.erudio.data.dto.request.EmailRequestDTO;
import br.com.erudio.mail.EmailSender;
import br.com.erudio.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String DEFAULT_SUBJECT = "Default Subject";
    private static final String DEFAULT_MESSAGE = "Default Message";

    private EmailSender emailSender;
    private EmailConfig emailConfig;
    private EmailService service;

    @BeforeEach
    void setUp() {
        emailSender = mock(EmailSender.class, RETURNS_SELF);
        emailConfig = new EmailConfig();

        EmailDefaultsConfig defaults = new EmailDefaultsConfig();
        defaults.setSubject(DEFAULT_SUBJECT);
        defaults.setMessage(DEFAULT_MESSAGE);

        service = new EmailService();
        ReflectionTestUtils.setField(service, "emailSender", emailSender);
        ReflectionTestUtils.setField(service, "emailConfigs", emailConfig);
        ReflectionTestUtils.setField(service, "emailDefaults", defaults);
    }

    private static EmailRequestDTO request(String to, String subject, String body) {
        EmailRequestDTO request = new EmailRequestDTO();
        request.setTo(to);
        request.setSubject(subject);
        request.setBody(body);
        return request;
    }

    private static MultipartFile attachment(String name, String content) {
        return new MockMultipartFile("attachment", name, "text/plain", content.getBytes(UTF_8));
    }

    // ---------------------------------------------------------------- simple e-mail

    @Test
    void sendSimpleEmailUsesTheSubjectAndBodyOfTheRequest() {
        service.sendSimpleEmail(request("ada@erudio.test", "Welcome", "<p>Hello Ada</p>"));

        verify(emailSender).to("ada@erudio.test");
        verify(emailSender).withSubject("Welcome");
        verify(emailSender).withMessage("<p>Hello Ada</p>");
        verify(emailSender).send(emailConfig);
        verify(emailSender, never()).attach(any());
        verify(emailSender, never()).attach(any(), any());
    }

    @Test
    void sendSimpleEmailSendsTheBodyAsTheMessageNotTheSubject() {
        service.sendSimpleEmail(request("ada@erudio.test", "Just the subject", "The real body"));

        verify(emailSender).withMessage("The real body");
        verify(emailSender, never()).withMessage("Just the subject");
    }

    @Test
    void sendSimpleEmailKeepsAnySubjectAndBodyTheCallerSets() {
        service.sendSimpleEmail(request("ada@erudio.test", DEFAULT_SUBJECT + " (custom)", DEFAULT_MESSAGE + " (custom)"));

        verify(emailSender).withSubject(DEFAULT_SUBJECT + " (custom)");
        verify(emailSender).withMessage(DEFAULT_MESSAGE + " (custom)");
    }

    @Test
    void sendSimpleEmailFallsBackToTheDefaultsOnlyWhenNothingWasInformed() {
        service.sendSimpleEmail(request("ada@erudio.test", null, null));

        verify(emailSender).withSubject(DEFAULT_SUBJECT);
        verify(emailSender).withMessage(DEFAULT_MESSAGE);
        verify(emailSender).send(emailConfig);
    }

    @Test
    void sendSimpleEmailTreatsBlankValuesAsMissing() {
        service.sendSimpleEmail(request("ada@erudio.test", "   ", ""));

        verify(emailSender).withSubject(DEFAULT_SUBJECT);
        verify(emailSender).withMessage(DEFAULT_MESSAGE);
    }

    @Test
    void sendSimpleEmailDefaultsEachFieldIndependently() {
        service.sendSimpleEmail(request("ada@erudio.test", "Only the subject", null));
        verify(emailSender).withSubject("Only the subject");
        verify(emailSender).withMessage(DEFAULT_MESSAGE);

        reset(emailSender);

        service.sendSimpleEmail(request("ada@erudio.test", null, "Only the body"));
        verify(emailSender).withSubject(DEFAULT_SUBJECT);
        verify(emailSender).withMessage("Only the body");
    }

    // ---------------------------------------------------------------- e-mail with attachment

    @Test
    void sendEmailWithAttachmentUsesTheValuesOfTheRequestAndAttachesTheFile() {
        AtomicReference<Path> attachedPath = new AtomicReference<>();
        AtomicReference<String> attachedContent = new AtomicReference<>();
        AtomicReference<String> attachedName = new AtomicReference<>();
        when(emailSender.attach(anyString(), anyString())).thenAnswer(invocation -> {
            Path path = Path.of((String) invocation.getArgument(0));
            attachedPath.set(path);
            attachedName.set(invocation.getArgument(1));
            attachedContent.set(Files.readString(path));
            return emailSender;
        });

        service.setEmailWithAttachment(
            "{\"to\":\"ada@erudio.test\",\"subject\":\"Report\",\"body\":\"See the file\"}",
            attachment("report.txt", "the report"));

        verify(emailSender).to("ada@erudio.test");
        verify(emailSender).withSubject("Report");
        verify(emailSender).withMessage("See the file");
        verify(emailSender).send(emailConfig);

        assertEquals("the report", attachedContent.get(), "the file must exist with its content while the e-mail is sent");
        assertEquals("report.txt", attachedName.get(), "the recipient must see the name of the uploaded file, not the temporary one");
        assertNotEquals("report.txt", attachedPath.get().getFileName().toString());
        assertFalse(Files.exists(attachedPath.get()), "the temporary copy must be deleted afterwards");
    }

    @Test
    void sendEmailWithAttachmentFallsBackToTheDefaultsWhenTheRequestHasNoSubjectOrBody() {
        service.setEmailWithAttachment("{\"to\":\"ada@erudio.test\"}", attachment("a.txt", "x"));

        verify(emailSender).withSubject(DEFAULT_SUBJECT);
        verify(emailSender).withMessage(DEFAULT_MESSAGE);
        verify(emailSender).send(emailConfig);
    }

    @Test
    void sendEmailWithAttachmentRejectsAnInvalidJson() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.setEmailWithAttachment("{not json", attachment("a.txt", "x")));

        assertEquals("Error parsing email request JSON!", exception.getMessage());
        verifyNoInteractions(emailSender);
    }

    @Test
    void sendEmailWithAttachmentRejectsUnknownFieldsInTheJson() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.setEmailWithAttachment("{\"to\":\"ada@erudio.test\",\"cc\":\"bob@erudio.test\"}", attachment("a.txt", "x")));

        assertEquals("Error parsing email request JSON!", exception.getMessage());
        verifyNoInteractions(emailSender);
    }

    @Test
    void sendEmailWithAttachmentReportsAFailureReadingTheAttachment() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.txt");
        doThrow(new IOException("disk full")).when(broken).transferTo(any(File.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.setEmailWithAttachment("{\"to\":\"ada@erudio.test\"}", broken));

        assertEquals("Error processing the attachment!", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
        verify(emailSender, never()).send(any());
    }

    @Test
    void sendEmailWithAttachmentDeletesTheTemporaryFileEvenWhenTheSendFails() {
        AtomicReference<Path> attachedPath = new AtomicReference<>();
        when(emailSender.attach(anyString(), anyString())).thenAnswer(invocation -> {
            attachedPath.set(Path.of((String) invocation.getArgument(0)));
            return emailSender;
        });
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(any());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> service.setEmailWithAttachment("{\"to\":\"ada@erudio.test\"}", attachment("a.txt", "x")));

        assertEquals("smtp down", exception.getMessage());
        assertNotNull(attachedPath.get());
        assertFalse(Files.exists(attachedPath.get()));
    }
}
