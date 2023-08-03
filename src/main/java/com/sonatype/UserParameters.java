package com.sonatype;

public class UserParameters {
  public String stage;
  public String applicationId;

  @Override
  public String toString() {
    return "UserParameters{" +
        "stage='" + stage + '\'' +
        ", applicationId='" + applicationId + '\'' +
        '}';
  }
}
