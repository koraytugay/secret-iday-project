package com.sonatype;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.AWSCodePipelineClientBuilder;
import com.amazonaws.services.codepipeline.model.FailureDetails;
import com.amazonaws.services.codepipeline.model.FailureType;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.security.Permission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

// https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/s3-java/src/main/java/example/Handler.java
// https://docs.aws.amazon.com/AmazonS3/latest/userguide/AuthUsingTempSessionToken.html
public class LambdaHandler
    implements RequestHandler<Object, String> {

  static class UserParameters {

    public String stage;

    public String applicationId;
  }

  private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);

  @Override
  public String handleRequest(Object obj, Context context) {
    LinkedHashMap<Object, Object> input = (LinkedHashMap<Object, Object>) obj;

    Set<Entry<Object, Object>> entries = input.entrySet();

    logger.info("entries size:" + entries.size());

    for (Entry<Object, Object> entry : entries) {
      logger.info("" + entry.getKey().getClass());
      logger.info("" + entry.getKey());

      logger.info("" + entry.getValue().getClass());
      logger.info("" + entry.getValue());
    }

    Entry<Object, Object> next = entries.iterator().next();

    logger.info("next:" + next);

    LinkedHashMap<String, Object> codePipelineJob = (LinkedHashMap<String, Object>) next.getValue();

    LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) codePipelineJob.get("data");

    logger.info("printing data");
    logger.info("" + data);

    Object inputArtifacts = data.get("inputArtifacts");
    logger.info("printing input artifacts");

    logger.info("" + inputArtifacts);
    logger.info("" + inputArtifacts.getClass());

    List<Object> inputArtifactsList = (List<Object>) inputArtifacts;
    LinkedHashMap<Object, Object> inputArtifact = (LinkedHashMap<Object, Object>) inputArtifactsList.get(0);
    LinkedHashMap<Object, Object> location = (LinkedHashMap<Object, Object>) inputArtifact.get("location");
    LinkedHashMap<Object, Object> s3Location = (LinkedHashMap<Object, Object>) location.get("s3Location");

    String srcBucket = (String) s3Location.get("bucketName");
    String srcKey = (String) s3Location.get("objectKey");

    logger.info("src bucket:" + srcBucket);
    logger.info("object key:" + srcKey);

    LinkedHashMap<Object, Object> artifactCredentials = (LinkedHashMap<Object, Object>) data.get("artifactCredentials");

    logger.info("artifact credentials..");
    logger.info("" + artifactCredentials);

    BasicSessionCredentials awsCredentials = new BasicSessionCredentials(
        (String) artifactCredentials.get("accessKeyId"),
        (String) artifactCredentials.get("secretAccessKey"),
        (String) artifactCredentials.get("sessionToken"));

    AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
        .build();

    InputStream s3ObjectInputStream = getObject(s3Client, srcBucket, srcKey);

    File targetZip;
    try {
      targetZip = File.createTempFile("temp-", ".zip");
      targetZip.deleteOnExit();
      FileOutputStream out = new FileOutputStream(targetZip);
      IOUtils.copy(s3ObjectInputStream, out);
      logger.info("temp file generated");
      logger.info("temp file path:" + targetZip.getAbsolutePath());
      logger.info("temp file length:" + targetZip.length());
    } catch (IOException e) {
      logger.info("Uppss! Could not create temp file!");
      throw new RuntimeException(e);
    }

    String iqServerUrl = System.getenv("IQ_SERVER_URL");

    String secretFromSecretManager = getSecret();

    String iqServerCredentials = secretFromSecretManager;

    logger.info("Secret from AWS Secret Manager: {}", secretFromSecretManager);

    LinkedHashMap<String, Object> actionConfiguration = (LinkedHashMap<String, Object>) data.get("actionConfiguration");
    LinkedHashMap<String, Object> configuration = (LinkedHashMap<String, Object>) actionConfiguration.get("configuration");
    String userParametersJson = (String) configuration.get("UserParameters");
    logger.info("User parameters json:" + userParametersJson);


    UserParameters userParameters = new Gson().fromJson(userParametersJson, UserParameters.class);
    logger.info("applicationId:" + userParameters.applicationId);
    logger.info("stage:" + userParameters.stage);

    // Results file
    File resultsFile;
    try {
      resultsFile = File.createTempFile("results", ".json");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String resultsFileAbsolutePath = resultsFile.getAbsolutePath();
    logger.info("Results file path:" + resultsFileAbsolutePath);

    File scanDir;
    try {
      scanDir = ZipExtractor.extract(targetZip);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    String[] args = {
        "-r", resultsFileAbsolutePath,
        "-t", userParameters.stage,
        "-i", userParameters.applicationId,
        "-s", iqServerUrl,
        "-a", iqServerCredentials,
        scanDir.getAbsolutePath()
    };

    logger.info("Calling PolicyEvaluatorCli.main(args)");
    try {
      // apparently we can't do this and we need to call in a separate process
      // PolicyEvaluatorCli.main(args);

      // nexus-iq-cli.jar is in: /var/task/lib/nexus-iq-cli-1.164.0-01.jar
      List<String> command = new LinkedList<>();
      command.add("java");
      command.add("-jar");
      command.add("/var/task/lib/nexus-iq-cli-1.164.0-01.jar");
      command.addAll(Arrays.stream(args).collect(Collectors.toList()));

      logger.info("Calling new ProcessBuilder(command)");
      ProcessBuilder builder = new ProcessBuilder(command);

      logger.info("Calling builder.inheritIO().start()");
      Process process = builder.inheritIO().start();
      logger.info("Called builder.inheritIO().start()");

      logger.info("Calling process.waitFor()");
      process.waitFor();
      logger.info("Called process.waitFor()");
    }
    catch (Exception e)
    {
      logger.info("Why does main throw exception when policy violation fails..");
      logger.info("I can read it from results file myself..");
      logger.info("Does not make sense to me...");
    }
    // continue even if we have policyAction:fail
    // I will make the pipeline fail by calling another method

    logger.info("Called PolicyEvaluatorCli.main(args)");

    StringBuilder stringBuilder = new StringBuilder();
    logger.info("Printing results file..");
    // Print results file
    Scanner scanner = null;
    try {
      scanner = new Scanner(resultsFile);
      while (scanner.hasNext()) {
        stringBuilder.append(scanner.next());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    String resultsJson = stringBuilder.toString();
    logger.info(resultsJson);
    logger.info("Printed resultsJson..");

    ResultDto resultDto = new Gson().fromJson(resultsJson, ResultDto.class);
    logger.info("Printing resultDto");
    logger.info("" + resultDto);

    try {
      // delete exploded zip directory
      ZipExtractor.deleteDirectoryWithContent(scanDir);
    }
    catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    // This is where we want to set the job results
    String jobId = (String) codePipelineJob.get("id");
    logger.info("job id is: {}", jobId);

    if ("Failure".equalsIgnoreCase(resultDto.policyAction)) {
      PutJobFailureResultRequest putJobFailureResultRequest = new PutJobFailureResultRequest();
      putJobFailureResultRequest.setJobId(jobId);

      FailureDetails failureDetails = new FailureDetails();
      failureDetails.setType(FailureType.JobFailed);
      failureDetails.setMessage("Failed due to policy violations");
      putJobFailureResultRequest.setFailureDetails(failureDetails);

      AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
      awsCodePipeline.putJobFailureResult(putJobFailureResultRequest);
    }
    else {
      PutJobSuccessResultRequest jobSuccessResultRequest = new PutJobSuccessResultRequest();
      jobSuccessResultRequest.setJobId(jobId);
      AWSCodePipeline awsCodePipeline = AWSCodePipelineClientBuilder.defaultClient();
      awsCodePipeline.putJobSuccessResult(jobSuccessResultRequest);
    }

    logger.info("Returning");
    return "SUCCESS";
  }

  private InputStream getObject(AmazonS3 s3Client, String bucket, String key) {
    GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
    S3Object object = s3Client.getObject(getObjectRequest);
    return object.getObjectContent();
  }

  public String getSecret() {
    String secretName = System.getenv("IQ_SERVER_CREDENTIALS");

    // Create a Secrets Manager client
    SecretsManagerClient client = SecretsManagerClient.builder()
        .region(Region.US_EAST_1)
        .build();

    GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();

    GetSecretValueResponse getSecretValueResponse;

    try {
      getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
    } catch (Exception e) {
      // For a list of exceptions thrown, see
      // https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
      throw e;
    }

    String secret = getSecretValueResponse.secretString();
    return secret;
  }

  static class ResultDto {

    public String policyAction;

    @Override
    public String toString() {
      return "ResultDto{" +
          "policyAction='" + policyAction + '\'' +
          '}';
    }
  }
}
