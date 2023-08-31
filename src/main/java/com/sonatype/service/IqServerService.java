package com.sonatype.service;

import com.sonatype.nexus.api.common.Authentication;
import com.sonatype.nexus.api.common.ServerConfig;
import com.sonatype.nexus.api.exception.IqClientException;
import com.sonatype.nexus.api.iq.Action;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.PolicyAlert;
import com.sonatype.nexus.api.iq.ProprietaryConfig;
import com.sonatype.nexus.api.iq.internal.InternalIqClient;
import com.sonatype.nexus.api.iq.internal.InternalIqClientBuilder;
import com.sonatype.nexus.api.iq.scan.ScanResult;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IqServerService {

  private static final Logger logger = LoggerFactory.getLogger(IqServerService.class);

  private final InternalIqClient internalIqClient;

  public IqServerService(String iqServerCredentials) {
    String username = iqServerCredentials.split(":")[0];
    String password = iqServerCredentials.split(":")[1];

    logger.info("username is:" + username);
    logger.info("password is:" + password);

    String iqServerUrl = System.getenv("IQ_SERVER_URL");
    logger.info("iq server url is: {}", iqServerUrl);

    URI uri = URI.create(iqServerUrl);
    logger.info("URI created: {}", uri);

    Authentication authentication = new Authentication(username, password.toCharArray());
    ServerConfig serverConfig = new ServerConfig(uri, authentication);
    InternalIqClientBuilder internalIqClientBuilder = InternalIqClientBuilder.create();

    internalIqClient = internalIqClientBuilder
        .withServerConfig(serverConfig)
        .withLogger(logger)
        .build();

    logger.info("Created internalIqClient: {}", internalIqClient);
  }

  public Set<String> getLicensedFeatures() {
    try {
      Set<String> licensedFeatures = internalIqClient.getLicensedFeatures();
      logger.info("Retrieved licensed features: {}", licensedFeatures);
      return licensedFeatures;
    } catch (IOException e) {
      logger.info("Failed to get licensed features.", e);
      return new HashSet<>();
    }
  }

  public ScanResult scan(String applicationId, File scanDir, Set<String> licensedFeatures) {
    // todo: fail or not, ignoreSystemErrors, failOnPolicyWarnings, ignoreScanningErrors
    ScanResult scanResult = null;

    try {
      scanResult = internalIqClient.scan(
          applicationId,
          new ProprietaryConfig(new ArrayList<>(), new ArrayList<>()),  // todo: low priority
          new Properties(),       // configuration for the scan  // todo: to the jvm
          Arrays.asList(scanDir), // targets (todo: resolve before passing in)
          scanDir,                // base directory
          new HashMap<>(),        // env vars  todo: same with properties
          licensedFeatures,
          new ArrayList<>()       // modules  todo: controlled by iqModuleExcludes
      );
    } catch (Exception e) {
      logger.info("Scan failed with: {}", e.getMessage());
    }

    return scanResult;
  }

  public boolean verifyOrCreateApplication(String applicationId, String organizationId) {
    try {
      if (organizationId == null) {
        return internalIqClient.verifyOrCreateApplication(applicationId);
      } else {
        return internalIqClient.verifyOrCreateApplication(applicationId, organizationId);
      }
    } catch (IqClientException e) {
      return false;
    }
  }

  public ApplicationPolicyEvaluation evaluate(String applicationId, String stage, ScanResult scanResult) {
    ApplicationPolicyEvaluation applicationPolicyEvaluation;
    try {
      applicationPolicyEvaluation = internalIqClient.evaluateApplication(
          applicationId, stage, scanResult);
    } catch (IqClientException e) {
      logger.info("evaluateApplication failed with: ", e.getCause());
      throw new RuntimeException(e);
    }

    return applicationPolicyEvaluation;
  }

  public boolean hasPolicyViolationsWithFailAction(ApplicationPolicyEvaluation applicationPolicyEvaluation) {
    List<PolicyAlert> policyAlerts = applicationPolicyEvaluation.getPolicyAlerts();
    for (PolicyAlert policyAlert : policyAlerts) {
      List<? extends Action> actions = policyAlert.getActions();
      for (Action action : actions) {
        if (Action.ID_FAIL.equals(action.getActionTypeId())) {
          return true;
        }
      }
      String policyAlertMessage = policyAlert.getTrigger().toString();
      policyAlertMessage = policyAlertMessage.replaceAll("\n", " ");
      logger.info(policyAlertMessage);
    }
    return false;
  }
}
