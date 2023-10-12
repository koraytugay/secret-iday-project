package com.sonatype.service;

import com.sonatype.dto.EvaluationServiceResultDto;
import com.sonatype.dto.UserParameters;
import java.io.File;
import org.apache.commons.lang3.StringUtils;

public class AwsCodePipelineService {

  private final AwsService awsService;
  private final EvaluationService evaluationService;
  private final UserParameters userParameters;
  private final String iqServerUsername;
  private final String iqServerCredentials;

  public AwsCodePipelineService(
      AwsService awsService,
      EvaluationService evaluationService,
      UserParameters userParameters,
      String iqServerUsername,
      String iqServerCredentials) {
    this.awsService = awsService;
    this.evaluationService = evaluationService;
    this.userParameters = userParameters;
    this.iqServerUsername = iqServerUsername;
    this.iqServerCredentials = iqServerCredentials;
  }


  public void handleRequest() {
    // Check if required data is provided
    if (StringUtils.isBlank(iqServerUsername) || StringUtils.isBlank(iqServerCredentials)) {
      awsService.fail("IQ Server credentials not provided.");
      return;
    }

    if (userParameters.applicationId == null) {
      awsService.fail("Application ID is required.");
      return;
    }

    if (userParameters.stage == null) {
      awsService.fail("Stage is required.");
      return;
    }

    // Run eval
    File scanDir = awsService.getScanDir();
    EvaluationServiceResultDto evaluationServiceResultDto = evaluationService.runEvaluation(scanDir);

    // Set outcome in aws based on the evaluation results
    if (evaluationServiceResultDto.hasNetworkError && userParameters.failBuildOnNetworkErrors) {
      awsService.fail("Failed due to IQ Server not being reachable.");
      return;
    }

    // At this point `failBuildOnNetworkErrors` is false, so even though IQ is not reachable,
    // we set the outcome to success..
    if (evaluationServiceResultDto.hasNetworkError) {
      awsService.success();
      return;
    }

    if (evaluationServiceResultDto.iqServerVersionValidationFailed) {
      awsService.fail("IQ Server being used must be version 1.60 or later.");
      return;
    }

    if (evaluationServiceResultDto.hasScanningErrors() && userParameters.failBuildOnScanningErrors) {
      awsService.fail("Failed due to scanning errors.");
      return;
    }

    if (evaluationServiceResultDto.applicationVerificationFailed) {
      awsService.fail("Could not verify application: " + userParameters.applicationId);
      return;
    }

    if (evaluationServiceResultDto.isLicensedFeaturesEmpty) {
      awsService.fail("Failed to receive license features.");
      return;
    }

    if (evaluationServiceResultDto.hasPolicyViolationsWithFailAction){
      awsService.fail("Failed due to policy violations.");
      return;
    }

    awsService.success();
  }

}
