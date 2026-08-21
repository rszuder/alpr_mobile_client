package com.example.alpr_v1.model;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class AlprPackageImporter {
    private static final int MAX_PACKAGE_ENTRIES = 640;
    private static final long MAX_PACKAGE_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_SOURCE_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Context context;
    private final ModelRegistry registry;
    private final ModelPackageImporter singleModelImporter;

    public AlprPackageImporter(Context context, ModelRegistry registry) {
        this.context = context.getApplicationContext();
        this.registry = registry;
        this.singleModelImporter = new ModelPackageImporter(context, registry.modelsRoot());
    }

    public ModelImportResult importPackage(Uri source) throws ModelPackageException {
        File work = new File(new File(context.getCacheDir(), "alpr-package-dispatch"), UUID.randomUUID().toString());
        File localPackage = new File(work, "source.alprmodel");
        try {
            ModelPackageImporter.ensureDirectory(work);
            copySource(source, localPackage);
            String manifestText = readRootManifest(localPackage);
            String schema;
            try {
                schema = new JSONObject(manifestText).optString("schema");
            } catch (JSONException e) {
                throw new ModelPackageException("Nieprawidłowy manifest pakietu: " + e.getMessage(), e);
            }
            if (ModelManifest.SCHEMA.equals(schema)) {
                return ModelImportResult.single(singleModelImporter.importPackage(localPackage));
            }
            if (AlprPackageManifest.SCHEMA.equals(schema)) {
                return ModelImportResult.complete(importCompletePackage(localPackage));
            }
            throw new ModelPackageException("Nieobsługiwany schemat pakietu: " + schema);
        } catch (ModelPackageException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelPackageException("Nie udało się zaimportować pakietu ALPR: " + e.getMessage(), e);
        } finally {
            ModelPackageImporter.safeDelete(work);
        }
    }

    private InstalledAlprPackage importCompletePackage(File source) throws Exception {
        File staging = new File(new File(context.getCacheDir(), "alpr-complete-import"), UUID.randomUUID().toString());
        try {
            ModelPackageImporter.ensureDirectory(staging);
            extractCompleteArchive(source, staging);
            File manifestFile = new File(staging, "manifest.json");
            if (!manifestFile.isFile()) {
                throw new ModelPackageException("Kompletny pakiet nie zawiera manifest.json w katalogu głównym");
            }
            byte[] manifestBytes = Files.readAllBytes(manifestFile.toPath());
            AlprPackageManifest manifest;
            try {
                manifest = AlprPackageManifest.parse(new String(manifestBytes, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new ModelPackageException("Nieprawidłowy manifest kompletnego pakietu: " + e.getMessage(), e);
            }

            ValidatedChild vehicle = manifest.vehicle() == null
                    ? null
                    : validateChild(staging, manifest.vehicle());
            ValidatedChild plate = validateChild(staging, manifest.plate());
            ValidatedChild character = validateChild(staging, manifest.character());
            InstalledModel installedVehicle = vehicle == null
                    ? null
                    : singleModelImporter.importPackage(vehicle.packageFile);
            InstalledModel installedPlate = singleModelImporter.importPackage(plate.packageFile);
            InstalledModel installedCharacter = singleModelImporter.importPackage(character.packageFile);
            InstalledAlprPackage installedPackage = registerPackage(
                    manifest,
                    manifestBytes,
                    source,
                    installedVehicle,
                    installedPlate,
                    installedCharacter
            );
            // Kompletny source.alprmodel zawiera już pakiety potomne. Nie
            // przechowujemy ich drugi raz obok rozpakowanych wag wykonawczych.
            if (installedVehicle != null) {
                ModelPackageImporter.safeDelete(installedVehicle.sourceArchive());
            }
            ModelPackageImporter.safeDelete(installedPlate.sourceArchive());
            ModelPackageImporter.safeDelete(installedCharacter.sourceArchive());
            return installedPackage;
        } finally {
            ModelPackageImporter.safeDelete(staging);
        }
    }

    private ValidatedChild validateChild(File root, AlprPackageModelEntry entry) throws Exception {
        File packageFile = resolveRequiredFile(root, entry.packageFile());
        File sidecarFile = resolveRequiredFile(root, entry.manifestFile());
        verifyHash(packageFile, entry.sha256().get(entry.packageFile()), entry.packageFile());
        verifyHash(sidecarFile, entry.sha256().get(entry.manifestFile()), entry.manifestFile());

        ModelManifest nested = singleModelImporter.validatePackage(packageFile);
        ModelManifest sidecar;
        String sidecarText = new String(Files.readAllBytes(sidecarFile.toPath()), StandardCharsets.UTF_8);
        try {
            sidecar = ModelManifest.parse(sidecarText);
        } catch (JSONException e) {
            throw new ModelPackageException(
                    "Nieprawidłowy manifest boczny modelu " + entry.role().wireName() + ": " + e.getMessage(), e
            );
        }
        requireEntryMatches(entry, nested);
        requireEntryMatches(entry, sidecar);
        if (!jsonEquivalent(new JSONObject(nested.rawJson()), new JSONObject(sidecar.rawJson()))) {
            throw new ModelPackageException(
                    "Manifest boczny nie odpowiada manifestowi zagnieżdżonego modelu " + entry.role().wireName()
            );
        }
        return new ValidatedChild(packageFile);
    }

    private static void requireEntryMatches(AlprPackageModelEntry entry, ModelManifest manifest)
            throws ModelPackageException {
        if (manifest.role() != entry.role()) {
            throw new ModelPackageException("Zagnieżdżony model ma niezgodną rolę: " + manifest.role().wireName());
        }
        if (!manifest.task().equals(entry.task())) {
            throw new ModelPackageException("Zagnieżdżony model ma niezgodne zadanie: " + manifest.task());
        }
        if (!manifest.modelId().equals(entry.modelId())) {
            throw new ModelPackageException("Niezgodny model_id zagnieżdżonego modelu: " + manifest.modelId());
        }
    }

    private InstalledAlprPackage registerPackage(
            AlprPackageManifest manifest,
            byte[] manifestBytes,
            File source,
            InstalledModel vehicle,
            InstalledModel plate,
            InstalledModel character
    ) throws Exception {
        String fingerprint = Hashing.sha256(manifestBytes).substring(0, 16);
        File packagesRoot = registry.packagesRoot();
        ModelPackageImporter.ensureDirectory(packagesRoot);
        File destination = new File(packagesRoot, manifest.packageId() + "-" + fingerprint);
        if (!destination.exists()) {
            File record = new File(new File(context.getCacheDir(), "alpr-package-record"), UUID.randomUUID().toString());
            try {
                ModelPackageImporter.ensureDirectory(record);
                Files.write(new File(record, "manifest.json").toPath(), manifestBytes);
                Files.copy(
                        source.toPath(),
                        new File(record, "source.alprmodel").toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
                JSONObject installation = new JSONObject();
                if (vehicle != null) {
                    installation.put("vehicle_storage_id", vehicle.storageId());
                }
                installation.put("plate_storage_id", plate.storageId());
                installation.put("character_storage_id", character.storageId());
                installation.put("source_size_bytes", source.length());
                installation.put("source_sha256", Hashing.sha256(source));
                Files.write(
                        new File(record, "installation.json").toPath(),
                        installation.toString(2).getBytes(StandardCharsets.UTF_8)
                );
                try {
                    Files.move(record.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(record.toPath(), destination.toPath());
                }
            } finally {
                ModelPackageImporter.safeDelete(record);
            }
        }
        File preservedSource = new File(destination, "source.alprmodel");
        if (!preservedSource.isFile()) {
            Files.copy(source.toPath(), preservedSource.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        registry.reload();
        InstalledAlprPackage installed = registry.findPackage(destination.getName());
        if (installed != null) return installed;
        return new InstalledAlprPackage(
                manifest,
                destination,
                fingerprint,
                source.length(),
                Hashing.sha256(source),
                vehicle,
                plate,
                character
        );
    }

    private void copySource(Uri source, File destination) throws IOException, ModelPackageException {
        ContentResolver resolver = context.getContentResolver();
        InputStream raw = resolver.openInputStream(source);
        if (raw == null) throw new ModelPackageException("Nie można otworzyć wybranego pliku");
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        try (InputStream input = new BufferedInputStream(raw);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_SOURCE_BYTES) {
                    throw new ModelPackageException("Plik pakietu przekracza limit 1 GiB");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static String readRootManifest(File source) throws IOException, ModelPackageException {
        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if ("manifest.json".equals(entry.getName())) {
                    if (manifest != null) throw new ModelPackageException("Powtórzony manifest.json w pakiecie");
                    manifest = entry;
                }
            }
            if (manifest == null || manifest.isDirectory()) {
                throw new ModelPackageException("Pakiet nie zawiera manifest.json w katalogu głównym");
            }
            try (InputStream input = zip.getInputStream(manifest)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    if (output.size() + read > MAX_MANIFEST_BYTES) {
                        throw new ModelPackageException("Manifest pakietu przekracza limit 2 MiB");
                    }
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void extractCompleteArchive(File source, File staging) throws Exception {
        Path root = staging.toPath().toAbsolutePath().normalize();
        int entryCount = 0;
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(source.toPath())))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_PACKAGE_ENTRIES) {
                    throw new ModelPackageException("Kompletny pakiet ALPR zawiera więcej niż 640 wpisów");
                }
                String name = entry.getName();
                ModelPackageImporter.validateZipPath(name);
                if (!seen.add(name)) throw new ModelPackageException("Powtórzony wpis w pakiecie: " + name);
                Path destination = root.resolve(name).normalize();
                if (!destination.startsWith(root)) {
                    throw new ModelPackageException("Pakiet zawiera niedozwoloną ścieżkę: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    zip.closeEntry();
                    continue;
                }
                if (Files.exists(destination)) {
                    throw new ModelPackageException("Powtórzony plik w pakiecie: " + name);
                }
                Files.createDirectories(destination.getParent());
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination.toFile()))) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        totalBytes += read;
                        if (totalBytes > MAX_PACKAGE_UNCOMPRESSED_BYTES) {
                            throw new ModelPackageException("Rozpakowany pakiet ALPR przekracza limit 1 GiB");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static File resolveRequiredFile(File root, String relative) throws Exception {
        ModelPackageImporter.validateZipPath(relative);
        Path normalizedRoot = root.toPath().toAbsolutePath().normalize();
        Path path = normalizedRoot.resolve(relative).normalize();
        if (!path.startsWith(normalizedRoot) || !Files.isRegularFile(path)) {
            throw new ModelPackageException("Brak pliku kompletnego pakietu: " + relative);
        }
        return path.toFile();
    }

    private static void verifyHash(File file, String expected, String relative) throws Exception {
        String actual = Hashing.sha256(file);
        if (!actual.equals(expected)) {
            throw new ModelPackageException("Niezgodna suma SHA-256 pliku: " + relative);
        }
    }

    private static boolean jsonEquivalent(Object left, Object right) throws JSONException {
        return normalizeJson(left).equals(normalizeJson(right));
    }

    private static Object normalizeJson(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> normalized = new TreeMap<>();
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                normalized.put(key, normalizeJson(object.get(key)));
            }
            return normalized;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> normalized = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) normalized.add(normalizeJson(array.get(i)));
            return normalized;
        }
        return value;
    }

    private static final class ValidatedChild {
        private final File packageFile;

        private ValidatedChild(File packageFile) {
            this.packageFile = packageFile;
        }
    }
}
