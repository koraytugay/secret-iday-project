package com.sonatype;

import com.sonatype.LambdaHandler.UserParameters;

public class CodePipelineJobDto {

  public String id;
  public String srcBucket;
  public String srcKey;
  public String accessKeyId;
  public String secretAccessKey;
  public String sessionToken;
  public UserParameters userParameters;
}
