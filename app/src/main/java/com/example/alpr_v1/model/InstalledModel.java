package com.example.alpr_v1.model;

import java.io.File;

public final class InstalledModel {
    private final ModelManifest manifest;
    private final File directory;
    private final String fingerprint;

    public InstalledModel(ModelManifest manifest, File directory, String fingerprint) {
        this.manifest = manifest;
        this.directory = directory;
        this.fingerprint = fingerprint;
    }

    public ModelManifest manifest() { return manifest; }
    public File directory() { return directory; }
    public String fingerprint() { return fingerprint; }
    public File resolve(String relativePath) { return new File(directory, relativePath); }
    public File sourceArchive() { return new File(directory, "source.alprmodel"); }

    public String storageId() {
        return manifest.role().wireName() + "/" + directory.getName();
    }
}
