package br.com.erudio.unittests.file;

import br.com.erudio.exception.BadRequestException;
import br.com.erudio.file.exporter.MediaTypes;
import br.com.erudio.file.exporter.contract.PersonExporter;
import br.com.erudio.file.exporter.factory.FileExporterFactory;
import br.com.erudio.file.exporter.impl.CsvExporter;
import br.com.erudio.file.exporter.impl.PdfExporter;
import br.com.erudio.file.exporter.impl.XlsxExporter;
import br.com.erudio.file.importer.contract.FileImporter;
import br.com.erudio.file.importer.factory.FileImporterFactory;
import br.com.erudio.file.importer.impl.CsvImporter;
import br.com.erudio.file.importer.impl.XlsxImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileFactoriesTest {

    @Nested
    class Exporters {

        private final ApplicationContext context = mock(ApplicationContext.class);
        private final FileExporterFactory factory = new FileExporterFactory();
        private final XlsxExporter xlsx = new XlsxExporter();
        private final CsvExporter csv = new CsvExporter();
        private final PdfExporter pdf = new PdfExporter();

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(factory, "context", context);
            when(context.getBean(XlsxExporter.class)).thenReturn(xlsx);
            when(context.getBean(CsvExporter.class)).thenReturn(csv);
            when(context.getBean(PdfExporter.class)).thenReturn(pdf);
        }

        @Test
        void picksTheExporterFromTheAcceptHeader() throws Exception {
            assertSame(xlsx, factory.getExporter(MediaTypes.APPLICATION_XLSX_VALUE));
            assertSame(csv, factory.getExporter(MediaTypes.APPLICATION_CSV_VALUE));
            assertSame(pdf, factory.getExporter(MediaTypes.APPLICATION_PDF_VALUE));
        }

        @Test
        void theAcceptHeaderIsMatchedIgnoringCase() throws Exception {
            assertSame(csv, factory.getExporter("TEXT/CSV"));
            assertSame(pdf, factory.getExporter("Application/PDF"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"application/json", "application/xml", "text/plain", "*/*", ""})
        void rejectsAnyOtherFormat(String accept) {
            BadRequestException exception = assertThrows(BadRequestException.class, () -> factory.getExporter(accept));

            assertEquals("Invalid File Format!", exception.getMessage());
        }

        @Test
        void theExporterItReturnsIsUsable() throws Exception {
            PersonExporter exporter = factory.getExporter(MediaTypes.APPLICATION_CSV_VALUE);

            assertTrue(exporter.exportPeople(List.of()).contentLength() > 0, "at least the header line");
        }
    }

    @Nested
    class Importers {

        private final ApplicationContext context = mock(ApplicationContext.class);
        private final FileImporterFactory factory = new FileImporterFactory();
        private final XlsxImporter xlsx = new XlsxImporter();
        private final CsvImporter csv = new CsvImporter();

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(factory, "context", context);
            when(context.getBean(XlsxImporter.class)).thenReturn(xlsx);
            when(context.getBean(CsvImporter.class)).thenReturn(csv);
        }

        @Test
        void picksTheImporterFromTheFileExtension() throws Exception {
            FileImporter fromXlsx = factory.getImporter("people.xlsx");
            FileImporter fromCsv = factory.getImporter("people.csv");

            assertSame(xlsx, fromXlsx);
            assertSame(csv, fromCsv);
        }

        @Test
        void onlyTheLastExtensionCounts() throws Exception {
            assertSame(csv, factory.getImporter("people.xlsx.csv"));
            assertSame(xlsx, factory.getImporter("my people (1).xlsx"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"people.txt", "people.xls", "people.pdf", "people", "csv", "people.csv.bak", ""})
        void rejectsAnyOtherExtension(String fileName) {
            BadRequestException exception = assertThrows(BadRequestException.class, () -> factory.getImporter(fileName));

            assertEquals("Invalid File Format!", exception.getMessage());
        }
    }
}
