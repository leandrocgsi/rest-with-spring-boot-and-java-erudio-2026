package br.com.erudio.unittests.file;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.exporter.impl.XlsxExporter;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XlsxExporterTest {

    private static final List<String> HEADER = List.of("ID", "First Name", "Last Name", "Address", "Gender", "Enabled");

    private final XlsxExporter exporter = new XlsxExporter();
    private final DataFormatter formatter = new DataFormatter();

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

    private List<String> rowValues(Row row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < HEADER.size(); i++) {
            Cell cell = row.getCell(i);
            values.add(cell == null ? null : formatter.formatCellValue(cell));
        }
        return values;
    }

    private Sheet firstSheet(Workbook workbook) {
        return workbook.getSheet("People");
    }

    @Test
    void writesASheetNamedPeopleWithTheHeaderAndOneRowPerPerson() throws Exception {
        Resource resource = exporter.exportPeople(List.of(
            person(1L, "Ayrton", "Senna", "São Paulo - Brasil", "Male", true),
            person(2L, "Marie", "Curie", "Warsaw - Poland", "Female", false)));

        try (InputStream in = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = firstSheet(workbook);
            assertNotNull(sheet, "the sheet must be named People");

            assertEquals(2, sheet.getLastRowNum());
            assertEquals(HEADER, rowValues(sheet.getRow(0)));
            assertEquals(List.of("1", "Ayrton", "Senna", "São Paulo - Brasil", "Male", "Yes"), rowValues(sheet.getRow(1)));
            assertEquals(List.of("2", "Marie", "Curie", "Warsaw - Poland", "Female", "No"), rowValues(sheet.getRow(2)));
        }
    }

    @Test
    void writesTheIdAsANumber() throws Exception {
        Resource resource = exporter.exportPeople(List.of(person(42L, "Ada", "Lovelace", "London", "Female", true)));

        try (InputStream in = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Cell id = firstSheet(workbook).getRow(1).getCell(0);

            assertEquals(CellType.NUMERIC, id.getCellType());
            assertEquals(42.0, id.getNumericCellValue());
        }
    }

    @Test
    void writesNoWhenTheEnabledFlagIsMissing() throws Exception {
        Resource resource = exporter.exportPeople(List.of(person(3L, "Alan", "Turing", "Wilmslow", "Male", null)));

        try (InputStream in = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            assertEquals("No", formatter.formatCellValue(firstSheet(workbook).getRow(1).getCell(5)));
        }
    }

    @Test
    void writesTheHeaderInBold() throws Exception {
        Resource resource = exporter.exportPeople(List.of());

        try (InputStream in = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Cell header = firstSheet(workbook).getRow(0).getCell(0);
            Font font = workbook.getFontAt(header.getCellStyle().getFontIndex());

            assertTrue(font.getBold());
        }
    }

    @Test
    void writesOnlyTheHeaderWhenThereIsNobody() throws Exception {
        Resource resource = exporter.exportPeople(List.of());

        try (InputStream in = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = firstSheet(workbook);

            assertEquals(0, sheet.getLastRowNum());
            assertEquals(HEADER, rowValues(sheet.getRow(0)));
        }
    }

    @Test
    void exportingASinglePersonIsNotSupported() throws Exception {
        assertNull(exporter.exportPerson(person(1L, "Ayrton", "Senna", "x", "Male", true)));
    }
}
