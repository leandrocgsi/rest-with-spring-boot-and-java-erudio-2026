package br.com.erudio.unittests.file;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.importer.impl.XlsxImporter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class XlsxImporterTest {

    private final XlsxImporter importer = new XlsxImporter();

    private static InputStream workbook(Consumer<Sheet> fill) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(workbook.createSheet("People"));
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static void header(Sheet sheet) {
        row(sheet, 0, "first_name", "last_name", "address", "gender");
    }

    private static void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    @Test
    void importsOnePersonPerRowSkippingTheHeader() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            row(sheet, 1, "Ada", "Lovelace", "London", "Female");
            row(sheet, 2, "Alan", "Turing", "Wilmslow", "Male");
        }));

        assertEquals(2, people.size());
        PersonDTO ada = people.get(0);
        assertEquals("Ada", ada.getFirstName());
        assertEquals("Lovelace", ada.getLastName());
        assertEquals("London", ada.getAddress());
        assertEquals("Female", ada.getGender());
        assertEquals("Alan", people.get(1).getFirstName());
    }

    @Test
    void importedPeopleAreEnabledAndHaveNoIdYet() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            row(sheet, 1, "Ada", "Lovelace", "London", "Female");
        }));

        assertTrue(people.get(0).getEnabled());
        assertNull(people.get(0).getId());
    }

    @Test
    void theFirstRowIsAlwaysTheHeaderWhateverItContains() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            row(sheet, 0, "Grace", "Hopper", "New York", "Female");
            row(sheet, 1, "Ada", "Lovelace", "London", "Female");
        }));

        assertEquals(1, people.size());
        assertEquals("Ada", people.get(0).getFirstName());
    }

    @Test
    void skipsRowsWhoseFirstCellIsBlank() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            row(sheet, 1, "Ada", "Lovelace", "London", "Female");
            Row blank = sheet.createRow(2);
            blank.createCell(0, CellType.BLANK);
            blank.createCell(1).setCellValue("Ignored");
            row(sheet, 3, "Alan", "Turing", "Wilmslow", "Male");
        }));

        assertEquals(List.of("Ada", "Alan"), people.stream().map(PersonDTO::getFirstName).toList());
    }

    @Test
    void skipsRowsThatDoNotExist() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            row(sheet, 1, "Ada", "Lovelace", "London", "Female");
            row(sheet, 5, "Alan", "Turing", "Wilmslow", "Male");
        }));

        assertEquals(2, people.size());
    }

    @Test
    void readsUtf8Accents() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            row(sheet, 1, "João", "Conceição", "Avenida São João", "Male");
        }));

        assertEquals("João", people.get(0).getFirstName());
        assertEquals("Conceição", people.get(0).getLastName());
        assertEquals("Avenida São João", people.get(0).getAddress());
    }

    @Test
    void importsManyRows() throws Exception {
        List<PersonDTO> people = importer.importFile(workbook(sheet -> {
            header(sheet);
            for (int i = 1; i <= 250; i++) {
                row(sheet, i, "Name" + i, "Last" + i, "Address " + i, "Male");
            }
        }));

        assertEquals(250, people.size());
        assertEquals("Name250", people.get(249).getFirstName());
    }

    @Test
    void importsNothingFromAHeaderOnlySheet() throws Exception {
        assertTrue(importer.importFile(workbook(XlsxImporterTest::header)).isEmpty());
    }

    @Test
    void importsNothingFromAnEmptySheet() throws Exception {
        assertTrue(importer.importFile(workbook(sheet -> {})).isEmpty());
    }

    @Test
    void failsWhenANameCellIsNotText() {
        assertThrows(IllegalStateException.class, () -> importer.importFile(workbook(sheet -> {
            header(sheet);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(12345);
            row.createCell(1).setCellValue("Lovelace");
            row.createCell(2).setCellValue("London");
            row.createCell(3).setCellValue("Female");
        })));
    }

    @Test
    void failsWhenTheContentIsNotAnXlsxFile() {
        assertThrows(Exception.class, () -> importer.importFile(new ByteArrayInputStream("first_name,last_name\nAda,Lovelace\n".getBytes())));
    }
}
