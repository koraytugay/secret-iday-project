package com.sonatype;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
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

    // Services
    InputHandlerService inputHandlerService = new InputHandlerService();
    AwsService awsService = new AwsService();
    EvaluationService evaluationService = new EvaluationService();

    // Data we need
    CodePipelineJobDto codePipelineJobDto = inputHandlerService.parseCodePipelineJobDto(obj);

    logger.info("codePipelineJobDto: {}", codePipelineJobDto);

    File scanDir = awsService.getScanDir(codePipelineJobDto);
    String iqServerCredentials = awsService.getIqServerCredentials();

    // Run eval
    ApplicationPolicyEvaluation applicationPolicyEvaluation
        = evaluationService.runEvaluation(iqServerCredentials, codePipelineJobDto, scanDir);

    logger.info("applicationPolicyEvaluation: {}", applicationPolicyEvaluation);

    // Cleanup
    try {
      ZipExtractor.deleteDirectoryWithContent(scanDir);
    } catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    // Set results on the job
    awsService.setResults(codePipelineJobDto.id, applicationPolicyEvaluation);

    return null;
  }
}
