package ch.tkb.certalert.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.ini4j.Ini;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Utility for resolving dynamic values from environment variables, plain files,
 * or structured configuration formats such as JSON, YAML, INI, Properties, and
 * TOML.
 *
 * <p>
 * Supported formats:
 * </p>
 * <ul>
 * <li>{@code env:VAR_NAME}</li>
 * <li>{@code file:/path/to/file.txt}</li>
 * <li>{@code file:/path/to/file.txt//key}</li>
 * <li>{@code json:/path/to/file.json//path.to.value}</li>
 * <li>{@code yaml:/path/to/file.yaml//path.to.value}</li>
 * <li>{@code ini:/path/to/file.ini//Section.Key}</li>
 * <li>{@code properties:/path/to/file.properties//key}</li>
 * <li>{@code toml:/path/to/file.toml//servers.0.host}</li>
 * </ul>
 */
public final class Resolver {

  private static final ObjectMapper jsonMapper = new ObjectMapper();
  private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  private Resolver() {
    // Utility class; prevent instantiation
  }

  /**
   * Resolves a value from an environment variable, file, or structured config
   * format.
   */
  public static String resolve(String rawValue) {
    if (rawValue == null) {
      return null;
    }

    if (rawValue.startsWith("env:")) {
      return resolveEnv(rawValue.substring(4));
    } else if (rawValue.startsWith("file:")) {
      return resolveFile(rawValue.substring(5));
    } else if (rawValue.startsWith("json:")) {
      return resolveJson(rawValue.substring(5));
    } else if (rawValue.startsWith("yaml:")) {
      return resolveYaml(rawValue.substring(5));
    } else if (rawValue.startsWith("ini:")) {
      return resolveIni(rawValue.substring(4));
    } else if (rawValue.startsWith("properties:")) {
      return resolveProperties(rawValue.substring(11));
    } else if (rawValue.startsWith("toml:")) {
      return resolveToml(rawValue.substring(5));
    } else {
      return rawValue;
    }
  }

  /**
   * Resolves a value from an environment variable.
   */
  private static String resolveEnv(String envVar) {
    return System.getenv(envVar);
  }

  /**
   * Resolves a value from a plain file or key-value file.
   */
  private static String resolveFile(String fileSpec) {
    String filePath;
    String key = null;

    int sepIndex = fileSpec.indexOf("//");
    if (sepIndex >= 0) {
      filePath = fileSpec.substring(0, sepIndex);
      key = fileSpec.substring(sepIndex + 2);
    } else {
      filePath = fileSpec;
    }

    Path path = Paths.get(filePath).toAbsolutePath().normalize();

    try {
      if (key == null || key.isBlank()) {
        return Files.readString(path).trim();
      } else {
        return readKeyFromFile(path, key);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read file: " + path, e);
    }
  }

  /**
   * Resolves a value from a JSON file using dot notation.
   */
  private static String resolveJson(String fileSpec) {
    return extractJsonOrYaml(fileSpec, jsonMapper);
  }

  /**
   * Resolves a value from a YAML file using dot notation.
   */
  private static String resolveYaml(String fileSpec) {
    return extractJsonOrYaml(fileSpec, yamlMapper);
  }

  /**
   * Extracts a value from a JSON or YAML file using a dot-separated path.
   */
  private static String extractJsonOrYaml(String fileSpec, ObjectMapper mapper) {
    String path, keyPath = null;
    int sepIndex = fileSpec.indexOf("//");
    if (sepIndex >= 0) {
      path = fileSpec.substring(0, sepIndex);
      keyPath = fileSpec.substring(sepIndex + 2);
    } else {
      path = fileSpec;
    }

    try {
      Path filePath = Paths.get(path).toAbsolutePath().normalize();
      JsonNode root = mapper.readTree(Files.newBufferedReader(filePath));
      if (keyPath == null || keyPath.isBlank()) {
        return root.toString();
      }
      return resolveJsonPath(root, keyPath);
    } catch (IOException e) {
      throw new RuntimeException("Failed to resolve JSON/YAML from: " + path, e);
    }
  }

  /**
   * Navigates a JsonNode tree using dot-separated notation.
   */
  private static String resolveJsonPath(JsonNode node, String path) {
    String[] parts = path.split("\\.");
    JsonNode current = node;
    for (String part : parts) {
      if (part.matches("\\d+")) {
        current = current.get(Integer.parseInt(part));
      } else {
        current = current.get(part);
      }
      if (current == null) {
        throw new RuntimeException("Path not found: " + path);
      }
    }
    return current.isValueNode() ? current.asText() : current.toString();
  }

  /**
   * Resolves a value from an INI file using Section.Key syntax.
   */
  private static String resolveIni(String fileSpec) {
    int sepIndex = fileSpec.indexOf("//");
    if (sepIndex < 0) {
      throw new IllegalArgumentException("INI path must be in the format ini:/file.ini//Section.Key");
    }

    String filePath = fileSpec.substring(0, sepIndex);
    String keyPath = fileSpec.substring(sepIndex + 2);

    String[] parts = keyPath.split("\\.", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("INI key must be in the format Section.Key");
    }

    try {
      Path iniPath = Paths.get(filePath).toAbsolutePath().normalize();
      Ini ini = new Ini(Files.newBufferedReader(iniPath));
      String value = ini.get(parts[0], parts[1]);
      if (value == null) {
        throw new RuntimeException("Key not found in INI file: " + keyPath);
      }
      return value.trim();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read INI file: " + filePath, e);
    }
  }

  /**
   * Resolves a value from a .properties file using key lookup.
   */
  private static String resolveProperties(String fileSpec) {
    int sepIndex = fileSpec.indexOf("//");
    if (sepIndex < 0) {
      throw new IllegalArgumentException("Properties path must be in the format properties:/file.properties//key");
    }

    String filePath = fileSpec.substring(0, sepIndex);
    String key = fileSpec.substring(sepIndex + 2);

    try (InputStream is = Files.newInputStream(Paths.get(filePath).toAbsolutePath().normalize())) {
      Properties props = new Properties();
      props.load(is);
      String value = props.getProperty(key);
      if (value == null) {
        throw new RuntimeException("Key not found in .properties file: " + key);
      }
      return value.trim();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read .properties file: " + filePath, e);
    }
  }

  /**
   * Resolves a value from a TOML file using dot-separated key path.
   */
  private static String resolveToml(String fileSpec) {
    int sepIndex = fileSpec.indexOf("//");
    if (sepIndex < 0) {
      throw new IllegalArgumentException("TOML path must be in the format toml:/file.toml//key.path");
    }

    String filePath = fileSpec.substring(0, sepIndex);
    String keyPath = fileSpec.substring(sepIndex + 2);

    try {
      Path tomlPath = Paths.get(filePath).toAbsolutePath().normalize();
      TomlParseResult result = Toml.parse(tomlPath);
      Object value = resolveTomlPath(result, keyPath);
      if (value == null) {
        throw new RuntimeException("Key not found in TOML file: " + keyPath);
      }
      return value.toString();
    } catch (RuntimeException e) {
      // Forward our specific exception as-is
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to read TOML file: " + filePath + " (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Navigates a TomlParseResult tree using dot-separated notation (e.g.,
   * servers.0.host).
   */
  private static Object resolveTomlPath(Object root, String path) {
    String[] parts = path.split("\\.");
    Object current = root;

    for (String part : parts) {
      if (current instanceof TomlParseResult result) {
        current = result.get(part);
      } else if (current instanceof TomlTable table) {
        current = table.get(part);
      } else if (current instanceof org.tomlj.TomlArray array) {
        if (!part.matches("\\d+")) {
          throw new RuntimeException("Expected numeric index for TOML array, got: " + part);
        }
        int idx = Integer.parseInt(part);
        if (idx >= array.size()) {
          throw new RuntimeException("Index out of bounds in TOML array: " + part);
        }
        current = array.get(idx);
      } else {
        throw new RuntimeException("Unsupported TOML path segment or structure: " + part + " (type="
            + current.getClass().getSimpleName() + ")");
      }

      if (current == null) {
        throw new RuntimeException("Key not found in TOML file: " + path);
      }
    }

    return current;
  }

  /**
   * Reads a key from a key=value formatted plain text file.
   */
  private static String readKeyFromFile(Path path, String key) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] pair = line.split("=", 2);
        if (pair.length == 2 && pair[0].trim().equals(key)) {
          return pair[1].trim();
        }
      }
    }
    throw new RuntimeException("Key '" + key + "' not found in file: " + path);
  }
}
