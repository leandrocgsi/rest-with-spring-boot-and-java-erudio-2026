package br.com.erudio.integrationtests.controllers.report;

import br.com.erudio.file.exporter.MediaTypes;
import br.com.erudio.integrationtests.AuthenticatedIntegrationTest;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import io.restassured.path.json.JsonPath;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.erudio.testsupport.NetworkAssumptions.assumeReportImagesAreReachable;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reports (CSV, XLSX and PDF exports) and the file import (mass creation) of the person endpoints.
 */
class PersonControllerFilesTest extends AuthenticatedIntegrationTest {

    private static final String BASE = "/api/person/v1";
    private static final String XLSX = MediaTypes.APPLICATION_XLSX_VALUE;
    private static final String CSV = MediaTypes.APPLICATION_CSV_VALUE;
    private static final String PDF = MediaTypes.APPLICATION_PDF_VALUE;
    private static final List<String> EXPORT_HEADER = List.of("ID", "First Name", "Last Name", "Address", "Gender", "Enabled");

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void deleteWhatTheTestImported() {
        createdIds.forEach(id -> given().spec(authenticated()).delete(BASE + "/" + id).then().statusCode(204));
        createdIds.clear();
    }

    // ---------------------------------------------------------------- helpers

    /** The same page as the JSON listing, which is the reference for what an export must contain. */
    private static List<Integer> idsOfThePage(int page, int size, String direction) {
        return JsonPath.from(given().spec(authenticated())
            .queryParam("page", page).queryParam("size", size).queryParam("direction", direction)
            .accept("application/json")
        .when()
            .get(BASE)
        .then()
            .statusCode(200)
        .extract().asString()).getList("_embedded.people.id");
    }

    private static List<String> firstNamesOfThePage(int page, int size, String direction) {
        return JsonPath.from(given().spec(authenticated())
            .queryParam("page", page).queryParam("size", size).queryParam("direction", direction)
            .accept("application/json")
        .when()
            .get(BASE)
        .then()
            .statusCode(200)
        .extract().asString()).getList("_embedded.people.firstName");
    }

    private static byte[] export(String accept, int page, int size, String direction) {
        return given().spec(authenticated())
            .header("Accept", accept)
            .queryParam("page", page).queryParam("size", size).queryParam("direction", direction)
        .when()
            .get(BASE + "/exportPage")
        .then()
            .statusCode(200)
        .extract().asByteArray();
    }

