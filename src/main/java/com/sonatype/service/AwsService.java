package com.sonatype.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.AWSCodePipelineClientBuilder;
import com.amazonaws.services.codepipeline.model.FailureDetails;
import com.amazonaws.services.codepipeline.model.FailureType;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.sonatype.dto.CodePipelineJobDto;
import com.sonatype.util.ZipExtractor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AwsService {

  private static final Logger logger = LoggerFactory.getLogger(AwsService.class);

  private final CodePipelineJobDto codePipelineJobDto;

  public AwsService(CodePipelineJobDto codePipelineJobDto) {
    this.codePipelineJobDto = codePipelineJobDto;
  }

  public String getIqServerPassword() {
    String region = System.getenv("IQ_SERVER_CREDENTIALS_REGION");
    Region awsRegion = Region.regions()
        .stream().filter(r -> r.id().equalsIgnoreCase(region)).findFirst().get();
    String secretName = System.getenv("IQ_SERVER_PASSWORD");
    try (SecretsManagerClient client = SecretsManagerClient.builder().region(awsRegion).build()) {
      GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
      GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
      String iqServerPassword = getSecretValueResponse.secretString();
      return iqServerPassword;
    }
  }

  public File getScanDir() {
    BasicSessionCredentials awsCredentials = new BasicSessionCredentials(codePipelineJobDto.accessKeyId, codePipelineJobDto.secretAccessKey, codePipelineJobDto.sessionToken);
    AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

    GetObjectRequest getObjectRequest = new GetObjectRequest(codePipelineJobDto.srcBucket, codePipelineJobDto.srcKey);
    S3Object object = s3Client.getObject(getObjectRequest);
    InputStream s3ObjectInputStream = object.getObjectContent();

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

  public void success() {
    PutJobSuccessResultRequest jobSuccessResultRequest = new PutJobSuccessResultRequest();
    jobSuccessResultRequest.setJobId(codePipelineJobDto.id);
    AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
    awsCodePipeline.putJobSuccessResult(jobSuccessResultRequest);
  }

  public void fail(String cause) {
    PutJobFailureResultRequest putJobFailureResultRequest = new PutJobFailureResultRequest();
    putJobFailureResultRequest.setJobId(codePipelineJobDto.id);

    FailureDetails failureDetails = new FailureDetails();
    failureDetails.setType(FailureType.JobFailed);
    failureDetails.setMessage(cause);
    putJobFailureResultRequest.setFailureDetails(failureDetails);

    AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
    awsCodePipeline.putJobFailureResult(putJobFailureResultRequest);
  }
}
