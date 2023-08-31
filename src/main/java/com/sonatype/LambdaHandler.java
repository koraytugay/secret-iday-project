package com.sonatype;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sonatype.dto.CodePipelineJobDto;
import com.sonatype.dto.UserParameters;
import com.sonatype.service.AwsCodePipelineService;
import com.sonatype.service.AwsService;
import com.sonatype.service.EvaluationService;
import com.sonatype.service.InputHandlerService;
import com.sonatype.service.IqServerService;
import com.sonatype.util.ZipExtractor;
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
    CodePipelineJobDto codePipelineJobDto = new InputHandlerService().parseCodePipelineJobDto(obj);
    AwsService awsService = new AwsService(codePipelineJobDto);
    String iqServerCredentials = awsService.getIqServerCredentials();
    IqServerService iqServerClientService = new IqServerService(iqServerCredentials);
    UserParameters userParameters = codePipelineJobDto.userParameters;
    EvaluationService evaluationService = new EvaluationService(iqServerClientService, userParameters);
    AwsCodePipelineService awsCodePipelineService = new AwsCodePipelineService(awsService, evaluationService, userParameters, iqServerCredentials);

    awsCodePipelineService.handleRequest();

    // Cleanup
    try {
      ZipExtractor.deleteDirectoryWithContent(awsService.getScanDir());
    } catch (IOException e) {
      logger.info("Could not delete extracted zip files");
    }

    return null;
  }
}
