package com.sonatype;

import com.sonatype.LambdaHandler.UserParameters;
import com.sonatype.nexus.api.common.Authentication;
import com.sonatype.nexus.api.common.ServerConfig;
import com.sonatype.nexus.api.exception.IqClientException;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.ProprietaryConfig;
import com.sonatype.nexus.api.iq.internal.InternalIqClient;
import com.sonatype.nexus.api.iq.internal.InternalIqClientBuilder;
import com.sonatype.nexus.api.iq.scan.ScanResult;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationService {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

  private AwsService awsService;
  private UserParameters userParameters;
  private File scanDir;

  public ApplicationPolicyEvaluation eval() {
    String iqServerCredentials = awsService.getIqServerCredentials();

    String iqServerUrl = System.getenv("IQ_SERVER_URL");
    logger.info("iq server url is: {}", iqServerUrl);

    URI uri = URI.create(iqServerUrl);
    logger.info("URI created: {}", uri);

    String username = iqServerCredentials.split(":")[0];
    String password = iqServerCredentials.split(":")[1];

    logger.info("username is:" + username);
    logger.info("password is:" + password);

    Authentication authentication = new Authentication(username, password.toCharArray());
    ServerConfig serverConfig = new ServerConfig(uri, authentication);
    InternalIqClientBuilder internalIqClientBuilder = InternalIqClientBuilder.create();
    InternalIqClient internalIqClient = internalIqClientBuilder
        .withServerConfig(serverConfig)
        .withLogger(logger)
        .build();
    logger.info("Created internalIq client..");

    ScanResult scanResult = null;
    try {
      scanResult = internalIqClient.scan(userParameters.applicationId,
          new ProprietaryConfig(new ArrayList<>(), new ArrayList<>()),
          new Properties(),
          Arrays.asList(scanDir));
    } catch (Exception e) {
      e.printStackTrace();
      logger.info("Failed with: {}", e.getMessage());
    }
    logger.info("Scan result ready.. Calling internalIqClient.evaluateApplication..");

    ApplicationPolicyEvaluation applicationPolicyEvaluation;
    try {
      applicationPolicyEvaluation = internalIqClient.evaluateApplication(
          userParameters.applicationId, userParameters.stage, scanResult);
    } catch (IqClientException e) {
      logger.info("evaluateApplication failed with: " , e.getCause());
      throw new RuntimeException(e);
    }

    return applicationPolicyEvaluation;
  }

  public void setAwsService(AwsService awsService) {
    this.awsService = awsService;
  }

  public void setUserParameters(UserParameters userParameters) {
    this.userParameters = userParameters;
  }

  public void setScanDir(File scanDir) {
    this.scanDir = scanDir;
  }
}
