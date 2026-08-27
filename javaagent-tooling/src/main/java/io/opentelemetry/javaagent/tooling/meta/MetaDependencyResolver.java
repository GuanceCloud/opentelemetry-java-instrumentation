/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.FINE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

final class MetaDependencyResolver {

  private static final int MAX_ARCHIVE_ENTRIES = 100_000;
  private static final int MAX_POM_PROPERTIES = 256;
  private static final int MAX_POM_PROPERTIES_BYTES = 64 * 1024;
  private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
  private static final long MAX_HASH_BYTES = 256L * 1024 * 1024;
  private static final int MAX_METADATA_VALUE_LENGTH = 256;

  private static final Logger logger = Logger.getLogger(MetaDependencyResolver.class.getName());
  private static final Pattern JAR_FILE_PATTERN =
      Pattern.compile("^(.+?)(?:-([0-9][^-]+(?:-\\w+)?))?\\.jar$");
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  static List<MetaDependency> resolve(URL location) {
    try {
      JarMetadata metadata = read(location);
      if (metadata == null) {
        return emptyList();
      }
      List<MetaDependency> dependencies = fromPomProperties(metadata.pomProperties);
      if (!dependencies.isEmpty()) {
        return dependencies;
      }
      return singletonList(fromManifestAndFileName(metadata));
    } catch (IOException | RuntimeException | URISyntaxException e) {
      logger.log(FINE, "Unable to resolve Meta dependency from " + location, e);
      return emptyList();
    }
  }

  @Nullable
  private static JarMetadata read(URL location) throws IOException, URISyntaxException {
    if ("file".equals(location.getProtocol())) {
      File file = new File(location.toURI());
      if (file.isFile()) {
        return readJarFile(file);
      }
      return file.isDirectory() ? readExplodedDirectory(file) : null;
    }
    if (!"jar".equals(location.getProtocol())) {
      return null;
    }
    return readJarLocation(location.toExternalForm().substring("jar:".length()));
  }

  @Nullable
  private static JarMetadata readJarLocation(String value) throws IOException, URISyntaxException {
    String path = stripJarSuffix(value);
    if (path.startsWith("file:")) {
      int nestedSeparator = path.indexOf("!/");
      if (nestedSeparator < 0) {
        return readJarFile(new File(new URI(path)));
      }
      File outerJar = new File(new URI(path.substring(0, nestedSeparator)));
      String innerJar = path.substring(nestedSeparator + 2);
      return readNestedJar(outerJar, innerJar);
    }
    if (path.startsWith("nested:")) {
      path = path.substring("nested:".length());
      int nestedSeparator = path.indexOf("/!");
      if (nestedSeparator < 0) {
        return null;
      }
      File outerJar = fileFromPath(path.substring(0, nestedSeparator));
      String innerJar = path.substring(nestedSeparator + 2);
      return readNestedJar(outerJar, innerJar);
    }
    return null;
  }

