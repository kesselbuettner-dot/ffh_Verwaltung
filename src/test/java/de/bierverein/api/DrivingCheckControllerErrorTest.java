package de.bierverein.api;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrivingCheckControllerErrorTest {
 @Test void safeScanErrorIsVisibleToMobileUserWithoutPhotoOrReference(){
  var controller=new DrivingCheckController(mock(DrivingCheckService.class),mock(FireDrivingReminderService.class));
  var result=controller.scanError(new ResponseStatusException(HttpStatus.CONFLICT,
    "Foto wurde übertragen, aber der geschützte Datenabgleich ist fehlgeschlagen."));
  assertEquals(HttpStatus.CONFLICT,result.getStatusCode());
  assertEquals("no-store",result.getHeaders().getCacheControl());
  assertEquals("Foto wurde übertragen, aber der geschützte Datenabgleich ist fehlgeschlagen.",
    result.getBody().get("detail"));
  assertFalse(result.getBody().containsKey("imageData"));
  assertFalse(result.getBody().containsKey("licenseNumber"));
 }
}