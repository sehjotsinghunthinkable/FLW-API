package com.iemr.flw.service;

/**
 * Generic S3 access, deliberately independent of any specific feature/entity so it can be
 * reused by future S3-backed sync jobs without duplicating client/connectivity/upload logic.
 */
public interface S3StorageService {

    boolean isBucketReachable(String bucket);

    String uploadObject(String bucket, String key, byte[] content, String contentType) throws Exception;

    static String buildObjectKey(String... segments) {
        return String.join("/", segments);
    }
}
