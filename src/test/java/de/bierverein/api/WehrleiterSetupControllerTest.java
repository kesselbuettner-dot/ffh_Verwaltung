package de.bierverein.api;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WehrleiterSetupControllerTest {
 @Test void neverRestoresRevokedPermissionsOnExistingRole(){
  var roles=mock(ManagedRoleRepository.class);
  var permissions=mock(ManagedRolePermissionRepository.class);
  var role=new ManagedRole("WEHRLEITER","Wehrleiter",null,false);
  when(roles.findByCode("WEHRLEITER")).thenReturn(Optional.of(role));
  var result=new WehrleiterSetupController(roles,permissions).createRole();
  assertFalse(result.created());
  verify(roles,never()).save(any());
  verifyNoInteractions(permissions);
 }
}
