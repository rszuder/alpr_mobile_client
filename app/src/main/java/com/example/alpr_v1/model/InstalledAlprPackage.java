package com.example.alpr_v1.model;

import java.io.File;

public final class InstalledAlprPackage {
    private final AlprPackageManifest manifest;
    private final File directory;
    private final String fingerprint;
    private final long sourceSizeBytes;
    private final String sourceSha256;
    private final InstalledModel vehicleModel;
    private final InstalledModel plateModel;
    private final InstalledModel characterModel;

    public InstalledAlprPackage(
            AlprPackageManifest manifest,
            File directory,
            String fingerprint,
            long sourceSizeBytes,
            String sourceSha256,
            InstalledModel vehicleModel,
            InstalledModel plateModel,
            InstalledModel characterModel
    ) {
        this.manifest = manifest;
        this.directory = directory;
        this.fingerprint = fingerprint;
        this.sourceSizeBytes = Math.max(0L, sourceSizeBytes);
        this.sourceSha256 = sourceSha256 == null ? "" : sourceSha256;
        this.vehicleModel = vehicleModel;
        this.plateModel = plateModel;
        this.characterModel = characterModel;
    }

    public AlprPackageManifest manifest() { return manifest; }
    public File directory() { return directory; }
    public String fingerprint() { return fingerprint; }
    public long sourceSizeBytes() { return sourceSizeBytes; }
    public String sourceSha256() { return sourceSha256; }
    public InstalledModel vehicleModel() { return vehicleModel; }
    public InstalledModel plateModel() { return plateModel; }
    public InstalledModel characterModel() { return characterModel; }
    public String storageId() { return directory.getName(); }
    public File sourceArchive() { return new File(directory, "source.alprmodel"); }
}
