package br.com.erudio.unittests.services;

import br.com.erudio.data.dto.PersonDTO;
import br.com.erudio.exception.BadRequestException;
import br.com.erudio.exception.FileStorageException;
import br.com.erudio.exception.ResourceNotFoundException;
import br.com.erudio.file.exporter.contract.PersonExporter;
import br.com.erudio.file.exporter.factory.FileExporterFactory;
import br.com.erudio.file.importer.contract.FileImporter;
import br.com.erudio.file.importer.factory.FileImporterFactory;
import br.com.erudio.model.Person;
import br.com.erudio.repository.PersonRepository;
import br.com.erudio.services.PersonService;
import br.com.erudio.unittests.mapper.mocks.MockPerson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceFilesTest {

    private static final String CSV = "text/csv";

    private final MockPerson input = new MockPerson();

    @InjectMocks
    private PersonService service;

    @Mock
    private PersonRepository repository;

    @Mock
    private FileImporterFactory importerFactory;

    @Mock
    private FileExporterFactory exporterFactory;

    @Mock
    private PagedResourcesAssembler<PersonDTO> assembler;

    private PersonExporter personExporter;
    private Resource exported;

    @BeforeEach
    void setUp() {
        personExporter = mock(PersonExporter.class);
        exported = new ByteArrayResource("exported".getBytes(UTF_8));
    }

    private static PersonDTO dto(String firstName) {
        PersonDTO person = new PersonDTO();
        person.setFirstName(firstName);
        person.setLastName("Last");
        person.setAddress("Address");
        person.setGender("Male");
        person.setEnabled(true);
        return person;
    }

    private static MultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, CSV, content.getBytes(UTF_8));
    }

    @Test
    void exportPageExportsTheRequestedPageInTheFormatOfTheAcceptHeader() throws Exception {
        Pageable pageable = PageRequest.of(0, 3, Sort.by("firstName"));
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(input.mockEntityList().subList(0, 3)));
        when(exporterFactory.getExporter(CSV)).thenReturn(personExporter);
        when(personExporter.exportPeople(anyList())).thenReturn(exported);

        Resource result = service.exportPage(pageable, CSV);

        assertSame(exported, result);
        ArgumentCaptor<List<PersonDTO>> people = ArgumentCaptor.forClass(List.class);
        verify(personExporter).exportPeople(people.capture());
        assertEquals(
            List.of("First Name Test0", "First Name Test1", "First Name Test2"),
            people.getValue().stream().map(PersonDTO::getFirstName).toList());
    }

    @Test
    void exportPageWrapsAnyFailureKeepingTheCause() throws Exception {
        Pageable pageable = PageRequest.of(0, 3);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));
        when(exporterFactory.getExporter("application/json")).thenThrow(new BadRequestException("Invalid File Format!"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.exportPage(pageable, "application/json"));

        assertEquals("Error during file export!", exception.getMessage());
        assertInstanceOf(BadRequestException.class, exception.getCause());
    }

    @Test
    void exportPersonExportsThePersonThatWasFound() throws Exception {
        Person entity = input.mockEntity(1);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(exporterFactory.getExporter("application/pdf")).thenReturn(personExporter);
        when(personExporter.exportPerson(any(PersonDTO.class))).thenReturn(exported);

        Resource result = service.exportPerson(1L, "application/pdf");

        assertSame(exported, result);
        ArgumentCaptor<PersonDTO> person = ArgumentCaptor.forClass(PersonDTO.class);
        verify(personExporter).exportPerson(person.capture());
        assertEquals("First Name Test1", person.getValue().getFirstName());
    }

    @Test
    void exportPersonFailsWhenThePersonDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
            () -> service.exportPerson(99L, "application/pdf"));

        assertEquals("No records found for this ID!", exception.getMessage());
        verifyNoInteractions(exporterFactory);
    }

    @Test
    void exportPersonWrapsAnyFailureKeepingTheCause() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(input.mockEntity(1)));
        when(exporterFactory.getExporter("application/pdf")).thenReturn(personExporter);
        when(personExporter.exportPerson(any(PersonDTO.class))).thenThrow(new IllegalStateException("template broken"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.exportPerson(1L, "application/pdf"));

        assertEquals("Error during file export!", exception.getMessage());
        assertEquals("template broken", exception.getCause().getMessage());
    }

    @Test
    void massCreationSavesEveryPersonOfTheFileAndReturnsThemWithLinks() throws Exception {
        FileImporter fileImporter = mock(FileImporter.class);
        when(importerFactory.getImporter("people.csv")).thenReturn(fileImporter);
        when(fileImporter.importFile(any(InputStream.class))).thenReturn(List.of(dto("Ada"), dto("Alan")));
        AtomicLong ids = new AtomicLong(100);
        when(repository.save(any(Person.class))).thenAnswer(invocation -> {
            Person saved = invocation.getArgument(0);
            saved.setId(ids.incrementAndGet());
            return saved;
        });

        List<PersonDTO> created = service.massCreation(csvFile("people.csv", "irrelevant, the importer is a mock"));

        assertEquals(2, created.size());
        assertEquals(List.of("Ada", "Alan"), created.stream().map(PersonDTO::getFirstName).toList());
        assertEquals(List.of(101L, 102L), created.stream().map(PersonDTO::getId).toList());
        created.forEach(person -> assertFalse(person.getLinks().isEmpty(), "every created person carries its HATEOAS links"));
        verify(repository, times(2)).save(any(Person.class));
    }

    @Test
    void massCreationOfAnEmptyFileIsABadRequest() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.massCreation(csvFile("people.csv", "")));

        assertEquals("Please set a Valid File!", exception.getMessage());
        verifyNoInteractions(importerFactory, repository);
    }

    @Test
    void massCreationReportsAnUnsupportedFileAsAStorageError() throws Exception {
        when(importerFactory.getImporter("people.txt")).thenThrow(new BadRequestException("Invalid File Format!"));

        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> service.massCreation(csvFile("people.txt", "whatever")));

        assertEquals("Error processing the file!", exception.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void massCreationReportsAnUnreadableFileAsAStorageError() throws Exception {
        FileImporter fileImporter = mock(FileImporter.class);
        when(importerFactory.getImporter("people.csv")).thenReturn(fileImporter);
        when(fileImporter.importFile(any(InputStream.class))).thenThrow(new IllegalArgumentException("Mapping for gender not found"));

        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> service.massCreation(csvFile("people.csv", "first_name\nAda\n")));

        assertEquals("Error processing the file!", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void massCreationReportsAFileWithoutNameAsAStorageError() throws IOException {
        MultipartFile nameless = mock(MultipartFile.class);
        when(nameless.isEmpty()).thenReturn(false);
        when(nameless.getInputStream()).thenReturn(InputStream.nullInputStream());
        when(nameless.getOriginalFilename()).thenReturn(null);

        FileStorageException exception = assertThrows(FileStorageException.class, () -> service.massCreation(nameless));

        assertEquals("Error processing the file!", exception.getMessage());
        verifyNoInteractions(importerFactory, repository);
    }
}
