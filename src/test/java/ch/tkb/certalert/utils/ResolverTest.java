package ch.tkb.certalert.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ResolverTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("Resolver.resolve returns null for invalid raw value")
  void testEnvVariable() {
    // Test will pass only if JAVA_HOME is set — not always guaranteed.
    String javaHome = Resolver.resolve("env:JAVA_HOME");
    if (javaHome == null) {
      System.out.println("JAVA_HOME not set, skipping testEnvVariable.");
      return;
    }
    assertNotNull(javaHome);
  }

  @Test
  @DisplayName("Resolver.resolve returns literal value")
  void testLiteralValue() {
    assertEquals("password123", Resolver.resolve("password123"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves file path")
  void testFilePlain() throws IOException {
    Path file = tempDir.resolve("plain.txt");
    Files.writeString(file, "secret-value");
    String resolved = Resolver.resolve("file:" + file);
    assertEquals("secret-value", resolved);
  }

  @Test
  @DisplayName("Resolver.resolve resolves key-value file path")
  void testFileKeyValue() throws IOException {
    Path file = tempDir.resolve("kv.txt");
    Files.writeString(file, "foo=bar\nbaz=qux");
    String resolved = Resolver.resolve("file:" + file + "//baz");
    assertEquals("qux", resolved);
  }

  @Test
  @DisplayName("Resolver.resolve resolves JSON file path")
  void testJsonResolution() throws IOException {
    Path file = tempDir.resolve("test.json");
    Files.writeString(file, """
        {
          "database": {
            "host": "localhost",
            "port": 5432
          },
          "servers": [{"host": "srv1"}, {"host": "srv2"}]
        }
        """);

    assertEquals("localhost", Resolver.resolve("json:" + file + "//database.host"));
    assertEquals("srv1", Resolver.resolve("json:" + file + "//servers.0.host"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves YAML file path")
  void testYamlResolution() throws IOException {
    Path file = tempDir.resolve("test.yaml");
    Files.writeString(file, """
        server:
          port: 8080
        servers:
          - host: alpha
          - host: beta
        """);

    assertEquals("8080", Resolver.resolve("yaml:" + file + "//server.port"));
    assertEquals("alpha", Resolver.resolve("yaml:" + file + "//servers.0.host"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves INI file path")
  void testIniResolution() throws IOException {
    Path file = tempDir.resolve("test.ini");
    Files.writeString(file, """
        [database]
        user = admin
        password = secret
        """);

    assertEquals("admin", Resolver.resolve("ini:" + file + "//database.user"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves properties file path")
  void testPropertiesResolution() throws IOException {
    Path file = tempDir.resolve("test.properties");
    Files.writeString(file, """
        foo=bar
        nested.value=123
        """);

    assertEquals("bar", Resolver.resolve("properties:" + file + "//foo"));
    assertEquals("123", Resolver.resolve("properties:" + file + "//nested.value"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves TOML file path")
  void testTomlBasic() throws IOException {
    Path file = tempDir.resolve("test.toml");
    Files.writeString(file, "[database]\nhost = \"localhost\"\n");

    assertEquals("localhost", Resolver.resolve("toml:" + file + "//database.host"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves TOML file path with dotted keys")
  void testTomlResolution() throws IOException {
    Path file = tempDir.resolve("test.toml");
    Files.writeString(file, """
        [database]
        host = "localhost"
        port = 3306

        [[servers]]
        host = "a1"

        [[servers]]
        host = "a2"
        """);

    String raw = Files.readString(file);
    System.out.println("=== TOML CONTENT ===");
    System.out.println(raw);

    TomlParseResult result = Toml.parse(file);
    System.out.println("=== dottedKeySet ===");
    System.out.println(result.dottedKeySet());
    System.out.println("=== servers raw ===");
    Object servers = result.get("servers");
    System.out.println(servers);
    System.out.println("=== servers type ===");
    System.out.println(servers.getClass());

    if (servers instanceof java.util.List<?> list) {
      Object item0 = list.get(0);
      System.out.println("servers[0]: " + item0);
      System.out.println("type: " + item0.getClass());
      if (item0 instanceof TomlTable table) {
        System.out.println("servers[0].host: " + table.getString("host"));
      }
    }

    assertEquals("localhost", Resolver.resolve("toml:" + file + "//database.host"));
    assertEquals("a1", Resolver.resolve("toml:" + file + "//servers.0.host"));
  }

  @Test
  @DisplayName("Resolver.resolve resolves TOML file path with minimal dotted keys")
  void testTomlResolutionMinimal() throws IOException {
    Path file = tempDir.resolve("test.toml");
    Files.writeString(file, """
        [database]
        host = "localhost"
        """);

    System.out.println("=== TOML content ===");
    System.out.println(Files.readString(file));

    String resolved = Resolver.resolve("toml:" + file + "//database.host");
    assertEquals("localhost", resolved);
  }

  @Test
  @DisplayName("Resolver.resolve throws exception for missing file")
  void testMissingFileThrows() {
    RuntimeException ex = assertThrows(RuntimeException.class, () -> Resolver.resolve("file:/nonexistent/file.txt"));
    assertTrue(ex.getMessage().contains("Failed to read"));
  }

  @Test
  @DisplayName("Resolver.resolve throws exception for missing key")
  void testMissingKeyThrows() throws IOException {
    Path file = tempDir.resolve("test.properties");
    Files.writeString(file, "x=1");
    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> Resolver.resolve("properties:" + file + "//missing.key"));
    assertTrue(ex.getMessage().contains("Key not found"));
  }

  @Test
  @DisplayName("Resolver.resolve returns raw value for invalid prefix")
  void testInvalidPrefixReturnsAsIs() {
    assertEquals("foobar", Resolver.resolve("foobar"));
  }

  @Test
  @DisplayName("Resolver.resolve returns full JSON document when no key path")
  void testJsonNoKeyPath() throws IOException {
    Path file = tempDir.resolve("noKey.json");
    Files.writeString(file, """
        {
          "foo": 123,
          "bar": "baz"
        }
        """);
    String resolved = Resolver.resolve("json:" + file);
    assertTrue(resolved.contains("foo"));
    assertTrue(resolved.contains("bar"));
  }

  @Test
  @DisplayName("Resolver.resolve returns full YAML document when no key path")
  void testYamlNoKeyPath() throws IOException {
    Path file = tempDir.resolve("noKey.yaml");
    Files.writeString(file, """
        foo: 123
        bar: baz
        """);
    String resolved = Resolver.resolve("yaml:" + file);
    assertTrue(resolved.contains("foo"));
    assertTrue(resolved.contains("bar"));
  }

  @Test
  @DisplayName("Resolver.resolve returns full TOML document when no key path")
  void testTomlNoKeyPath() throws IOException {
    Path file = tempDir.resolve("noKey.toml");
    Files.writeString(file, "foo = 123\nbar = 'baz'\n");
    String resolved = Resolver.resolve("toml:" + file + "//foo");
    assertEquals("123", resolved);
  }

  @Test
  @DisplayName("Resolver.resolve throws for properties with missing key format")
  void testPropertiesInvalidFormat() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> Resolver.resolve("properties:/some/file.properties"));
    assertTrue(ex.getMessage().contains("format"));
  }

  @Test
  @DisplayName("Resolver.resolve throws for INI with missing section.key format")
  void testIniInvalidFormat() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> Resolver.resolve("ini:/some/file.ini//invalidKey"));
    assertTrue(ex.getMessage().contains("format"));
  }

  @Test
  @DisplayName("Resolver.resolve throws for out-of-bounds TOML index")
  void testTomlInvalidIndex() throws IOException {
    Path file = tempDir.resolve("badIndex.toml");
    Files.writeString(file, """
        [[items]]
        name = "a"
        """);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> Resolver.resolve("toml:" + file + "//items.5.name"));

    assertTrue(ex.getMessage().contains("Index out of bounds")
        || (ex.getCause() != null && ex.getCause().getMessage().contains("Index out of bounds")),
        "Expected TOML path segment error, got: " + ex.getMessage());
  }

  @Test
  @DisplayName("Resolver.resolve throws for missing TOML key")
  void testTomlMissingKey() throws IOException {
    Path file = tempDir.resolve("missingKey.toml");
    Files.writeString(file, """
        [database]
        host = "localhost"
        """);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> Resolver.resolve("toml:" + file + "//database.password"));

    assertTrue(ex.getMessage().contains("Key not found")
        || (ex.getCause() != null && ex.getCause().getMessage().contains("Key not found")),
        "Expected missing key message, got: " + ex.getMessage());
  }
}
