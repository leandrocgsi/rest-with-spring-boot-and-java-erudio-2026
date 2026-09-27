package br.com.erudio.services;

import br.com.erudio.config.AwsS3Properties;
import br.com.erudio.exception.FileNotFoundException;
import br.com.erudio.exception.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final S3Client s3Client;
    private final String bucket;

    @Autowired
    public FileStorageService(S3Client s3Client, AwsS3Properties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getBucket();
        createBucketIfMissing();
    }

    private void createBucketIfMissing() {
        try {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                logger.info("Creating S3 bucket " + bucket);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            logger.error("Could not verify or create the S3 bucket where files will be stored!");
            throw new FileStorageException("Could not verify or create the S3 bucket where files will be stored!", e);
        }
    }

    public String storeFile(MultipartFile file) {

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            if (fileName.contains("..")) {
                logger.error("Sorry! Filename Contains a Invalid path Sequence " + fileName);
                throw new FileStorageException("Sorry! Filename Contains a Invalid path Sequence " + fileName);
            }

            logger.info("Saving file in S3");

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(file.getContentType())
                .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return fileName;
        } catch (Exception e) {
            logger.error("Could not store file " + fileName + ". Please try Again!");
            throw new FileStorageException("Could not store file " + fileName + ". Please try Again!", e);
        }
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            byte[] content = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(fileName).build()).asByteArray();

            return new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return fileName;
                }

                @Override
                public File getFile() {
                    return new File(fileName);
                }

                @Override
                public boolean exists() {
                    return true;
                }
            };
        } catch (Exception e) {
            logger.error("File not found " + fileName);
            throw new FileNotFoundException("File not found " + fileName, e);
        }
    }
}
