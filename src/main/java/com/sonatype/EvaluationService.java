package com.sonatype;

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

  public ApplicationPolicyEvaluation runEvaluation(String iqServerCredentials, CodePipelineJobDto codePipelineJobDto, File scanDir) {
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

    logger.info("Created internalIqClient: {}", internalIqClient);
    logger.info("scanDir: {}", scanDir);

    ScanResult scanResult = null;
    try {
      scanResult = internalIqClient.scan(codePipelineJobDto.userParameters.applicationId,
          new ProprietaryConfig(new ArrayList<>(), new ArrayList<>()), new Properties(),
          Arrays.asList(scanDir));
    } catch (Exception e) {
      e.printStackTrace();
      logger.info("Failed with: {}", e.getMessage());
    }
    logger.info("Scan result ready.. Calling internalIqClient.evaluateApplication..");

    ApplicationPolicyEvaluation applicationPolicyEvaluation;
    try {
      applicationPolicyEvaluation = internalIqClient.evaluateApplication(
          codePipelineJobDto.userParameters.applicationId, codePipelineJobDto.userParameters.stage,
          scanResult);
    } catch (IqClientException e) {
      logger.info("evaluateApplication failed with: ", e.getCause());
      throw new RuntimeException(e);
    }

    return applicationPolicyEvaluation;
  }
}