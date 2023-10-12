package com.sonatype.service;

import com.sonatype.dto.EvaluationServiceResultDto;
import com.sonatype.dto.UserParameters;
import com.sonatype.nexus.api.iq.ApplicationPolicyEvaluation;
import com.sonatype.nexus.api.iq.scan.ScanResult;
import com.sonatype.nexus.git.utils.repository.RepositoryUrlFinderBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationService {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

  private final IqServerService iqServerService;

  private final UserParameters userParameters;

  private final List<String> DEFAULT_SCAN_PATTERN
      = Arrays.asList("**/*.jar", "**/*.war", "**/*.ear", "**/*.zip", "**/*.tar.gz");

  public EvaluationService(IqServerService iqServerService, UserParameters userParameters) {
    this.iqServerService = iqServerService;
    this.userParameters = userParameters;
  }


  // for debug purposes only
  private static void getFilesRecursive(File path) {
    for (File file : path.listFiles()) {
      if (file.isDirectory()) {
        getFilesRecursive(file);
      } else {
        logger.info(file.getAbsolutePath() + " " + file.getName());
      }
    }
  }

  public EvaluationServiceResultDto runEvaluation(File scanDir) {
    logger.info("scanDir: {}", scanDir);
    // for debug purposes only can and will be removed later TODO
    getFilesRecursive(scanDir);

    String applicationId = userParameters.applicationId;
    String organizationId = userParameters.organizationId;
    String stage = userParameters.stage;
    EvaluationServiceResultDto evaluationServiceResultDto = new EvaluationServiceResultDto();

    Boolean isServerVersionValid = iqServerService.validateServerVersion();
    if (isServerVersionValid == null) {
      evaluationServiceResultDto.hasNetworkError = true;
      return evaluationServiceResultDto;
    }

    if (!isServerVersionValid) {
      evaluationServiceResultDto.iqServerVersionValidationFailed = true;
      return evaluationServiceResultDto;
    }

    Set<String> licensedFeatures = iqServerService.getLicensedFeatures();
    if (licensedFeatures.isEmpty()) {
      evaluationServiceResultDto.isLicensedFeaturesEmpty = true;
      return evaluationServiceResultDto;
    }

    boolean applicationVerified = iqServerService.verifyOrCreateApplication(applicationId, organizationId);
    if (!applicationVerified) {
      evaluationServiceResultDto.applicationVerificationFailed = true;
      return evaluationServiceResultDto;
    }

    Optional<String> repositoryUrlOptional = new RepositoryUrlFinderBuilder()
        .withEnvironmentVariableDefault()
        .withGitRepoAtPath(scanDir.getAbsolutePath() + "/.git")
        .withLogger(logger)
        .build()
        .tryGetRepositoryUrl();

    if (repositoryUrlOptional.isPresent()) {
      String repositoryUrl = repositoryUrlOptional.get();
      iqServerService.addOrUpdateSourceControl(applicationId, repositoryUrl);
      logger.info("Repository URL found: {}.", repositoryUrl);
    } else {
      logger.info("Repository URL not found.");
    }

      List<File> scanTargets = getScanTargets(scanDir);
    ScanResult scanresult = iqServerService.scan(applicationId, scanTargets, scanDir, licensedFeatures);
    evaluationServiceResultDto.scanResult = scanresult;
    logger.info("Scan result ready.. Calling internalIqClient.evaluateApplication..");

    ApplicationPolicyEvaluation applicationPolicyEvaluation
        = iqServerService.evaluate(applicationId, stage, scanresult);
    evaluationServiceResultDto.applicationPolicyEvaluation = applicationPolicyEvaluation;
    evaluationServiceResultDto.hasPolicyViolationsWithFailAction
        = iqServerService.hasPolicyViolationsWithFailAction(applicationPolicyEvaluation);

    return evaluationServiceResultDto;
  }

  // Stolen from RemoteScanner.groovy in nexus-platform-plugin
  public List<File> getScanTargets(File workDir) {
    List<String> scanPatterns = null;
    if (userParameters.scanTargets != null) {
      String[] split = userParameters.scanTargets.split(",");
      scanPatterns = Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

    DirectoryScanner directoryScanner = new DirectoryScanner();
    List<String> normalizedScanPatterns = scanPatterns == null ? DEFAULT_SCAN_PATTERN : scanPatterns;
    directoryScanner.setBasedir(workDir);
    directoryScanner.setIncludes(normalizedScanPatterns.toArray(new String[0]));
    directoryScanner.addDefaultExcludes();
    directoryScanner.scan();

    String[] includedDirectories = directoryScanner.getIncludedDirectories();
    String[] includedFiles = directoryScanner.getIncludedFiles();

    List<String> allIncludedFiles = new ArrayList<>();
    allIncludedFiles.addAll(Arrays.stream(includedDirectories).collect(Collectors.toList()));
    allIncludedFiles.addAll(Arrays.stream(includedFiles).collect(Collectors.toList()));

    List<File> scanTargetFiles = allIncludedFiles.stream().map(s -> new File(workDir, s))
        .collect(Collectors.toList());

    return scanTargetFiles;
  }
}
