package com.sonatype.service;

import com.sonatype.dto.EvaluationServiceResultDto;
import com.sonatype.dto.UserParameters;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.scan.ScanResult;
import java.io.File;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationService {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

  private final IqServerService iqServerService;

  private final UserParameters userParameters;

  public EvaluationService(IqServerService iqServerService, UserParameters userParameters) {
    this.iqServerService = iqServerService;
    this.userParameters = userParameters;
  }

  public EvaluationServiceResultDto runEvaluation(File scanDir) {
    logger.info("scanDir: {}", scanDir);

    String applicationId = userParameters.applicationId;
    String organizationId = userParameters.organizationId;
    String stage = userParameters.stage;
    EvaluationServiceResultDto evaluationServiceResultDto = new EvaluationServiceResultDto();

    Set<String> licensedFeatures = iqServerService.getLicensedFeatures();
    if (licensedFeatures.isEmpty()) {
      evaluationServiceResultDto.isLicensedFeaturesEmpty = true;
      return evaluationServiceResultDto;
    }

    // todo: support: https://help.sonatype.com/iqserver/integrations/nexus-iq-cli#NexusIQCLI-Parameters
    boolean applicationVerified = iqServerService.verifyOrCreateApplication(applicationId, organizationId);
    if (!applicationVerified) {
      evaluationServiceResultDto.applicationVerificationFailed = true;
      return evaluationServiceResultDto;
    }

    ScanResult scanresult = iqServerService.scan(applicationId, scanDir, licensedFeatures);
    evaluationServiceResultDto.scanResult = scanresult;
    logger.info("Scan result ready.. Calling internalIqClient.evaluateApplication..");

    ApplicationPolicyEvaluation applicationPolicyEvaluation = iqServerService.evaluate(applicationId, stage, scanresult);
    evaluationServiceResultDto.applicationPolicyEvaluation = applicationPolicyEvaluation;
    evaluationServiceResultDto.hasPolicyViolationsWithFailAction
        = iqServerService.hasPolicyViolationsWithFailAction(applicationPolicyEvaluation);

    return evaluationServiceResultDto;
  }
}
