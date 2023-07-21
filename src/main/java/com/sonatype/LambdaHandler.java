package com.sonatype;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.PolicyAlert;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    AwsService awsService = new AwsService();

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
    LinkedHashMap<Object, Object> artifactCredentials = (LinkedHashMap<Object, Object>) data.get("artifactCredentials");

    String accessKeyId = (String) artifactCredentials.get("accessKeyId");
    String secretAccessKey = (String) artifactCredentials.get("secretAccessKey");
    String sessionToken = (String) artifactCredentials.get("sessionToken");

    File scanDir = awsService.getScanDir(srcBucket, srcKey, accessKeyId, secretAccessKey, sessionToken);

    LinkedHashMap<String, Object> actionConfiguration = (LinkedHashMap<String, Object>) data.get("actionConfiguration");
    LinkedHashMap<String, Object> configuration = (LinkedHashMap<String, Object>) actionConfiguration.get("configuration");

    String userParametersJson = (String) configuration.get("UserParameters");
    UserParameters userParameters = new Gson().fromJson(userParametersJson, UserParameters.class);

    EvaluationService evaluationService = new EvaluationService();
    evaluationService.setAwsService(awsService);
    evaluationService.setUserParameters(userParameters);
    evaluationService.setScanDir(scanDir);
    ApplicationPolicyEvaluation applicationPolicyEvaluation = evaluationService.eval();

    try {
      ZipExtractor.deleteDirectoryWithContent(scanDir);
    } catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    // This is where we want to set the job results
    String jobId = (String) codePipelineJob.get("id");
    List<PolicyAlert> policyAlerts = applicationPolicyEvaluation.getPolicyAlerts();
    logger.info("Policy alerts size: {}", policyAlerts.size());

    awsService.foo(jobId);

    return "SUCCESS";
  }
}
