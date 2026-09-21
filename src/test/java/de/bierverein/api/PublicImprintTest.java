package de.bierverein.api;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicImprintTest {
    @Test
    void publicReadOnlyImprintUsesConfiguredOrganizationAndNoAdminSettings() {
        AppSettingsRepository repository = mock(AppSettingsRepository.class);
        AppSettings settings = new AppSettings();
        settings.setOrganizationName("Freiwillige Feuerwehr Holzhausen");
        settings.setStreet("Beispielstraße 1");
        settings.setCity("Leipzig");
        settings.setPostalCode("04288");
        settings.setContactEmail("info@example.org");
        settings.setLegalRepresentative("Beispielperson");
        when(repository.findAll()).thenReturn(List.of(settings));
        AppSettingsController.ImprintDto result = new AppSettingsController(repository).imprint();
        assertEquals("Freiwillige Feuerwehr Holzhausen", result.organizationName());
        assertEquals("Leipzig", result.city());
        assertEquals("info@example.org", result.contactEmail());
        assertEquals("Eric Kessel-Büttner", result.softwareAuthor());
        assertTrue(result.licenseNotice().contains("Alle Rechte vorbehalten"));
        verify(repository, never()).save(any());
    }
}
