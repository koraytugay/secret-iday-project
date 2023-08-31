package com.sonatype.dto;

import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;

public class EvaluationServiceResultDto {

  public ApplicationPolicyEvaluation applicationPolicyEvaluation;

  public boolean applicationVerificationFailed;
}
