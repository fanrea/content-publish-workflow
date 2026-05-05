package com.contentworkflow.document.application.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "workflow.storage.snapshot", name = "backend", havingValue = "filesystem")
public class FileSystemDocumentSnapshotStore implements DocumentSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentSnapshotStore.class);
    private static final HexFormat HEX = HexFormat.of();

    private final Path rootDirectory;

    public FileSystemDocumentSnapshotStore(
            @Value("${workflow.storage.snapshot.filesystem.root-dir:./data/snapshots}") String rootDirectory) {
        this.rootDirectory = resolveRootDirectory(rootDirectory);
        try {
            Files.createDirectories(this.rootDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to initialize snapshot filesystem root: " + this.rootDirectory, ex);
        }
        log.info("document snapshot store backend=filesystem root={}", this.rootDirectory.toAbsolutePath());
    }

    @Override
    public void put(String snapshotRef, String content) {
        String normalizedRef = normalizeSnapshotRef(snapshotRef);
        if (normalizedRef == null) {
            return;
        }
        Path snapshotPath = resolveSnapshotPath(normalizedRef);
        try {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(
                    snapshotPath,
                    content == null ? "" : content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to persist snapshot content, snapshotRef=" + normalizedRef, ex);
        }
    }

    @Override
    public Optional<String> get(String snapshotRef) {
        String normalizedRef = normalizeSnapshotRef(snapshotRef);
        if (normalizedRef == null) {
            return Optional.empty();
        }
        Path snapshotPath = resolveSnapshotPath(normalizedRef);
        if (!Files.exists(snapshotPath) || !Files.isRegularFile(snapshotPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(snapshotPath, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load snapshot content, snapshotRef=" + normalizedRef, ex);
        }
    }

    private Path resolveRootDirectory(String configuredRoot) {
        String root = configuredRoot == null || configuredRoot.isBlank() ? "./data/snapshots" : configuredRoot.trim();
        try {
            return Paths.get(root).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("invalid snapshot filesystem root: " + root, ex);
        }
    }

    private Path resolveSnapshotPath(String snapshotRef) {
        // File-system adapter uses hash(ref) as filename to avoid path traversal and platform-specific invalid chars.
        String hash = hashSnapshotRef(snapshotRef);
        String shard = hash.substring(0, 2);
        return rootDirectory.resolve(shard).resolve(hash + ".snapshot");
    }

    private String hashSnapshotRef(String snapshotRef) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(snapshotRef.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 is unavailable for snapshot hashing", ex);
        }
    }

    private String normalizeSnapshotRef(String snapshotRef) {
        if (snapshotRef == null) {
            return null;
        }
        String normalized = snapshotRef.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
