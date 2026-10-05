import com.zack88604.autoupdater.domain.FileEntry;
import com.zack88604.autoupdater.domain.Manifest;
import com.zack88604.autoupdater.infrastructure.files.FileManager;
import com.zack88604.autoupdater.infrastructure.http.ServerClient;
import com.zack88604.autoupdater.infrastructure.json.JsonParser;
import com.zack88604.autoupdater.infrastructure.json.ManifestParser;
import com.zack88604.autoupdater.infrastructure.security.ManifestKeyTrustBootstrap;
import com.zack88604.autoupdater.infrastructure.security.ManifestSignatureVerifier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Dependency-free self-check for the updater's manifest parsing, key trust, and
 * managed-file rules. It needs no test framework: run it with run-tests.sh or
 * run-tests.bat, or manually with
 * {@code java -cp <classes> AgentSelfCheck}.
 */
public final class AgentSelfCheck {

    private static int checked;
    private static int failures;

    private AgentSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        File sandbox = Files.createTempDirectory("mc-update-self-check-").toFile();
        try {
            checkJsonKeyMatching();
            checkManifestParsing();
            checkKeyTrustIsOptional(sandbox);
            checkManagedFiles(sandbox);
            checkPathEncoding();
        } finally {
            deleteRecursively(sandbox);
        }
        System.out.println("AgentSelfCheck: " + checked + " check(s), " + failures + " failure(s)");
        if (failures > 0) {
            System.exit(1);
        }
    }

    /**
     * A string value that happens to equal a key name must never be read as
     * that key: a manifest managing a path literally named "agent" used to be
     * parsed as if it carried agent self-update metadata.
     */
    private static void checkJsonKeyMatching() {
        String collision = """
                {"managed_paths": ["agent"], "files": [{"path": "x", "size": 7}], "agent": {"path": "y", "size": 9}}""";
        String object = JsonParser.getObject(collision, "agent");
        check("getObject skips a value that equals the key",
                object != null && object.indexOf("9") >= 0 && object.indexOf("7") < 0);

        String withoutSection = """
                {"managed_paths": ["agent"], "files": []}""";
        check("getObject returns null when only a value matches the key",
                JsonParser.getObject(withoutSection, "agent") == null);

        String spaced = """
                { "files" : [ ] }""";
        check("getArray accepts whitespace around the colon",
                JsonParser.getArray(spaced, "files") != null);
    }

    private static void checkManifestParsing() {
        String managedNamedAgent = """
                {"managed_paths": ["agent"], "excluded_paths": [], "files": [{"path": "agent", "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "size": 3}], "agent": {"path": "UpdateAgent.jar", "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "size": 9}}""";
        Manifest parsed = ManifestParser.parse(managedNamedAgent);
        check("a managed path named 'agent' is not mistaken for the agent section",
                parsed.isAgentSectionPresent() && parsed.getAgentArtifact() != null
                        && parsed.getAgentArtifact().getSize() == 9);
        check("the managed file list is still parsed",
                parsed.getFiles().size() == 1 && "agent".equals(parsed.getFiles().get(0).getPath()));

        String bracePath = """
                {"files": [{"path": "mods/a{b.jar", "hash": "h", "size": 1}]}""";
        Manifest braces = ManifestParser.parse(bracePath);
        check("a brace inside a path does not drop the entry",
                braces.getFiles().size() == 1
                        && "mods/a{b.jar".equals(braces.getFiles().get(0).getPath()));
    }

    private static void checkKeyTrustIsOptional(File sandbox) throws Exception {
        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("Ed25519");
        } catch (Exception unavailable) {
            System.out.println("SKIP  Ed25519 is unavailable on this runtime");
            return;
        }
        File gameDir = new File(sandbox, "trust");
        Files.createDirectories(gameDir.toPath());
        String pinned = Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        ManifestSignatureVerifier verifier = ManifestKeyTrustBootstrap.resolveOffline(gameDir, pinned, null);
        check("a pinned public key resolves without a key id", verifier != null);

        String other = Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        boolean rejected = false;
        try {
            ManifestKeyTrustBootstrap.resolveOffline(gameDir, other, null);
        } catch (IOException expected) {
            rejected = true;
        }
        check("a conflicting pinned key is still refused", rejected);
    }

    private static void checkManagedFiles(File sandbox) throws Exception {
        // A non-canonical game directory used to shift the computed relative
        // path and delete files that the manifest lists.
        File gameDir = new File(sandbox, "./game");
        File mods = new File(sandbox, "game/mods");
        Files.createDirectories(mods.toPath());
        write(new File(mods, "keep.jar"), "KEEP");
        write(new File(mods, "stale.jar"), "STALE");

        FileManager manager = new FileManager(gameDir);
        check("a managed path resolves inside the game directory",
                manager.resolveManagedFile("mods/keep.jar") != null);
        check("parent traversal is rejected", manager.resolveManagedFile("../evil.txt") == null);
        check("an absolute path is rejected", manager.resolveManagedFile("/etc/passwd") == null);
        check("a Windows drive path is rejected", manager.resolveManagedFile("C:/windows/system32") == null);

        List<FileEntry> manifestFiles = new ArrayList<FileEntry>();
        manifestFiles.add(new FileEntry("mods/keep.jar", "h", 4L));
        StringBuilder log = new StringBuilder();
        manager.cleanStaleFiles(manifestFiles, Arrays.asList("mods/"), new ArrayList<String>(),
                line -> log.append(line).append(' '), () -> { }, null);
        check("stale cleanup keeps files that the manifest lists",
                new File(mods, "keep.jar").isFile());
        check("stale cleanup removes files that the manifest omits",
                !new File(mods, "stale.jar").exists());
        check("stale cleanup logs the removal", log.indexOf("stale.jar") >= 0);
    }

    private static void checkPathEncoding() {
        check("path segments are percent-encoded",
                "mods/a%20b.jar".equals(ServerClient.encodePath("mods/a b.jar")));
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(String description, boolean passed) {
        checked++;
        if (!passed) {
            failures++;
        }
        System.out.println((passed ? "PASS  " : "FAIL  ") + description);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
