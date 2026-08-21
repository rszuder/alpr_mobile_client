package com.example.alpr_v1.model;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ModelPackageImporter {
    private static final int MAX_ENTRIES = 256;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Context context;
    private final File modelsRoot;

    public ModelPackageImporter(Context context, File modelsRoot) {
        this.context = context.getApplicationContext();
        this.modelsRoot = modelsRoot;
    }

    public InstalledModel importPackage(Uri source) throws ModelPackageException {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream raw = resolver.openInputStream(source)) {
            if (raw == null) throw new ModelPackageException("Nie można otworzyć wybranego pliku");
            return importPackage(raw);
        } catch (ModelPackageException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelPackageException("Nie można otworzyć wybranego pliku: " + e.getMessage(), e);
        }
    }

    public InstalledModel importPackage(File source) throws ModelPackageException {
        try {
            InstalledModel installed = importPackage(new FileInputStream(source));
            File preserved = installed.sourceArchive();
            if (!preserved.isFile()) {
                Files.copy(source.toPath(), preserved.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return installed;
        } catch (ModelPackageException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelPackageException("Nie można otworzyć pakietu modelu: " + e.getMessage(), e);
        }
    }

    public ModelManifest validatePackage(File source) throws ModelPackageException {
        File staging = new File(new File(context.getCacheDir(), "model-validation"), UUID.randomUUID().toString());
        try {
            ensureDirectory(staging);
            try (InputStream raw = new FileInputStream(source)) {
                extractArchive(raw, staging);
            }
            return readAndValidate(staging);
        } catch (ModelPackageException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelPackageException("Nieprawidłowy pakiet modelu: " + e.getMessage(), e);
        } finally {
            safeDelete(staging);
        }
    }

    private InstalledModel importPackage(InputStream raw) throws ModelPackageException {
        File staging = new File(new File(context.getCacheDir(), "model-import"), UUID.randomUUID().toString());
        try {
            ensureDirectory(staging);
            extractArchive(raw, staging);
            ModelManifest manifest = readAndValidate(staging);
            byte[] manifestBytes = Files.readAllBytes(new File(staging, "manifest.json").toPath());

            String fingerprint = Hashing.sha256(manifestBytes).substring(0, 16);
            File roleDirectory = new File(modelsRoot, manifest.role().wireName());
            ensureDirectory(roleDirectory);
            File destination = new File(roleDirectory, manifest.modelId() + "-" + fingerprint);
            if (destination.exists()) {
                deleteRecursively(staging.toPath());
                return new InstalledModel(manifest, destination, fingerprint);
            }

            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging.toPath(), destination.toPath());
            }
            return new InstalledModel(manifest, destination, fingerprint);
        } catch (ModelPackageException e) {
            safeDelete(staging);
            throw e;
        } catch (Exception e) {
            safeDelete(staging);
            throw new ModelPackageException("Nie udało się zaimportować pakietu: " + e.getMessage(), e);
        }
    }

    private ModelManifest readAndValidate(File staging) throws Exception {
        File manifestFile = new File(staging, "manifest.json");
        if (!manifestFile.isFile()) {
            throw new ModelPackageException("Pakiet nie zawiera pliku manifest.json w katalogu głównym");
        }
        byte[] manifestBytes = Files.readAllBytes(manifestFile.toPath());
        ModelManifest manifest;
        try {
            manifest = ModelManifest.parse(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new ModelPackageException("Nieprawidłowy manifest modelu: " + e.getMessage(), e);
        }
        validateFiles(staging, manifest);
        return manifest;
    }

    private void extractArchive(InputStream raw, File staging) throws IOException, ModelPackageException {
        Path root = staging.toPath().toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new ModelPackageException("Pakiet zawiera zbyt wiele plików");
                }
                String safeName = entry.getName();
                validateZipPath(safeName);
                if (!seen.add(safeName)) {
                    throw new ModelPackageException("Powtórzony wpis w pakiecie: " + safeName);
                }
                Path destination = root.resolve(safeName).normalize();
                if (!destination.startsWith(root)) {
                    throw new ModelPackageException("Pakiet zawiera niedozwoloną ścieżkę: " + safeName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    zip.closeEntry();
                    continue;
                }
                if (Files.exists(destination)) {
                    throw new ModelPackageException("Powtórzony plik w pakiecie: " + safeName);
                }
                Files.createDirectories(destination.getParent());
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination.toFile()))) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        totalBytes += read;
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw new ModelPackageException("Rozpakowany pakiet przekracza limit 512 MiB");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    static void validateZipPath(String path) throws ModelPackageException {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.startsWith("\\")
                || path.indexOf('\\') >= 0) {
            throw new ModelPackageException("Pakiet zawiera niedozwoloną ścieżkę POSIX: " + path);
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (trimmed.isEmpty()) throw new ModelPackageException("Pakiet zawiera pustą ścieżkę");
        for (String part : trimmed.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains(":")) {
                throw new ModelPackageException("Pakiet zawiera niedozwoloną ścieżkę POSIX: " + path);
            }
        }
    }

    private void validateFiles(File staging, ModelManifest manifest) throws Exception {
        Path root = staging.toPath().toAbsolutePath().normalize();
        for (ModelVariant variant : manifest.variants()) {
            boolean hasParam = false;
            boolean hasBin = false;
            for (String relative : variant.files()) {
                validateZipPath(relative);
                Path path = root.resolve(relative).normalize();
                if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                    throw new ModelPackageException("Brak pliku wariantu " + variant.id() + ": " + relative);
                }
                String lower = relative.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".param")) hasParam = true;
                if (lower.endsWith(".bin")) hasBin = true;

                String expected = variant.sha256().get(relative);
                if (expected == null || !expected.matches("[0-9a-f]{64}")) {
                    throw new ModelPackageException("Brak prawidłowej sumy SHA-256 dla pliku: " + relative);
                }
                String actual = Hashing.sha256(path.toFile());
                if (!actual.equals(expected)) {
                    throw new ModelPackageException("Niezgodna suma SHA-256 pliku: " + relative);
                }
            }
            String primary = variant.primaryFile().toLowerCase(Locale.ROOT);
            if (variant.runtime() == ModelRuntime.TFLITE && !primary.endsWith(".tflite")) {
                throw new ModelPackageException("Wariant TFLite musi wskazywać plik .tflite");
            }
            if (variant.runtime() == ModelRuntime.ONNX && !primary.endsWith(".onnx")) {
                throw new ModelPackageException("Wariant ONNX musi wskazywać plik .onnx");
            }
            if (variant.runtime() == ModelRuntime.NCNN && (!hasParam || !hasBin)) {
                throw new ModelPackageException("Wariant NCNN wymaga plików .param i .bin");
            }
        }
    }

    static void ensureDirectory(File directory) throws IOException {
        Files.createDirectories(directory.toPath());
        if (!directory.isDirectory()) throw new IOException("Nie można utworzyć katalogu: " + directory);
    }

    static void safeDelete(File directory) {
        try {
            if (directory.exists()) deleteRecursively(directory.toPath());
        } catch (Exception ignored) {
            // Katalog tymczasowy zostanie usunięty przez system cache.
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw e;
        }
    }
}
