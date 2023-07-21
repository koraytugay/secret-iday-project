package com.sonatype;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.AWSCodePipelineClientBuilder;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AwsService {

  public String getIqServerCredentials() {
    String secretName = System.getenv("IQ_SERVER_CREDENTIALS");
    try (SecretsManagerClient client = SecretsManagerClient.builder().region(Region.US_EAST_1).build()) {
      GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
      GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
      String secret = getSecretValueResponse.secretString();
      return secret;
    }
  }

  public File getScanDir(CodePipelineJobDto codePipelineJobDto) {
    BasicSessionCredentials awsCredentials = new BasicSessionCredentials(codePipelineJobDto.accessKeyId, codePipelineJobDto.secretAccessKey, codePipelineJobDto.sessionToken);
    AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

    InputStream s3ObjectInputStream = getObject(s3Client, codePipelineJobDto.srcBucket, codePipelineJobDto.srcKey);
    File targetZip;
    try {
      targetZip = File.createTempFile("temp-", ".zip");
      targetZip.deleteOnExit();
      FileOutputStream out = new FileOutputStream(targetZip);
      IOUtils.copy(s3ObjectInputStream, out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    File scanDir;
    try {
      scanDir = ZipExtractor.extract(targetZip);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return scanDir;
  }

  public void foo(String jobId) {
    PutJobSuccessResultRequest jobSuccessResultRequest = new PutJobSuccessResultRequest();
    jobSuccessResultRequest.setJobId(jobId);
    AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
    awsCodePipeline.putJobSuccessResult(jobSuccessResultRequest);


    //    if ("Failure".equalsIgnoreCase(resultDto.policyAction)) {
//      PutJobFailureResultRequest putJobFailureResultRequest = new PutJobFailureResultRequest();
//      putJobFailureResultRequest.setJobId(jobId);
//
//      FailureDetails failureDetails = new FailureDetails();
//      failureDetails.setType(FailureType.JobFailed);
//      failureDetails.setMessage("Failed due to policy violations");
//      putJobFailureResultRequest.setFailureDetails(failureDetails);
//
//      AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
//      awsCodePipeline.putJobFailureResult(putJobFailureResultRequest);
//    }
//    else {
//      PutJobSuccessResultRequest jobSuccessResultRequest = new PutJobSuccessResultRequest();
//      jobSuccessResultRequest.setJobId(jobId);
//      AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
//      awsCodePipeline.putJobSuccessResult(jobSuccessResultRequest);
//    }
  }

  private InputStream getObject(AmazonS3 s3Client, String bucket, String key) {
    GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
    S3Object object = s3Client.getObject(getObjectRequest);
    return object.getObjectContent();
  }
}
