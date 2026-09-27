package br.com.erudio.unittests.services;

import br.com.erudio.config.AwsS3Properties;
import br.com.erudio.exception.FileNotFoundException;
import br.com.erudio.exception.FileStorageException;
import br.com.erudio.services.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    private static final String BUCKET = "erudio-files-test";

    private S3Client s3Client;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        service = new FileStorageService(s3Client, propertiesFor(BUCKET));
    }

    private static AwsS3Properties propertiesFor(String bucket) {
        AwsS3Properties properties = new AwsS3Properties();
        properties.setBucket(bucket);
        return properties;
    }

    private static MultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(UTF_8));
    }

    @Test
    void doesNotRecreateTheBucketWhenItAlreadyExists() {
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void createsTheBucketWhenItDoesNotExist() {
        S3Client freshClient = mock(S3Client.class);
        when(freshClient.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("missing").build());

        new FileStorageService(freshClient, propertiesFor(BUCKET));

        verify(freshClient).createBucket(argThat((CreateBucketRequest r) -> r.bucket().equals(BUCKET)));
    }

    @Test
    void failsWhenTheBucketCannotBeVerifiedOrCreated() {
        S3Client brokenClient = mock(S3Client.class);
        when(brokenClient.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("missing").build());
        when(brokenClient.createBucket(any(CreateBucketRequest.class)))
            .thenThrow(S3Exception.builder().message("boom").build());

        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> new FileStorageService(brokenClient, propertiesFor(BUCKET)));

        assertEquals("Could not verify or create the S3 bucket where files will be stored!", exception.getMessage());
    }

    @Test
    void storesTheFileWithItsContentAndReturnsItsName() throws IOException {
        String stored = service.storeFile(file("notes.txt", "hello upload"));

        assertEquals("notes.txt", stored);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        assertEquals(BUCKET, requestCaptor.getValue().bucket());
        assertEquals("notes.txt", requestCaptor.getValue().key());
        assertEquals("hello upload", contentOf(bodyCaptor.getValue()));
    }

    @Test
    void replacesAFileThatAlreadyExists() throws IOException {
        service.storeFile(file("notes.txt", "first"));
        service.storeFile(file("notes.txt", "second"));

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), bodyCaptor.capture());

        assertEquals("second", contentOf(bodyCaptor.getAllValues().get(1)));
    }

    @Test
    void cleansRedundantPathSegmentsFromTheName() {
        String stored = service.storeFile(file("folder/../notes.txt", "content"));

        assertEquals("notes.txt", stored);
        verify(s3Client).putObject(argThat((PutObjectRequest r) -> r.key().equals("notes.txt")), any(RequestBody.class));
    }

    @Test
    void rejectsANameThatEscapesTheUploadDirectory() {
        FileStorageException exception = assertThrows(FileStorageException.class,
            () -> service.storeFile(file("../evil.txt", "boom")));

        assertEquals("Could not store file ../evil.txt. Please try Again!", exception.getMessage());
        assertInstanceOf(FileStorageException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Invalid path Sequence"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void reportsAFailureReadingTheUploadedContent() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.txt");
        when(broken.getBytes()).thenThrow(new IOException("connection reset"));

        FileStorageException exception = assertThrows(FileStorageException.class, () -> service.storeFile(broken));

        assertEquals("Could not store file broken.txt. Please try Again!", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void loadsAFileThatWasStored() throws IOException {
        when(s3Client.getObjectAsBytes(argThat((GetObjectRequest r) -> r.key().equals("notes.txt"))))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "stored content".getBytes(UTF_8)));

        Resource resource = service.loadFileAsResource("notes.txt");

        assertTrue(resource.exists());
        assertEquals("notes.txt", resource.getFilename());
        assertEquals("stored content", new String(resource.getContentAsByteArray(), UTF_8));
    }

    @Test
    void failsToLoadAFileThatDoesNotExist() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("missing").build());

        FileNotFoundException exception = assertThrows(FileNotFoundException.class,
            () -> service.loadFileAsResource("missing.txt"));

        assertEquals("File not found missing.txt", exception.getMessage());
    }

    private static String contentOf(RequestBody body) throws IOException {
        return new String(body.contentStreamProvider().newStream().readAllBytes(), UTF_8);
    }
}
