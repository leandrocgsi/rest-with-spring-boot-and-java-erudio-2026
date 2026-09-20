package br.com.erudio.file.exporter.impl;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.exporter.contract.PersonExporter;
import br.com.erudio.services.QRCodeService;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PdfExporter implements PersonExporter {

    @Autowired
    private QRCodeService service;

    private final Map<String, JasperReport> reports = new ConcurrentHashMap<>();

    private JasperReport report(String template) throws Exception {
        JasperReport cached = reports.get(template);
        if (cached != null) {
            return cached;
        }
        String path = "/templates/" + template;
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new RuntimeException("Template file not found: " + path);
            }
            JasperReport compiled = JasperCompileManager.compileReport(stream);
            reports.put(template, compiled);
            return compiled;
        }
    }

    @Override
    public Resource exportPeople(List<PersonDTO> people) throws Exception {
        JasperReport jasperReport = report("people.jrxml");

        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(people);
        Map<String, Object> parameters = new HashMap<>();
        // parameters.put("title", "People Report");

        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }

    @Override
    public Resource exportPerson(PersonDTO person) throws Exception {
        JasperReport mainReport = report("person.jrxml");
        JasperReport subReport = report("books.jrxml");

        InputStream qrCodeStream = service.generateQRCode(person.getProfileUrl(), 200, 200);

        JRBeanCollectionDataSource subReportDataSource = new JRBeanCollectionDataSource(person.getBooks());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("SUB_REPORT_DATA_SOURCE", subReportDataSource);
        parameters.put("BOOK_SUB_REPORT", subReport);
        parameters.put("QR_CODEIMAGE", qrCodeStream);

        JRBeanCollectionDataSource mainDataSource = new JRBeanCollectionDataSource(Collections.singletonList(person));

        JasperPrint jasperPrint = JasperFillManager.fillReport(mainReport, parameters, mainDataSource);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }
}