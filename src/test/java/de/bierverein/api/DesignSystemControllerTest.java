package de.bierverein.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesignSystemControllerTest {
 @Test void savesOnlyWhitelistedDesignTokensAndPreservesOtherSettings(){
  AppSettingsRepository repository=mock(AppSettingsRepository.class);
  AppSettings settings=new AppSettings();
  settings.setAppName("Bestehende Feuerwehr");
  settings.setMenuLayout("{\"version\":2,\"groups\":[],\"entries\":{},\"style\":{}}");
  settings.setPrimaryColor("#aabbcc");
  when(repository.findAll()).thenReturn(List.of(settings));
  when(repository.save(any(AppSettings.class))).thenAnswer(inv->inv.getArgument(0));
  var controller=new DesignSystemController(repository,new ObjectMapper());
  assertEquals(Map.of(),controller.get().tokens());
  var updated=controller.save(new DesignSystemController.DesignTokens(Map.of(
    "space","20px","radius","12px","controlHeight","48px",
    "pageWidth","1440px","textSize","16px","shadow","none")));
  assertEquals("12px",updated.tokens().get("radius"));
  assertEquals(updated.tokens(),controller.get().tokens());
  assertEquals("Bestehende Feuerwehr",settings.getAppName());
  assertEquals("#aabbcc",settings.getPrimaryColor());
  assertTrue(settings.getMenuLayout().contains("\"version\":2"));
  verify(repository,times(1)).save(settings);
 }
 @Test void rejectsUntrustedCssValuesAndUnknownProperties(){
  AppSettingsRepository repository=mock(AppSettingsRepository.class);
  var controller=new DesignSystemController(repository,new ObjectMapper());
  assertThrows(ResponseStatusException.class,()->controller.save(
    new DesignSystemController.DesignTokens(Map.of("radius","url(https://malicious.example)"))));
  assertThrows(ResponseStatusException.class,()->controller.save(
    new DesignSystemController.DesignTokens(Map.of("injectedClass","display:none"))));
  verify(repository,never()).save(any());
 }
 @Test void serverSideRolesProtectChangingGlobalStyles() throws Exception {
  var write=DesignSystemController.class.getMethod("save",DesignSystemController.DesignTokens.class);
  var read=DesignSystemController.class.getMethod("get");
  assertEquals("hasRole('ADMIN')",write.getAnnotation(PreAuthorize.class).value());
  assertEquals("isAuthenticated()",read.getAnnotation(PreAuthorize.class).value());
 }
}
