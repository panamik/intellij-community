/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 24.05.13 12:20
 */
@Tag("ExternalSystemSettings")
public class ExternalSystemTaskExecutionSettings implements Cloneable {

  @NotNull @NonNls public static final String TAG_NAME = "ExternalSystemSettings";

  private List<String> myTaskNames = ContainerUtilRt.newArrayList();

  private String myExternalSystemIdString;
  private String myExternalProjectPath;
  private String myVmOptions;

  public String getExternalSystemIdString() {
    return myExternalSystemIdString;
  }

  public ProjectSystemId getExternalSystemId() {
    return new ProjectSystemId(myExternalSystemIdString);
  }

  public void setExternalSystemIdString(String externalSystemIdString) {
    myExternalSystemIdString = externalSystemIdString;
  }

  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  public void setExternalProjectPath(String externalProjectPath) {
    myExternalProjectPath = externalProjectPath;
  }

  public String getVmOptions() {
    return myVmOptions;
  }

  public void setVmOptions(String vmOptions) {
    myVmOptions = vmOptions;
  }

  public List<String> getTaskNames() {
    return myTaskNames;
  }

  public void setTaskNames(List<String> taskNames) {
    myTaskNames = taskNames;
  }

  @Override
  public ExternalSystemTaskExecutionSettings clone() {
    ExternalSystemTaskExecutionSettings result = new ExternalSystemTaskExecutionSettings();
    result.setExternalSystemIdString(getExternalSystemIdString());
    result.setExternalProjectPath(getExternalProjectPath());
    result.setVmOptions(getVmOptions());
    result.setTaskNames(ContainerUtilRt.newArrayList(getTaskNames()));
    return result;
  }
}
