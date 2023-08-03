package com.sonatype;

public class CodePipelineJobDto {

  public String id;
  public String srcBucket;
  public String srcKey;
  public String accessKeyId;
  public String secretAccessKey;
  public String sessionToken;
  public UserParameters userParameters;

  @Override
  public String toString() {
    return "CodePipelineJobDto{" +
        "id='" + id + '\'' +
        ", srcBucket='" + srcBucket + '\'' +
        ", srcKey='" + srcKey + '\'' +
        ", accessKeyId='" + accessKeyId + '\'' +
        ", secretAccessKey='" + secretAccessKey + '\'' +
        ", sessionToken='" + sessionToken + '\'' +
        ", userParameters=" + userParameters +
        '}';
  }
}
