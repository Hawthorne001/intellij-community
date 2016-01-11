/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.ProcessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class OSProcessManagerImpl extends OSProcessManager {
  @Override
  public boolean killProcessTree(@NotNull Process process) {
    return ProcessUtils.killProcessTree(process);
  }

  public static void killProcess(@NotNull Process process) {
    ProcessUtils.killProcess(process);
  }

  public static int getProcessID(@NotNull Process process) {
    return ProcessUtils.getProcessID(process);
  }

  @Override
  @Nullable
  public List<String> getCommandLinesOfRunningProcesses() {
    return ProcessUtils.getCommandLinesOfRunningProcesses();
  }
}
