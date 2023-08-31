package com.sonatype;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sonatype.dto.CodePipelineJobDto;
import com.sonatype.dto.EvaluationServiceResultDto;
import com.sonatype.dto.UserParameters;
import com.sonatype.service.AwsService;
import com.sonatype.service.EvaluationService;
import com.sonatype.service.InputHandlerService;
import com.sonatype.service.IqServerService;
import com.sonatype.util.ZipExtractor;
import java.io.File;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/s3-java/src/main/java/example/Handler.java
// https://docs.aws.amazon.com/AmazonS3/latest/userguide/AuthUsingTempSessionToken.html
@SuppressWarnings("FieldCanBeLocal")
public class LambdaHandler
    implements RequestHandler<Object, Void> {

  private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);

  private InputHandlerService inputHandlerService;

  private AwsService awsService;

  private IqServerService iqServerClientService;

  private EvaluationService evaluationService;

  private UserParameters userParameters;

  private String iqServerCredentials;

  @Override
  public Void handleRequest(Object obj, Context context) {
    logger.info("" + obj);

    // Setup
    inputHandlerService = new InputHandlerService();
    CodePipelineJobDto codePipelineJobDto = inputHandlerService.parseCodePipelineJobDto(obj);
    awsService = new AwsService(codePipelineJobDto);
    iqServerCredentials = awsService.getIqServerCredentials();
    iqServerClientService = new IqServerService(iqServerCredentials);
    userParameters = codePipelineJobDto.userParameters;
    evaluationService = new EvaluationService(iqServerClientService, userParameters);

    doHandleRequest();

    // Cleanup
    try {
      ZipExtractor.deleteDirectoryWithContent(awsService.getScanDir());
    } catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    return null;
  }

  public void doHandleRequest() {
    // Check if required data is provided
    if (StringUtils.isBlank(iqServerCredentials)) {
      awsService.fail("IQ Server credentials not provided.");
      return;
    }

    if (userParameters.applicationId == null) {
      awsService.fail("Application ID is required.");
      return;
    }

    // Run eval
    File scanDir = awsService.getScanDir();
    EvaluationServiceResultDto evaluationServiceResultDto = evaluationService.runEvaluation(scanDir);

    // Set outcome in aws based on the evaluation results
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
