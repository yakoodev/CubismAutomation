package com.live2d.cubism.patch;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class CubismBootstrap {
  private CubismBootstrap() {}

  public static void bootstrap() {
    log("bootstrap begin");
    try {
      createMarkerFile();
      startExternalServer();
    } catch (Throwable ignored) {
      log("bootstrap error: " + ignored.getClass().getName() + ": " + ignored.getMessage());
      // fail-safe: bootstrap errors must never break Cubism startup
    }
  }

  private static void createMarkerFile() throws Exception {
    String userProfile = System.getenv("USERPROFILE");
    if (userProfile == null || userProfile.isEmpty()) {
      log("marker skipped: USERPROFILE is empty");
      return;
    }
    File marker = new File(userProfile + File.separator + "Desktop" + File.separator + "cubism_patched_ok.txt");
    boolean created = marker.createNewFile();
    log("marker " + (created ? "created" : "already exists") + ": " + marker.getAbsolutePath());
  }

  private static void startExternalServer() throws Exception {
    File jarFile = resolveServerJar();
    if (jarFile == null || !jarFile.isFile()) {
      log("server jar not found");
      return;
    }
    log("server jar: " + jarFile.getAbsolutePath());

    URL jarUrl = jarFile.toURI().toURL();
    ClassLoader parent = CubismBootstrap.class.getClassLoader();
    URLClassLoader loader = new URLClassLoader(new URL[] { jarUrl }, parent);
    Class<?> bootstrapClass = Class.forName("com.live2d.cubism.agent.ServerBootstrap", true, loader);
    Method start = bootstrapClass.getMethod("start");
    start.invoke(null);
    log("server bootstrap invoked");
  }

  private static File resolveServerJar() {
    String envPath = System.getenv("CUBISM_AGENT_SERVER_JAR");
    if (envPath != null && !envPath.isBlank()) {
      File fromEnv = new File(envPath);
      if (fromEnv.isFile()) {
        return fromEnv;
      }
    }

    File base = new File(System.getProperty("user.dir", "."));
    String[] candidates = new String[] {
      "cubism-agent-server.jar",
      "agent\\cubism-agent-server.jar",
      "plugins\\agent\\cubism-agent-server.jar",
      "lib\\agent\\cubism-agent-server.jar"
    };
    for (String rel : candidates) {
      File candidate = new File(base, rel);
      if (candidate.isFile()) {
        return candidate;
      }
    }
    return null;
  }

  private static void log(String msg) {
    try {
      Path path = resolveLogPath();
      Files.createDirectories(path.getParent());
      String line = Instant.now().toString() + " " + msg + System.lineSeparator();
      Files.write(path, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (Throwable ignored) {
      // logging is best-effort only
    }
  }

  private static Path resolveLogPath() {
    String envPath = System.getenv("CUBISM_AGENT_LOG");
    if (envPath != null && !envPath.isBlank()) {
      return new File(envPath).toPath();
    }
    String userProfile = System.getenv("USERPROFILE");
    if (userProfile != null && !userProfile.isBlank()) {
      return new File(userProfile + File.separator + "Desktop" + File.separator + "cubism_agent_loader.log").toPath();
    }
    return new File("cubism_agent_loader.log").toPath();
  }
}
