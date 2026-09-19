package br.com.erudio.unittests.file;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.file.importer.impl.CsvImporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class CsvImporterTest {

    private final CsvImporter importer = new CsvImporter();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
    }

    @Test
    void importsOnePersonPerLine() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("""
            first_name,last_name,address,gender
            Ada,Lovelace,London,Female
            Alan,Turing,Wilmslow,Male
            """));

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
        List<PersonDTO> people = importer.importFile(csv("""
            first_name,last_name,address,gender
            Ada,Lovelace,London,Female
            """));

        assertTrue(people.get(0).getEnabled());
        assertNull(people.get(0).getId());
    }

    @Test
    void theOrderOfTheColumnsDoesNotMatter() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("""
            gender,address,last_name,first_name
            Female,London,Lovelace,Ada
            """));

        assertEquals("Ada", people.get(0).getFirstName());
        assertEquals("Lovelace", people.get(0).getLastName());
        assertEquals("London", people.get(0).getAddress());
        assertEquals("Female", people.get(0).getGender());
    }

    @Test
    void trimsSpacesAroundTheValues() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("first_name,last_name,address,gender\n  Ada  ,  Lovelace ,  London  , Female \n"));

        assertEquals("Ada", people.get(0).getFirstName());
        assertEquals("Lovelace", people.get(0).getLastName());
        assertEquals("London", people.get(0).getAddress());
        assertEquals("Female", people.get(0).getGender());
    }

    @Test
    void ignoresEmptyLines() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("first_name,last_name,address,gender\n\nAda,Lovelace,London,Female\n\n\nAlan,Turing,Wilmslow,Male\n\n"));

        assertEquals(2, people.size());
    }

    @Test
    void keepsCommasInsideQuotedValues() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("first_name,last_name,address,gender\nAda,Lovelace,\"Rua A, 10 - apto 2\",Female\n"));

        assertEquals("Rua A, 10 - apto 2", people.get(0).getAddress());
    }

    @Test
    void readsUtf8Accents() throws Exception {
        List<PersonDTO> people = importer.importFile(csv("first_name,last_name,address,gender\nJoão,Conceição,Avenida São João,Male\n"));

        assertEquals("João", people.get(0).getFirstName());
        assertEquals("Conceição", people.get(0).getLastName());
        assertEquals("Avenida São João", people.get(0).getAddress());
    }

    @Test
    void importsNothingFromAHeaderOnlyFile() throws Exception {
        assertTrue(importer.importFile(csv("first_name,last_name,address,gender\n")).isEmpty());
    }

    @Test
    void importsNothingFromAnEmptyFile() throws Exception {
        assertTrue(importer.importFile(csv("")).isEmpty());
    }

    @Test
    void failsWhenARequiredColumnIsMissing() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> importer.importFile(csv("first_name,last_name,address\nAda,Lovelace,London\n")));

        assertTrue(exception.getMessage().contains("gender"), exception.getMessage());
    }

    @Test
    void failsWhenALineHasFewerValuesThanTheHeader() {
        assertThrows(IllegalArgumentException.class,
            () -> importer.importFile(csv("first_name,last_name,address,gender\nAda,Lovelace\n")));
    }
}
