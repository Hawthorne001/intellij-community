/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.internal.backRefCollector;

import com.intellij.util.Consumer;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.backwardRefs.BackwardReferenceIndexUtil;
import org.jetbrains.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.backwardRefs.JavacReferenceIndexWriter;
import org.jetbrains.backwardRefs.javac.ast.JavacReferenceIndexListener;
import org.jetbrains.backwardRefs.javac.ast.api.JavacFileData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.ArrayDeque;
import java.util.Queue;

public class ReferenceIndexJavacPlugin implements Plugin {
  public static final String PLUGIN_NAME = "ReferenceIndexJavacPlugin";
  public static final String FORK_ARG = "fork";
  public static final String INDEX_PATH_ARG = "index.path=";

  @Override
  public String getName() {
    return PLUGIN_NAME;
  }

  @Override
  public void init(JavacTask task, String... args) {
    File dir = getIndexDir(args);
    if (dir == null) return;

    JavacReferenceIndexWriter writer = new JavacReferenceIndexWriter(new CompilerBackwardReferenceIndex(dir, false) {
      @NotNull
      @Override
      protected RuntimeException createBuildDataCorruptedException(IOException cause) {
        return new RuntimeException(cause);
      }
    });

    JavacFileDataStream stream;
    try {
      stream = new JavacFileDataStream(writer, dir);
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    task.addTaskListener(new JavacReferenceIndexListener(false, new Consumer<JavacFileData>() {
      @Override
      public void consume(JavacFileData data) {
        stream.add(data);
      }
    }, task, true));

    boolean[] closed = {false};
    task.addTaskListener(new TaskListener() {
      @Override
      public void started(TaskEvent e) {
      }

      @Override
      public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.GENERATE) {
          if (!closed[0]) {
            try {
              stream.close();
            } finally {
              closed[0] = true;
            }
          }
        }
      }
    });
  }

  @Nullable
  private static File getIndexDir(String[] args) {
    for (String arg : args) {
      if (arg.startsWith(INDEX_PATH_ARG)) {
        return new File(arg.substring(INDEX_PATH_ARG.length()));
      }
    }
    return null;
  }

  private static class JavacFileDataStream {
    private final Queue<JavacFileData> myQueue = new ArrayDeque<>();
    private final JavacReferenceIndexWriter myWriter;
    private final ReferenceIndexFileLock myFileLock;

    private JavacFileDataStream(JavacReferenceIndexWriter writer,
                                File indexDir) throws FileNotFoundException {
      myWriter = writer;
      myFileLock = new ReferenceIndexFileLock(indexDir);
    }

    public void add(JavacFileData data) {
      myQueue.add(data);
      if (myQueue.size() > 100) {
        try {
          flush();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    public void close() {
      try {
        flush();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myWriter.close();
    }

    private void flush() throws IOException {
      FileLock lock = myFileLock.lock();
      try {
        //TODO
        while (!myQueue.isEmpty()) {
          JavacFileData data = myQueue.poll();
          BackwardReferenceIndexUtil.registerFile(data.getFilePath(),
                                                  data.getRefs(),
                                                  data.getDefs(),
                                                  myWriter);
        }

        myWriter.flush();
      } finally {
        lock.release();
      }
    }
  }
}
