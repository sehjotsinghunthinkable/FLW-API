package com.iemr.flw.service.impl;

import com.iemr.flw.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3StorageServiceImpl implements S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    @Autowired
    private S3Client s3Client;

    @Override
    public boolean isBucketReachable(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (Exception e) {
            logger.debug("S3 bucket '{}' unreachable: {}", bucket, e.getMessage());
            return false;
        }
    }

    @Override
    public String uploadObject(String bucket, String key, byte[] content, String contentType) throws Exception {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
        return key;
    }
}
