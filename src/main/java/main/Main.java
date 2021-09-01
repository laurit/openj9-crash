package main;

import agent.Agent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;

public class Main {
  public static void main(String... args) {
    new GenerateClasses().start();
    new LoadClasses().start();
  }

  static class GenerateClasses extends Thread {
    @Override
    public void run() {
      try {
        runInternal();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void runInternal() throws Exception {
      for (int i = 0; i < 1000; i++) {
        Map<String, byte[]> classnameToBytes = genClasses();
        Map<String, Class<?>> classMap = injectBootstrapClassLoader(classnameToBytes);
        // System.err.println(classMap);
      }
    }

    private AtomicInteger counter = new AtomicInteger();
    private Map<String, byte[]> genClasses() {
      Map<String, byte[]> result = new HashMap<>();

      String dotClassName = "test.Test" + counter.incrementAndGet();
      String slashClassName = dotClassName.replace('.', '/');
      ClassWriter cw = new ClassWriter(0);
      cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, slashClassName, null, "java/lang/Object", null);
      cw.visitEnd();
      result.put(dotClassName, cw.toByteArray());

      return result;
    }

    private Map<String, Class<?>> injectBootstrapClassLoader(Map<String, byte[]> classnameToBytes)
            throws IOException {
      // Mar 2020: Since we're proactively cleaning up tempDirs, we cannot share dirs per thread.
      // If this proves expensive, we could do a per-process tempDir with
      // a reference count -- but for now, starting simple.

      // Failures to create a tempDir are propagated as IOException and handled by transform
      File tempDir = createTempDir();
      try {
        return ClassInjector.UsingInstrumentation.of(
                        tempDir, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, Agent.instrumentation)
                .injectRaw(classnameToBytes);
      } finally {
        // Delete fails silently
        deleteTempDir(tempDir);
      }
    }

    private static File createTempDir() throws IOException {
      return Files.createTempDirectory("opentelemetry-temp-jars").toFile();
    }

    private static void deleteTempDir(File file) {
      // Not using Files.delete for deleting the directory because failures
      // create Exceptions which may prove expensive.  Instead using the
      // older File API which simply returns a boolean.
      boolean deleted = file.delete();
      if (!deleted) {
        file.deleteOnExit();
      }
    }
  }

  static class LoadClasses extends Thread {
    @Override
    public void run() {
      try {
        runInternal();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void runInternal() throws Exception {
      String javaHome = System.getProperty("java.home");

      try (var zipFile = new ZipFile(javaHome + "/jmods/java.base.jmod")) {
        for (var entries = zipFile.entries(); entries.hasMoreElements(); ) {
          // System.out.println(entries.nextElement());
          String entryName = entries.nextElement().getName();
          if (entryName.endsWith(".class") && entryName.startsWith("classes/") && !entryName.contains("module-info")
              && !entryName.contains("Trampoline") && !entryName.contains("MethodHandleImpl")) {
            String className = entryName.substring(8, entryName.length() - 6).replace('/', '.');
            // System.err.println(className);
            try {
              Class.forName(className);
            } catch (Throwable t) {
              System.err.println(className);
              t.printStackTrace();
            }
          }
        }
      }
    }
  }
}
