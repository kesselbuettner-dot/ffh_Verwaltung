package de.bierverein.api;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentCenterAccessTest {
 private final ArchiveDocumentRepository docs=mock(ArchiveDocumentRepository.class);
 private final ArchiveDocumentRevisionRepository revisions=mock(ArchiveDocumentRevisionRepository.class);
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final EffectivePermissionService permissions=mock(EffectivePermissionService.class);
 private final DocumentCenterController controller=new DocumentCenterController(docs,revisions,users,permissions,new DocumentTextRecognitionService(),new DocumentSuggestionsService(),mock(DeviceRepository.class),mock(ExternalDocumentReviewService.class),"/tmp/document-test-private");
 private AppUser user(long id,Role role){
  AppUser u=new AppUser();ReflectionTestUtils.setField(u,"id",id);u.setUsername("test"+id);u.setRole(role);
  u.setEnabled(true);u.setRegistrationApproved(true);
  when(users.findByUsernameWithMember(u.getUsername())).thenReturn(Optional.of(u));
  when(users.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
  return u;
 }
 private Authentication auth(AppUser u){Authentication a=mock(Authentication.class);when(a.getName()).thenReturn(u.getUsername());return a;}
 @Test void aReaderCannotSeeConfidentialDocumentsEvenIfTheyKnowTheId(){
  AppUser reader=user(1L,Role.MEMBER);
  when(permissions.hasPermission(1L,"documents.read")).thenReturn(true);
  ArchiveDocument publicDoc=new ArchiveDocument("Public","", "MEMBERS","GENERAL",null,"admin");
  ArchiveDocument restricted=new ArchiveDocument("Secret","", "RESTRICTED","DEVICE",null,"admin");
  publicDoc.id=11L;restricted.id=12L;
  when(docs.findByArchivedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(publicDoc,restricted));
  when(docs.findById(12L)).thenReturn(Optional.of(restricted));
  var result=controller.list("",auth(reader));
  assertEquals(1,result.documents().size());
  assertEquals("Public",result.documents().get(0).title());
  assertThrows(ResponseStatusException.class,()->controller.versions(12L,auth(reader)));
  verify(revisions,never()).findByDocumentIdOrderByVersionNumberDesc(12L);
 }
 @Test void aMemberCannotPublishConfidentialFiles(){
  AppUser member=user(1L,Role.MEMBER);
  when(permissions.hasPermission(1L,"documents.write")).thenReturn(true);
  var confidential=new DocumentCenterController.Input("Protected","","GENERAL","RESTRICTED",null);
  assertThrows(ResponseStatusException.class,()->controller.create(confidential,null,auth(member)));
  verifyNoInteractions(revisions);
 }
 @Test void externalReviewMustBeFromBoardAndCannotTransferConfidentialDocuments(){
  AppUser board=user(5L,Role.VORSTAND),member=user(6L,Role.MEMBER);
  when(permissions.hasPermission(6L,"documents.read")).thenReturn(true);
  ArchiveDocument privateDoc=new ArchiveDocument("Confidential","", "RESTRICTED","GENERAL",null,"test5");
  privateDoc.id=55L;
  when(docs.findById(55L)).thenReturn(Optional.of(privateDoc));
  var approved=new DocumentCenterController.ExternalReviewRequest(true,"Nonconfidential excerpt");
  assertThrows(ResponseStatusException.class,()->controller.externalReview(55L,1,approved,auth(board)));
  assertThrows(ResponseStatusException.class,()->controller.externalReview(55L,1,approved,auth(member)));
  verify(revisions,never()).findByDocumentIdAndVersionNumber(eq(55L),anyInt());
 }
 @Test void externalReviewRequiresExplicitAffirmation(){
  AppUser board=user(5L,Role.VORSTAND);
  ArchiveDocument ordinary=new ArchiveDocument("Non-confidential","", "MEMBERS","GENERAL",null,"test5");
  ordinary.id=56L;
  when(docs.findById(56L)).thenReturn(Optional.of(ordinary));
  var revision=new ArchiveDocumentRevision(56L,1,"manual.pdf","storage","application/pdf",5L,"text","test5");
  when(revisions.findByDocumentIdAndVersionNumber(56L,1)).thenReturn(Optional.of(revision));
  assertThrows(ResponseStatusException.class,()->controller.externalReview(56L,1,
    new DocumentCenterController.ExternalReviewRequest(false,"text"),auth(board)));
 }
 @Test void onlyApprovedMessagesAreShownOnTheTelevision(){
  var messages=mock(DashboardMessageRepository.class);
  var dashboard=new DashboardMessageController(messages,mock(DashboardMessageReadRepository.class),
   mock(AppSettingsRepository.class),users,mock(WebPushService.class));
  AppUser account=user(1L,Role.MEMBER);
  DashboardMessage approved=new DashboardMessage();approved.setType("MESSAGE");approved.setTitle("Allowed");approved.setActive(true);approved.setOnWallboard(true);
  DashboardMessage privateMessage=new DashboardMessage();privateMessage.setType("MESSAGE");privateMessage.setTitle("Private");privateMessage.setActive(true);
  DashboardMessage inactive=new DashboardMessage();inactive.setType("MESSAGE");inactive.setTitle("Inactive");inactive.setActive(false);inactive.setOnWallboard(true);
  when(messages.findAllByOrderByPriorityDescEventAtAscCreatedAtDesc()).thenReturn(List.of(approved,privateMessage,inactive));
  var result=dashboard.wallboard(auth(account));
  assertEquals(1,result.size());assertEquals("Allowed",result.get(0).title());
  assertThrows(ResponseStatusException.class,()->dashboard.wallboard(null));
 }
}
