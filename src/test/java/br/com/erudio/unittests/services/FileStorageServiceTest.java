package br.com.erudio.unittests.services;

import br.com.erudio.config.FileStorageConfig;
import br.com.erudio.exception.FileNotFoundException;
import br.com.erudio.exception.FileStorageException;
import br.com.erudio.services.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private Path uploadDir;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        uploadDir = tempDir.resolve("uploads");
        service = new FileStorageService(configFor(uploadDir));
    }

    private static FileStorageConfig configFor(Path directory) {
        FileStorageConfig config = new FileStorageConfig();
        config.setUploadDir(directory.toString());
        return config;
    }

    private static MultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(UTF_8));
    }

    @Test
    void createsTheUploadDirectoryWhenItDoesNotExist() {
        Path nested = tempDir.resolve("a").resolve("b").resolve("uploads");

        new FileStorageService(configFor(nested));

        assertTrue(Files.isDirectory(nested));
    }

    @Test
    void failsWhenTheUploadDirectoryCannotBeCreated() throws IOException {
        Path regularFile = Files.writeString(tempDir.resolve("not-a-directory"), "x");

        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> new FileStorageService(configFor(regularFile.resolve("uploads"))));

        assertEquals("Could not create the directory where files will be stored!", exception.getMessage());
    }

    @Test
    void storesTheFileWithItsContentAndReturnsItsName() throws IOException {
        String stored = service.storeFile(file("notes.txt", "hello upload"));

        assertEquals("notes.txt", stored);
        assertEquals("hello upload", Files.readString(uploadDir.resolve("notes.txt")));
    }

    @Test
    void replacesAFileThatAlreadyExists() throws IOException {
        service.storeFile(file("notes.txt", "first"));
        service.storeFile(file("notes.txt", "second"));

        assertEquals("second", Files.readString(uploadDir.resolve("notes.txt")));
    }

    @Test
    void cleansRedundantPathSegmentsFromTheName() throws IOException {
        String stored = service.storeFile(file("folder/../notes.txt", "content"));

        assertEquals("notes.txt", stored);
        assertTrue(Files.exists(uploadDir.resolve("notes.txt")));
    }

    @Test
    void rejectsANameThatEscapesTheUploadDirectory() {
        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> service.storeFile(file("../evil.txt", "boom")));

        assertEquals("Could not store file ../evil.txt. Please try Again!", exception.getMessage());
        assertInstanceOf(FileStorageException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Invalid path Sequence"));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    void reportsAFailureReadingTheUploadedContent() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.txt");
        when(broken.getInputStream()).thenThrow(new IOException("connection reset"));

        FileStorageException exception = assertThrows(FileStorageException.class, () -> service.storeFile(broken));

        assertEquals("Could not store file broken.txt. Please try Again!", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void loadsAFileThatWasStored() throws IOException {
        service.storeFile(file("notes.txt", "stored content"));

        Resource resource = service.loadFileAsResource("notes.txt");

        assertTrue(resource.exists());
        assertEquals("notes.txt", resource.getFilename());
        assertEquals("stored content", new String(resource.getContentAsByteArray(), UTF_8));
    }

    @Test
    void failsToLoadAFileThatDoesNotExist() {
        FileNotFoundException exception = assertThrows(FileNotFoundException.class,
            () -> service.loadFileAsResource("missing.txt"));

        assertEquals("File not found missing.txt", exception.getMessage());
    }
}
