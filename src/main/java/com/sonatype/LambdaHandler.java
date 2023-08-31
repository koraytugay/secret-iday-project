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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/s3-java/src/main/java/example/Handler.java
// https://docs.aws.amazon.com/AmazonS3/latest/userguide/AuthUsingTempSessionToken.html
public class LambdaHandler
    implements RequestHandler<Object, Void> {

  private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);

  @Override
  public Void handleRequest(Object obj, Context context) {
    logger.info("" + obj);

    // Setup
    InputHandlerService inputHandlerService = new InputHandlerService();
    CodePipelineJobDto codePipelineJobDto = inputHandlerService.parseCodePipelineJobDto(obj);

    logger.info("codePipelineJobDto: {}", codePipelineJobDto);
    AwsService awsService = new AwsService(codePipelineJobDto);
    String iqServerCredentials = awsService.getIqServerCredentials();
    IqServerService iqServerClientService = new IqServerService(iqServerCredentials);

    UserParameters userParameters = codePipelineJobDto.userParameters;
    EvaluationService evaluationService = new EvaluationService(iqServerClientService, userParameters);

    // Run eval
    File scanDir = awsService.getScanDir();
    EvaluationServiceResultDto evaluationServiceResultDto = evaluationService.runEvaluation(scanDir);

    // Set outcome in aws
    if (evaluationServiceResultDto.applicationVerificationFailed) {
      awsService.failForApplicationValidation();
    } else if (evaluationServiceResultDto.isLicensedFeaturesEmpty) {
      awsService.failForLicenseFeatures();
    } else {
      logger.info("applicationPolicyEvaluation: {}", evaluationServiceResultDto.applicationPolicyEvaluation);
      awsService.setResults(evaluationServiceResultDto.applicationPolicyEvaluation);
    }

    // Cleanup
    try {
      ZipExtractor.deleteDirectoryWithContent(scanDir);
    } catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    return null;
  }
}
