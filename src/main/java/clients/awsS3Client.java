package clients;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class awsS3Client {
    private final Region region = Region.US_WEST_2;
    private final S3Client s3 = S3Client.builder()
            .region(region)
            .build();

    public void createBucket(String bucket) throws S3Exception {
        CreateBucketRequest createRequest = CreateBucketRequest
                .builder()
                .bucket(bucket)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .locationConstraint(region.id())
                                .build())
                .build();
        s3.createBucket(createRequest);
    }

    public void deleteBucket(String bucket) throws S3Exception {
        DeleteBucketRequest deleteRequest = DeleteBucketRequest
                .builder()
                .bucket(bucket)
                .build();
        s3.deleteBucket(deleteRequest);
    }

    public void putObject(String bucket, String key, File file) throws S3Exception {
        PutObjectRequest putRequest = PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.putObject(putRequest, RequestBody.fromFile(file));
    }

    public void putString(String bucket, String key, String str) throws S3Exception{
        PutObjectRequest putRequest = PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.putObject(putRequest, RequestBody.fromString(str));
    }

    public BufferedReader getObject(String bucket, String key) throws S3Exception{
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3Response = s3.getObject(getRequest);
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3Response));
        return reader;
    }

    public void deleteObject(String bucket, String key) throws S3Exception {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.deleteObject(deleteRequest);
    }

}
