package com.sonatype.dto;

public class UserParameters {
  public String stage;
  public String applicationId;
  public String organizationId;

  @Override
  public String toString() {
    return "UserParameters{" +
        "stage='" + stage + '\'' +
        ", applicationId='" + applicationId + '\'' +
        ", organizationId='" + organizationId + '\'' +
        '}';
  }
}
