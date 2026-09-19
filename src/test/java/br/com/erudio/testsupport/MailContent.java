package br.com.erudio.testsupport;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MailContent {

    private final List<String> htmlBodies = new ArrayList<>();
    private final Map<String, byte[]> attachments = new LinkedHashMap<>();

    private MailContent() {}

    public static MailContent of(MimeMessage message) throws Exception {
        MailContent content = new MailContent();
        content.collect(message);
        return content;
    }

    private void collect(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i));
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            try (InputStream content = part.getInputStream()) {
                attachments.put(part.getFileName(), content.readAllBytes());
            }
        } else if (part.isMimeType("text/html")) {
            htmlBodies.add((String) part.getContent());
        }
    }

    public String html() {
        return String.join("\n", htmlBodies);
    }

    public Map<String, byte[]> attachments() {
        return attachments;
    }
}
