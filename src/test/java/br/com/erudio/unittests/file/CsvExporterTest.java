package br.com.erudio.unittests.file;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.exporter.impl.CsvExporter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {

    private static final List<String> HEADER = List.of("ID", "First Name", "Last Name", "Address", "Gender", "Enabled");

    private final CsvExporter exporter = new CsvExporter();

    private static PersonDTO person(Long id, String firstName, String lastName, String address, String gender, Boolean enabled) {
        PersonDTO person = new PersonDTO();
        person.setId(id);
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAddress(address);
        person.setGender(gender);
        person.setEnabled(enabled);
        return person;
    }

    private record ParsedCsv(List<String> header, List<CSVRecord> records) {}

    private static ParsedCsv parse(Resource resource) throws IOException {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            return new ParsedCsv(parser.getHeaderNames(), parser.getRecords());
        }
    }

    @Test
    void writesTheHeaderAndOneLinePerPerson() throws Exception {
        ParsedCsv csv = parse(exporter.exportPeople(List.of(
            person(1L, "Ayrton", "Senna", "São Paulo - Brasil", "Male", true),
            person(2L, "Leonardo", "da Vinci", "Vinci - Italy", "Male", false))));

        assertEquals(HEADER, csv.header());
        assertEquals(2, csv.records().size());
        assertEquals(List.of("1", "Ayrton", "Senna", "São Paulo - Brasil", "Male", "true"), csv.records().get(0).toList());
        assertEquals(List.of("2", "Leonardo", "da Vinci", "Vinci - Italy", "Male", "false"), csv.records().get(1).toList());
    }

    @Test
    void writesOnlyTheHeaderWhenThereIsNobody() throws Exception {
        ParsedCsv csv = parse(exporter.exportPeople(List.of()));

        assertEquals(HEADER, csv.header());
        assertTrue(csv.records().isEmpty());
    }

    @Test
    void keepsCommasAndQuotesInsideAValue() throws Exception {
        String address = "Rua \"A\", 10 - apto 2";

        ParsedCsv csv = parse(exporter.exportPeople(List.of(person(7L, "Ada", "Lovelace", address, "Female", true))));

        assertEquals(1, csv.records().size());
        assertEquals(address, csv.records().get(0).get("Address"));
        assertEquals(6, csv.records().get(0).size());
    }

    @Test
    void encodesTheFileAsUtf8() throws Exception {
        Resource resource = exporter.exportPeople(List.of(person(1L, "João", "Conceição", "Avenida São João", "Male", true)));

        assertTrue(new String(resource.getContentAsByteArray(), UTF_8).contains("João,Conceição,Avenida São João"));
    }

    @Test
    void writesMissingValuesAsEmptyFields() throws Exception {
        ParsedCsv csv = parse(exporter.exportPeople(List.of(person(3L, "Alan", "Turing", null, null, null))));

        CSVRecord record = csv.records().get(0);
        assertEquals("", record.get("Address"));
        assertEquals("", record.get("Gender"));
        assertEquals("", record.get("Enabled"));
    }

    @Test
    void exportingASinglePersonIsNotSupported() throws Exception {
        assertNull(exporter.exportPerson(person(1L, "Ayrton", "Senna", "x", "Male", true)));
    }
}
