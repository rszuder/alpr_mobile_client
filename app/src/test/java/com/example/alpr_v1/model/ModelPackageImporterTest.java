package com.example.alpr_v1.model;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public final class ModelPackageImporterTest {
    @Test
    public void acceptsRelativePosixPaths() throws Exception {
        ModelPackageImporter.validateZipPath("variants/tflite/model.tflite");
        ModelPackageImporter.validateZipPath("models/plate/");
    }

    @Test
    public void rejectsTraversalAbsoluteAndNonPosixPaths() {
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("models/../manifest.json")
        );
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("/manifest.json")
        );
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("models\\plate\\model.alprmodel")
        );
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("C:/manifest.json")
        );
    }

    @Test
    public void rejectsAmbiguousPathSegments() {
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("models//manifest.json")
        );
        assertThrows(
                ModelPackageException.class,
                () -> ModelPackageImporter.validateZipPath("./manifest.json")
        );
    }
}
