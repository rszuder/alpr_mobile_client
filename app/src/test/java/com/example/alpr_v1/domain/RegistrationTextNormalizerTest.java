package com.example.alpr_v1.domain;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegistrationTextNormalizerTest {
    @Test public void foldsCaseAndSeparatorsWithoutSemanticCorrections() {
        for (String raw : new String[]{"AAA123","aaa123","AaA123","AA A-123"})
            assertEquals("AAA123",RegistrationTextNormalizer.registrationKey(raw));
        assertEquals("WX123AB",RegistrationTextNormalizer.registrationKey("wx 123ab"));
        assertEquals("O0I1",RegistrationTextNormalizer.registrationKey("o0i1"));
        assertEquals("",RegistrationTextNormalizer.registrationKey(null));
        assertEquals("",RegistrationTextNormalizer.registrationKey(" - \n"));
    }
    @Test public void usesRootLocaleAndFullUnicodeCodePoints() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr","TR"));
            assertEquals("I123",RegistrationTextNormalizer.registrationKey("i123"));
            assertEquals("SS\u00b2\u2163",RegistrationTextNormalizer.registrationKey("\u00df\u00b2\u2173"));
            assertEquals("\ud801\udc00",RegistrationTextNormalizer.registrationKey("\ud801\udc28"));
            assertEquals("A",RegistrationTextNormalizer.registrationKey("a\u0301"));
        } finally { Locale.setDefault(original); }
    }
}