  private static String stripJarSuffix(String value) {
    if (value.endsWith("!/")) {
      return value.substring(0, value.length() - 2);
    }
    if (value.endsWith("!")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static File fileFromPath(String path) throws URISyntaxException {
    return new File(new URI("file", null, path, null));
  }

  private static JarMetadata readJarFile(File file) throws IOException {
    try (JarFile jarFile = new JarFile(file, false)) {
      Map<String, Properties> pomProperties = new LinkedHashMap<>();
      Enumeration<JarEntry> entries = jarFile.entries();
      int entryCount = 0;
      while (entries.hasMoreElements()) {
        ensureNotInterrupted();
        JarEntry entry = entries.nextElement();
        if (++entryCount > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("JAR contains too many entries");
        }
        if (isPomProperties(entry)) {
          if (pomProperties.size() >= MAX_POM_PROPERTIES) {
            throw new IOException("JAR contains too many pom.properties entries");
          }
          try (InputStream inputStream = jarFile.getInputStream(entry)) {
            pomProperties.put(entry.getName(), readProperties(inputStream));
          }
        }
      }
      Attributes manifestAttributes = new Attributes();
      JarEntry manifestEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF");
      if (manifestEntry != null) {
        try (InputStream inputStream = jarFile.getInputStream(manifestEntry)) {
          manifestAttributes = readManifest(inputStream).getMainAttributes();
        }
      }
      return new JarMetadata(
          file.getName(), pomProperties, manifestAttributes, () -> new FileInputStream(file));
    }
  }

  @Nullable
  private static JarMetadata readExplodedDirectory(File root) throws IOException {
    File mavenDirectory = new File(root, "META-INF/maven");
    File[] groupDirectories = mavenDirectory.listFiles(File::isDirectory);
    if (groupDirectories == null) {
      return null;
    }

    Map<String, Properties> pomProperties = new LinkedHashMap<>();
    int visitedDirectories = 0;
    for (File groupDirectory : groupDirectories) {
      ensureNotInterrupted();
      if (++visitedDirectories > MAX_ARCHIVE_ENTRIES) {
        throw new IOException("Exploded application contains too many Maven metadata directories");
      }
      File[] artifactDirectories = groupDirectory.listFiles(File::isDirectory);
      if (artifactDirectories == null) {
        continue;
      }
      for (File artifactDirectory : artifactDirectories) {
        ensureNotInterrupted();
        if (++visitedDirectories > MAX_ARCHIVE_ENTRIES) {
          throw new IOException(
              "Exploded application contains too many Maven metadata directories");
        }
        File propertiesFile = new File(artifactDirectory, "pom.properties");
        if (!propertiesFile.isFile()) {
          continue;
        }
        if (pomProperties.size() >= MAX_POM_PROPERTIES) {
          throw new IOException("Exploded application contains too many pom.properties entries");
        }
        try (InputStream inputStream = new FileInputStream(propertiesFile)) {
          String relativeName = root.toURI().relativize(propertiesFile.toURI()).getPath();
          pomProperties.put(relativeName, readProperties(inputStream));
        }
      }
    }
    if (pomProperties.isEmpty()) {
      return null;
    }
    return new JarMetadata(
        root.getName(),
        pomProperties,
        new Attributes(),
        () -> new ByteArrayInputStream(new byte[0]));
  }

  @Nullable
  private static JarMetadata readNestedJar(File outerJar, String innerJar) throws IOException {
    try (JarFile jarFile = new JarFile(outerJar, false)) {
      JarEntry nestedEntry = jarFile.getJarEntry(innerJar);
      if (nestedEntry == null || nestedEntry.isDirectory()) {
        return null;
      }
      try (InputStream inputStream = jarFile.getInputStream(nestedEntry);
          ZipInputStream nestedInputStream = new ZipInputStream(inputStream)) {
        Map<String, Properties> pomProperties = new LinkedHashMap<>();
        Attributes manifestAttributes = new Attributes();
        ZipEntry entry;
        int entryCount = 0;
        while ((entry = nestedInputStream.getNextEntry()) != null) {
          ensureNotInterrupted();
          if (++entryCount > MAX_ARCHIVE_ENTRIES) {
            throw new IOException("Nested JAR contains too many entries");
          }
          if (isPomProperties(entry)) {
            if (pomProperties.size() >= MAX_POM_PROPERTIES) {
              throw new IOException("Nested JAR contains too many pom.properties entries");
            }
            pomProperties.put(entry.getName(), readProperties(nestedInputStream));
          } else if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
            manifestAttributes = readManifest(nestedInputStream).getMainAttributes();
          }
        }
        return new JarMetadata(
            new File(innerJar).getName(),
            pomProperties,
            manifestAttributes,
            () -> new NestedJarInputStream(outerJar, innerJar));
      }
    }
  }

  private static boolean isPomProperties(ZipEntry entry) {
    return !entry.isDirectory()
        && entry.getName().startsWith("META-INF/maven/")
        && entry.getName().endsWith("/pom.properties");
  }

  private static Properties readProperties(InputStream inputStream) throws IOException {
    byte[] content = readBounded(inputStream, MAX_POM_PROPERTIES_BYTES, "pom.properties");
    Properties properties = new Properties();
    properties.load(new ByteArrayInputStream(content));
    return properties;
  }

  private static Manifest readManifest(InputStream inputStream) throws IOException {
    return new Manifest(
        new ByteArrayInputStream(readBounded(inputStream, MAX_MANIFEST_BYTES, "JAR manifest")));
  }

  private static byte[] readBounded(InputStream inputStream, int maxBytes, String description)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[4_096];
    int totalBytes = 0;
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      ensureNotInterrupted();
      totalBytes += read;
      if (totalBytes > maxBytes) {
        throw new IOException(description + " is too large");
      }
      outputStream.write(buffer, 0, read);
    }
    return outputStream.toByteArray();
  }

  private static void ensureNotInterrupted() throws IOException {
    if (Thread.currentThread().isInterrupted()) {
      throw new IOException("Meta dependency resolution was interrupted");
    }
  }

  private static List<MetaDependency> fromPomProperties(Map<String, Properties> pomProperties) {
    Map<String, MetaDependency> dependencies = new LinkedHashMap<>();
    for (Properties properties : pomProperties.values()) {
      String groupId = metadataValue(properties.getProperty("groupId"));
      String artifactId = metadataValue(properties.getProperty("artifactId"));
      String version = metadataValue(properties.getProperty("version"));
      if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
        continue;
      }
      MetaDependency dependency = new MetaDependency(groupId + ":" + artifactId, version, null);
      dependencies.put(dependency.key(), dependency);
    }
    return new ArrayList<>(dependencies.values());
  }

  private static MetaDependency fromManifestAndFileName(JarMetadata metadata) {
    Matcher matcher = JAR_FILE_PATTERN.matcher(metadata.jarName);
    String fileName = metadata.jarName;
    String artifactName =
        fileName.endsWith(".jar")
            ? fileName.substring(0, fileName.length() - ".jar".length())
            : fileName;
    String fileVersion = "";
    if (matcher.matches()) {
      artifactName = matcher.group(1);
      fileVersion = trim(matcher.group(2));
    }

    String bundleSymbolicName = manifestValue(metadata.attributes, "Bundle-SymbolicName");
    String bundleName = manifestValue(metadata.attributes, "Bundle-Name");
    String implementationTitle = manifestValue(metadata.attributes, "Implementation-Title");
    String bundleVersion = manifestValue(metadata.attributes, "Bundle-Version");
    String implementationVersion = manifestValue(metadata.attributes, "Implementation-Version");

    String name;
    if (isValidArtifactId(bundleName)) {
      name = bundleName;
    } else if (isValidArtifactId(implementationTitle)) {
      name = implementationTitle;
    } else {
      name = artifactName;
    }

    String version;
    if (!bundleVersion.isEmpty() && bundleVersion.equals(implementationVersion)) {
      version = bundleVersion;
    } else {
      version = firstNonEmpty(fileVersion, bundleVersion, implementationVersion);
    }

    String groupId = parseGroupId(bundleSymbolicName, artifactName);
    if (!isValidGroupId(groupId)) {
      groupId = parseGroupId(bundleSymbolicName, bundleName);
    }
    if (isValidGroupId(groupId)) {
      name = groupId + ":" + name;
    }
    return new MetaDependency(name, version, sha1(metadata.inputStreamSupplier));
  }

  private static boolean isValidArtifactId(String value) {
    return !value.isEmpty()
        && value.indexOf(' ') < 0
        && value.indexOf('.') < 0
        && value.toLowerCase(Locale.ROOT).equals(value);
  }

  private static boolean isValidGroupId(String value) {
    return !value.isEmpty()
        && value.indexOf(' ') < 0
        && value.indexOf('.') >= 0
        && value.toLowerCase(Locale.ROOT).equals(value);
  }

  private static String parseGroupId(String symbolicName, String artifactName) {
    if (symbolicName.isEmpty() || artifactName.isEmpty()) {
      return "";
    }
    String artifactSuffix = "." + artifactName;
    if (!symbolicName.endsWith(artifactSuffix)) {
      return "";
    }
    String groupId = symbolicName.substring(0, symbolicName.length() - artifactSuffix.length());
    return groupId.indexOf('.') >= 0 && groupId.length() > 5 ? groupId : "";
  }

  private static String manifestValue(Attributes attributes, String name) {
    return metadataValue(attributes.getValue(name));
  }

  private static String firstNonEmpty(String... values) {
    for (String value : values) {
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "";
  }

  private static String trim(@Nullable String value) {
    return value == null ? "" : value.trim();
  }

  private static String metadataValue(@Nullable String value) {
    String result = trim(value);
    return result.length() <= MAX_METADATA_VALUE_LENGTH ? result : "";
  }

  @Nullable
  private static String sha1(InputStreamSupplier inputStreamSupplier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      try (InputStream inputStream = inputStreamSupplier.get()) {
        byte[] buffer = new byte[8_192];
        long totalBytes = 0;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
          if (Thread.currentThread().isInterrupted()) {
            return null;
          }
          totalBytes += read;
          if (totalBytes > MAX_HASH_BYTES) {
            return null;
          }
          digest.update(buffer, 0, read);
        }
      }
      byte[] hash = digest.digest();
      char[] encoded = new char[hash.length * 2];
      for (int i = 0; i < hash.length; i++) {
        int value = hash[i] & 0xff;
        encoded[i * 2] = HEX[value >>> 4];
        encoded[i * 2 + 1] = HEX[value & 0x0f];
      }
      return new String(encoded);
    } catch (IOException | NoSuchAlgorithmException e) {
      logger.log(FINE, "Unable to hash Meta dependency", e);
      return null;
    }
  }

  private interface InputStreamSupplier {
    InputStream get() throws IOException;
  }

  private static final class JarMetadata {
    private final String jarName;
    private final Map<String, Properties> pomProperties;
    private final Attributes attributes;
    private final InputStreamSupplier inputStreamSupplier;

    private JarMetadata(
        String jarName,
        Map<String, Properties> pomProperties,
        Attributes attributes,
        InputStreamSupplier inputStreamSupplier) {
      this.jarName = jarName;
      this.pomProperties = pomProperties;
      this.attributes = attributes;
      this.inputStreamSupplier = inputStreamSupplier;
    }
  }

  private static final class NestedJarInputStream extends InputStream {
    private final JarFile outerJar;
    private final InputStream inputStream;

    private NestedJarInputStream(File outerJar, String innerJar) throws IOException {
      this.outerJar = new JarFile(outerJar, false);
      JarEntry entry = this.outerJar.getJarEntry(innerJar);
      if (entry == null) {
        this.outerJar.close();
        throw new IOException("Nested JAR not found: " + innerJar);
      }
      this.inputStream = this.outerJar.getInputStream(entry);
    }

    @Override
    public int read() throws IOException {
      return inputStream.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      return inputStream.read(bytes, offset, length);
    }

    @Override
    public void close() throws IOException {
      try {
        inputStream.close();
      } finally {
        outerJar.close();
      }
    }
  }

  private MetaDependencyResolver() {}
}
