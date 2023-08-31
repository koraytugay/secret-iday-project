package com.sonatype.dto;

import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.scan.ScanResult;

public class EvaluationServiceResultDto {

  public ApplicationPolicyEvaluation applicationPolicyEvaluation;

  public boolean applicationVerificationFailed;

  public boolean isLicensedFeaturesEmpty;

  public boolean hasPolicyViolationsWithFailAction;

  public ScanResult scanResult;

  public boolean hasScanningErrors() {
    return scanResult != null &&
        scanResult.getScan() != null &&
        scanResult.getScan().getSummary() != null &&
        scanResult.getScan().getSummary().getErrorCount() > 0;
  }
}
