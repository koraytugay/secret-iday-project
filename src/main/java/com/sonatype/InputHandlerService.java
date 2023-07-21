package com.sonatype;

import com.google.gson.Gson;
import com.sonatype.LambdaHandler.UserParameters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class InputHandlerService {

  public CodePipelineJobDto parseCodePipelineJobDto(Object obj) {
    CodePipelineJobDto codePipelineJobDto = new CodePipelineJobDto();
    LinkedHashMap<Object, Object> input = (LinkedHashMap<Object, Object>) obj;
    Set<Entry<Object, Object>> entries = input.entrySet();
    Entry<Object, Object> next = entries.iterator().next();

    LinkedHashMap<String, Object> codePipelineJob = (LinkedHashMap<String, Object>) next.getValue();
    codePipelineJobDto.id = (String) codePipelineJob.get("id");

    LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) codePipelineJob.get("data");

    Object inputArtifacts = data.get("inputArtifacts");
    List<Object> inputArtifactsList = (List<Object>) inputArtifacts;
    LinkedHashMap<Object, Object> inputArtifact = (LinkedHashMap<Object, Object>) inputArtifactsList.get(0);
    LinkedHashMap<Object, Object> location = (LinkedHashMap<Object, Object>) inputArtifact.get("location");
    LinkedHashMap<Object, Object> s3Location = (LinkedHashMap<Object, Object>) location.get("s3Location");
    codePipelineJobDto.srcBucket = (String) s3Location.get("bucketName");
    codePipelineJobDto.srcKey = (String) s3Location.get("objectKey");

    LinkedHashMap<Object, Object> artifactCredentials = (LinkedHashMap<Object, Object>) data.get("artifactCredentials");
    codePipelineJobDto.accessKeyId = (String) artifactCredentials.get("accessKeyId");
    codePipelineJobDto.secretAccessKey = (String) artifactCredentials.get("secretAccessKey");
    codePipelineJobDto.sessionToken = (String) artifactCredentials.get("sessionToken");

    LinkedHashMap<String, Object> actionConfiguration = (LinkedHashMap<String, Object>) data.get("actionConfiguration");
    LinkedHashMap<String, Object> configuration = (LinkedHashMap<String, Object>) actionConfiguration.get("configuration");
    String userParametersJson = (String) configuration.get("UserParameters");
    codePipelineJobDto.userParameters = new Gson().fromJson(userParametersJson, UserParameters.class);

    return codePipelineJobDto;
  }
}
