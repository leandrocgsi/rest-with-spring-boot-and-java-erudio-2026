package br.com.erudio.services;

import br.com.erudio.config.EmailConfig;
import br.com.erudio.data.dto.request.EmailRequestDTO;
import br.com.erudio.mail.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;

@Service
public class EmailService {

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private EmailConfig emailConfigs;

    public void sendSimpleEmail(EmailRequestDTO emailRequest) {
        emailSender
            .to(emailRequest.getTo())
            .withSubject(emailRequest.getSubject())
            .withMessage(emailRequest.getSubject())
            .send(emailConfigs);
    }

    public void setEmailWithAttachment(String emailRequestJson, MultipartFile attachment) {
        File tempFile = null;
        try {
            EmailRequestDTO emailRequest = JsonMapper.builder().build().readValue(emailRequestJson, EmailRequestDTO.class);
            tempFile = File.createTempFile("attachment", attachment.getOriginalFilename());
            attachment.transferTo(tempFile);

            emailSender
                .to(emailRequest.getTo())
                .withSubject(emailRequest.getSubject())
                .withMessage(emailRequest.getSubject())
                .attach(tempFile.getAbsolutePath())
                .send(emailConfigs);

        } catch (JacksonException e) {
            throw new RuntimeException("Error parsing email request JSON!", e);
        } catch (IOException e) {
            throw new RuntimeException("Error processing the attachment!", e);
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }

    }
}
