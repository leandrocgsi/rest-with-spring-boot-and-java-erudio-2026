package br.com.erudio.unittests.file;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.exporter.impl.PdfExporter;
import br.com.erudio.model.Book;
import br.com.erudio.services.QRCodeService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static br.com.erudio.testsupport.NetworkAssumptions.assumeReportImagesAreReachable;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The templates download their images while the PDF is generated, so these tests are skipped when
 * raw.githubusercontent.com cannot be reached.
 */
class PdfExporterTest {

    private final PdfExporter exporter = new PdfExporter();

    @BeforeEach
    void setUp() {
        assumeReportImagesAreReachable();
        ReflectionTestUtils.setField(exporter, "service", new QRCodeService());
    }

    private static PersonDTO person(Long id, String firstName, String lastName) {
        PersonDTO person = new PersonDTO();
        person.setId(id);
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAddress("Address of " + firstName);
        person.setGender("Male");
        person.setEnabled(true);
        return person;
    }

    private static Book book(Long id, String title, String author) {
        Book book = new Book();
        book.setId(id);
        book.setTitle(title);
        book.setAuthor(author);
        book.setPrice(49.9);
        book.setLaunchDate(new Date(1_511_963_405_878L));
        return book;
    }

    private record ParsedPdf(int pages, String text) {}

    private static ParsedPdf read(Resource pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf.getContentAsByteArray());
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return new ParsedPdf(reader.getNumberOfPages(), text.toString());
        } finally {
            reader.close();
        }
    }

    @Test
    void exportPeopleGeneratesAPdfWithThePeople() throws Exception {
        Resource resource = exporter.exportPeople(List.of(
            person(1L, "Ayrton", "Senna"),
            person(2L, "Marie", "Curie")));

        byte[] bytes = resource.getContentAsByteArray();
        assertEquals("%PDF", new String(bytes, 0, 4, US_ASCII));

        ParsedPdf pdf = read(resource);
        assertEquals(1, pdf.pages());
        assertTrue(pdf.text().contains("PEOPLE REPORT"), pdf.text());
        assertTrue(pdf.text().contains("Ayrton"), pdf.text());
        assertTrue(pdf.text().contains("Marie"), pdf.text());
    }

    @Test
    void exportPeopleShowsTheNameOfTheCourseInThePageHeader() throws Exception {
        ParsedPdf pdf = read(exporter.exportPeople(List.of(person(1L, "Ayrton", "Senna"))));

        assertTrue(pdf.text().contains("Spring Boot 2026"), pdf.text());
        assertFalse(pdf.text().contains("RESTful from 0"), "the old course name must be gone: " + pdf.text());
    }

    @Test
    void exportPeopleSpansSeveralPagesWhenThereAreManyPeople() throws Exception {
        List<PersonDTO> people = new ArrayList<>();
        for (long i = 1; i <= 120; i++) {
            people.add(person(i, "Person" + i, "Number"));
        }

        ParsedPdf pdf = read(exporter.exportPeople(people));

        assertTrue(pdf.pages() > 1, "120 people cannot fit in a single page but got " + pdf.pages());
        assertTrue(pdf.text().contains("Person1 "), pdf.text());
        assertTrue(pdf.text().contains("Person120"), pdf.text());
    }

    @Test
    void exportPeopleGeneratesAValidPdfEvenWithoutPeople() throws Exception {
        Resource resource = exporter.exportPeople(List.of());

        assertEquals("%PDF", new String(resource.getContentAsByteArray(), 0, 4, US_ASCII));
        ParsedPdf pdf = read(resource);
        assertEquals(1, pdf.pages());
        assertFalse(pdf.text().contains("Ayrton"), pdf.text());
    }

    @Test
    void exportPersonGeneratesAPdfWithThePersonAndTheirBooks() throws Exception {
        PersonDTO person = person(1L, "Ayrton", "Senna");
        person.setProfileUrl("https://en.wikipedia.org/wiki/Ayrton_Senna");
        person.setPhotoUrl("https://raw.githubusercontent.com/leandrocgsi/rest-with-spring-boot-and-java-erudio/refs/heads/main/photos/01_senna.jpg");
        person.setBooks(List.of(
            book(1L, "Working effectively with legacy code", "Michael C. Feathers"),
            book(2L, "Clean Code", "Robert C. Martin")));

        Resource resource = exporter.exportPerson(person);

        assertEquals("%PDF", new String(resource.getContentAsByteArray(), 0, 4, US_ASCII));
        ParsedPdf pdf = read(resource);
        assertTrue(pdf.text().contains("Ayrton"), pdf.text());
        assertTrue(pdf.text().contains("Clean Code"), pdf.text());
        assertTrue(pdf.text().contains("Working effectively"), pdf.text());
    }
}
