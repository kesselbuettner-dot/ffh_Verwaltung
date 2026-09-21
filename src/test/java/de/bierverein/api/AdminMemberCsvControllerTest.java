package de.bierverein.api;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminMemberCsvControllerTest {
 private final MemberService service=mock(MemberService.class);
 private final MemberRepository members=mock(MemberRepository.class);
 private final MemberExtraRepository extra=mock(MemberExtraRepository.class);
 private final AdminMemberCsvController controller=new AdminMemberCsvController(service,members,extra);
 @Test void googleContactsCsvNeedsPreviewAndImportsWithoutCreatingLogin(){
  when(members.findAll()).thenReturn(List.of());
  String csv="Given Name,Family Name,E-mail 1 - Value,Phone 1 - Value,Birthday\n"+
      "Max,Muster,max@example.org,034112345,2000-01-02\n";
  var preview=controller.preview(new AdminMemberCsvController.CsvInput(csv));
  assertEquals(1,preview.newCount());
  assertEquals(0,preview.errors());
  assertEquals("Max Muster",preview.rows().getFirst().name());
  assertEquals("max@example.org",preview.rows().getFirst().email());
  Authentication auth=mock(Authentication.class);
  when(service.create(any(MemberDtos.MemberRequest.class),eq(auth)))
    .thenReturn(new MemberDtos.MemberResponse(99L,"Max Muster",null,null,"max@example.org",null,null,true,
          java.math.BigDecimal.ZERO,null,null,null,false));
  var result=controller.commit(new AdminMemberCsvController.CsvInput(csv),auth);
  assertEquals(1,result.imported());
  verify(service).create(argThat(r->r.loginEnabled()==null&&r.username()==null&&r.password()==null),eq(auth));
  verify(extra).save(argThat(profile->profile.memberId.equals(99L)
      &&profile.birthDate.equals(java.time.LocalDate.of(2000,1,2))));
 }
 @Test void ambiguousExistingNameIsSkippedNotSilentlyOverwritten(){
  Member existing=new Member();existing.setName("Max Muster");
  when(members.findAll()).thenReturn(List.of(existing));
  String csv="Name;E-Mail\nMax Muster;new@example.org\n";
  var preview=controller.preview(new AdminMemberCsvController.CsvInput(csv));
  assertEquals(1,preview.duplicates());assertEquals(0,preview.newCount());
  verifyNoInteractions(service);
 }
 @Test void malformedDateNeverImports(){
  when(members.findAll()).thenReturn(List.of());
  var preview=controller.preview(new AdminMemberCsvController.CsvInput("Name;Geburtsdatum\nMax Muster;unbekannt\n"));
  assertEquals(1,preview.errors());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,
    ()->controller.commit(new AdminMemberCsvController.CsvInput("Name;Geburtsdatum\nMax Muster;unbekannt\n"),mock(Authentication.class)));
  verifyNoInteractions(service);
 }
}
