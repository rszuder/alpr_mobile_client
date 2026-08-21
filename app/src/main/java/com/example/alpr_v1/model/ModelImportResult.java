package com.example.alpr_v1.model;

public final class ModelImportResult {
    private final InstalledModel singleModel;
    private final InstalledAlprPackage completePackage;

    private ModelImportResult(InstalledModel singleModel, InstalledAlprPackage completePackage) {
        this.singleModel = singleModel;
        this.completePackage = completePackage;
    }

    public static ModelImportResult single(InstalledModel model) {
        return new ModelImportResult(model, null);
    }

    public static ModelImportResult complete(InstalledAlprPackage completePackage) {
        return new ModelImportResult(null, completePackage);
    }

    public boolean isCompletePackage() { return completePackage != null; }
    public InstalledModel singleModel() { return singleModel; }
    public InstalledAlprPackage completePackage() { return completePackage; }
}