    private static List<CSVRecord> parseCsv(byte[] csv, List<String> expectedHeader) throws Exception {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csv), UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            assertEquals(expectedHeader, parser.getHeaderNames());
            return parser.getRecords();
        }
    }

    private static String pdfText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private static String tag() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static byte[] xlsx(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("People");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Sends the file, remembers the created people so they are deleted after the test, and returns the response. */
    private static io.restassured.response.ValidatableResponse massCreation(String name, byte[] content, String contentType) {
        return given().spec(authenticated())
            .multiPart("file", name, content, contentType)
        .when()
            .post(BASE + "/massCreation")
        .then();
    }

    private void remember(io.restassured.response.ValidatableResponse created) {
        created.extract().jsonPath().getList("id", Long.class).forEach(createdIds::add);
    }

    private static int peopleNamed(String tag) {
        return given().spec(authenticated())
            .accept("application/json")
        .when()
            .get(BASE + "/findPeopleByName/" + tag)
        .then()
            .statusCode(200)
        .extract().path("page.totalElements");
    }

    // ---------------------------------------------------------------- CSV report

    @Test
    void exportPageAsCsvHasOneLinePerPersonOfThePageInTheSameOrderAsTheListing() throws Exception {
        byte[] csv = export(CSV, 0, 5, "asc");

        List<CSVRecord> records = parseCsv(csv, EXPORT_HEADER);
        assertEquals(5, records.size());
        assertEquals(
            idsOfThePage(0, 5, "asc").stream().map(String::valueOf).toList(),
            records.stream().map(record -> record.get("ID")).toList());
        assertEquals(
            firstNamesOfThePage(0, 5, "asc"),
            records.stream().map(record -> record.get("First Name")).toList());
    }

    @Test
    void exportPageAsCsvHasTheHeadersAndTheDownloadName() {
        given().spec(authenticated())
            .header("Accept", CSV)
            .queryParam("size", 2)
        .when()
            .get(BASE + "/exportPage")
        .then()
            .statusCode(200)
            .contentType(startsWith("text/csv"))
            .header("Content-Disposition", equalTo("attachment; filename=\"people_exported.csv\""))
            .body(startsWith("ID,First Name,Last Name,Address,Gender,Enabled"));
    }

    @Test
    void exportPageRespectsThePageSizeAndTheDirection() throws Exception {
        List<CSVRecord> ascending = parseCsv(export(CSV, 0, 3, "asc"), EXPORT_HEADER);
        List<CSVRecord> descending = parseCsv(export(CSV, 0, 3, "desc"), EXPORT_HEADER);
        List<CSVRecord> secondPage = parseCsv(export(CSV, 1, 3, "asc"), EXPORT_HEADER);

        assertEquals(3, ascending.size());
        assertEquals(3, descending.size());
        assertEquals(3, secondPage.size());
        assertNotEquals(ascending.get(0).get("ID"), descending.get(0).get("ID"));
        assertEquals(
            idsOfThePage(0, 3, "desc").stream().map(String::valueOf).toList(),
            descending.stream().map(record -> record.get("ID")).toList());
        assertEquals(
            idsOfThePage(1, 3, "asc").stream().map(String::valueOf).toList(),
            secondPage.stream().map(record -> record.get("ID")).toList());
    }

    @Test
    void exportPageOutsideTheDataIsJustTheHeader() throws Exception {
        assertTrue(parseCsv(export(CSV, 99999, 5, "asc"), EXPORT_HEADER).isEmpty());
    }

    // ---------------------------------------------------------------- XLSX report

    @Test
    void exportPageAsXlsxHasTheSameRowsAsTheListing() throws Exception {
        byte[] bytes = export(XLSX, 0, 5, "asc");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("People");
            assertNotNull(sheet);
            assertEquals(5, sheet.getLastRowNum());

            DataFormatter formatter = new DataFormatter();
            List<String> header = new ArrayList<>();
            sheet.getRow(0).forEach(cell -> header.add(formatter.formatCellValue(cell)));
            assertEquals(EXPORT_HEADER, header);

            List<String> expectedNames = firstNamesOfThePage(0, 5, "asc");
            List<Integer> expectedIds = idsOfThePage(0, 5, "asc");
            for (int i = 0; i < 5; i++) {
                Row row = sheet.getRow(i + 1);
                assertEquals(expectedIds.get(i).doubleValue(), row.getCell(0).getNumericCellValue());
                assertEquals(expectedNames.get(i), formatter.formatCellValue(row.getCell(1)));
                assertThat(formatter.formatCellValue(row.getCell(5)), anyOf(is("Yes"), is("No")));
            }
        }
    }

    @Test
    void exportPageAsXlsxHasTheContentTypeAndTheDownloadName() {
        given().spec(authenticated())
            .header("Accept", XLSX)
            .queryParam("size", 2)
        .when()
            .get(BASE + "/exportPage")
        .then()
            .statusCode(200)
            .contentType(XLSX)
            .header("Content-Disposition", equalTo("attachment; filename=\"people_exported.xlsx\""));
    }

    // ---------------------------------------------------------------- PDF reports (they need the network, see NetworkAssumptions)

    @Test
    void exportPageAsPdfIsAValidPdfWithThePeopleOfThePage() throws Exception {
        assumeReportImagesAreReachable();

        byte[] pdf = given().spec(authenticated())
            .header("Accept", PDF)
            .queryParam("page", 0).queryParam("size", 5)
        .when()
            .get(BASE + "/exportPage")
        .then()
            .statusCode(200)
            .contentType(PDF)
            .header("Content-Disposition", equalTo("attachment; filename=\"people_exported.pdf\""))
        .extract().asByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4, US_ASCII));
        String text = pdfText(pdf);
        assertTrue(text.contains("PEOPLE REPORT"), text);
        for (String firstName : firstNamesOfThePage(0, 5, "asc")) {
            assertTrue(text.contains(firstName), firstName + " is missing from: " + text);
        }
    }

    @Test
    void exportOnePersonAsPdfIsAValidPdfAboutThatPerson() throws Exception {
        assumeReportImagesAreReachable();
        String firstName = given().spec(authenticated()).accept("application/json").get(BASE + "/1").then().statusCode(200).extract().path("firstName");

        byte[] pdf = given().spec(authenticated())
            .header("Accept", PDF)
        .when()
            .get(BASE + "/export/1")
        .then()
            .statusCode(200)
            .contentType(PDF)
            .header("Content-Disposition", containsString(".pdf"))
        .extract().asByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4, US_ASCII));
        assertTrue(pdfText(pdf).contains(firstName));
    }

    // ---------------------------------------------------------------- export errors

    @Test
    void exportOnePersonThatDoesNotExistIsNotFound() {
        // the error can only be rendered when the client also accepts JSON
        given().spec(authenticated())
            .header("Accept", PDF + ", application/json")
        .when()
            .get(BASE + "/export/999999")
        .then()
            .statusCode(404)
            .body("message", equalTo("No records found for this ID!"));
    }

    @Test
    void exportOnePersonThatDoesNotExistAsksingOnlyForPdfGetsAnEmptyForbidden() {
        // the error body cannot be rendered as a PDF, and the error dispatch is not allowed by the security rules
        given().spec(authenticated())
            .header("Accept", PDF)
        .when()
            .get(BASE + "/export/999999")
        .then()
            .statusCode(403)
            .body(emptyOrNullString());
    }

    @Test
    void aSinglePersonOnlyExportsToPdf() {
        given().spec(authenticated())
            .header("Accept", CSV)
        .when()
            .get(BASE + "/export/1")
        .then()
            .statusCode(406);
    }

    @Test
    void exportPageRejectsAFormatItCannotProduce() {
        given().spec(authenticated())
            .header("Accept", "application/json")
        .when()
            .get(BASE + "/exportPage")
        .then()
            .statusCode(both(greaterThanOrEqualTo(400)).and(lessThan(500)));
    }

    @Test
    void exportsRequireAuthentication() {
        given().spec(anonymous()).header("Accept", CSV).get(BASE + "/exportPage").then().statusCode(403);
        given().spec(anonymous()).header("Accept", PDF).get(BASE + "/export/1").then().statusCode(403);
    }

    // ---------------------------------------------------------------- mass creation from CSV

    @Test
    void massCreationFromACsvCreatesEveryPersonOfTheFile() {
        String tag = tag();
        String csv = "first_name,last_name,address,gender\n"
            + "Imp" + tag + "A,Csv,Street 1,Female\n"
            + "Imp" + tag + "B,Csv,\"Street 2, apto 3\",Male\n";

        var created = massCreation("people.csv", csv.getBytes(UTF_8), "text/csv")
            .statusCode(200)
            .body("size()", is(2))
            .body("firstName", contains("Imp" + tag + "A", "Imp" + tag + "B"))
            .body("lastName", everyItem(is("Csv")))
            .body("address", contains("Street 1", "Street 2, apto 3"))
            .body("gender", contains("Female", "Male"))
            .body("enabled", everyItem(is(true)))
            .body("id", everyItem(notNullValue()))
            .body("links", everyItem(not(empty())));
        remember(created);

        assertEquals(2, peopleNamed("imp" + tag));
    }

    @Test
    void massCreationFromACsvKeepsTheAccents() {
        String tag = tag();
        String csv = "first_name,last_name,address,gender\nJoão" + tag + ",Conceição,Avenida São João,Male\n";

        var created = massCreation("people.csv", csv.getBytes(UTF_8), "text/csv")
            .statusCode(200)
            .body("[0].firstName", equalTo("João" + tag))
            .body("[0].lastName", equalTo("Conceição"))
            .body("[0].address", equalTo("Avenida São João"));
        remember(created);

        given().spec(authenticated()).accept("application/json")
            .get(BASE + "/" + created.extract().path("[0].id"))
            .then().statusCode(200)
            .body("firstName", equalTo("João" + tag))
            .body("address", equalTo("Avenida São João"));
    }

    @Test
    void massCreationFromACsvWithAMissingColumnCreatesNobody() {
        String tag = tag();
        String csv = "first_name,last_name,address\nImp" + tag + ",Csv,Street 1\n";

        massCreation("people.csv", csv.getBytes(UTF_8), "text/csv")
            .statusCode(500)
            .body("message", equalTo("Error processing the file!"));

        assertEquals(0, peopleNamed("imp" + tag));
    }

    // ---------------------------------------------------------------- mass creation from XLSX

    @Test
    void massCreationFromAnXlsxCreatesEveryPersonOfTheFile() throws Exception {
        String tag = tag();
        byte[] workbook = xlsx(new String[][]{
            {"first_name", "last_name", "address", "gender"},
            {"Imp" + tag + "A", "Xlsx", "Street 1", "Female"},
            {"Imp" + tag + "B", "Xlsx", "Street 2", "Male"},
            {"Imp" + tag + "C", "Xlsx", "Street 3", "Female"}});

        var created = massCreation("people.xlsx", workbook, XLSX)
            .statusCode(200)
            .body("size()", is(3))
            .body("firstName", contains("Imp" + tag + "A", "Imp" + tag + "B", "Imp" + tag + "C"))
            .body("lastName", everyItem(is("Xlsx")))
            .body("enabled", everyItem(is(true)))
            .body("id", everyItem(notNullValue()));
        remember(created);

        assertEquals(3, peopleNamed("imp" + tag));
    }

    @Test
    void massCreationCanAnswerInXml() throws Exception {
        String tag = tag();
        byte[] workbook = xlsx(new String[][]{
            {"first_name", "last_name", "address", "gender"},
            {"Imp" + tag, "Xml", "Street 1", "Male"}});

        var response = given().spec(authenticated())
            .header("Accept", "application/xml")
            .multiPart("file", "people.xlsx", workbook, XLSX)
        .when()
            .post(BASE + "/massCreation")
        .then()
            .statusCode(200)
            .contentType(containsString("xml"))
            .body("List.item.firstName", equalTo("Imp" + tag));
        response.extract().xmlPath().getList("List.item.id", Long.class).forEach(createdIds::add);
    }

    // ---------------------------------------------------------------- mass creation errors

    @Test
    void massCreationOfAnUnsupportedFileTypeIsRejected() {
        massCreation("people.txt", "first_name,last_name,address,gender\nX,Y,Z,Male\n".getBytes(UTF_8), "text/plain")
            .statusCode(500)
            .body("message", equalTo("Error processing the file!"));
    }

    @Test
    void massCreationOfAnEmptyFileIsABadRequest() {
        massCreation("people.csv", new byte[0], "text/csv")
            .statusCode(400)
            .body("message", equalTo("Please set a Valid File!"));
    }

    @Test
    void massCreationWithoutAFileIsRejected() {
        given().spec(authenticated())
            .multiPart("notTheFile", "people.csv", "x".getBytes(UTF_8), "text/csv")
        .when()
            .post(BASE + "/massCreation")
        .then()
            .statusCode(400);
    }

    @Test
    void massCreationRequiresAuthentication() {
        given().spec(anonymous())
            .multiPart("file", "people.csv", "x".getBytes(UTF_8), "text/csv")
        .when()
            .post(BASE + "/massCreation")
        .then()
            .statusCode(403);
    }
}
