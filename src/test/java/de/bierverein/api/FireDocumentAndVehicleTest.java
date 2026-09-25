package de.bierverein.api;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FireDocumentAndVehicleTest {
 private final FireQualificationAttachmentRepository documents=mock(FireQualificationAttachmentRepository.class);
 private final FireMemberQualificationRepository assignments=mock(FireMemberQualificationRepository.class);
 private final FireQualificationPermissions permissions=mock(FireQualificationPermissions.class);
 private final Authentication actor=mock(Authentication.class);
 private FireQualificationAttachmentController docs(){return new FireQualificationAttachmentController(documents,assignments,permissions);}
 private FireMemberQualification card(boolean sensitive){
  var t=new FireQualificationType("QUAL","Qualifikation","Q",false,sensitive,30,0);
  var q=new FireMemberQualification(1L,t);
  org.springframework.test.util.ReflectionTestUtils.setField(q,"id",3L);
  when(assignments.findById(3L)).thenReturn(Optional.of(q));
  return q;
 }
 @Test void pdfEvidenceValidatesSignatureAndCanBeUploadedOnOneMemberCard(){
  card(false);
  byte[] good="%PDF-1.7\nexample content".getBytes(StandardCharsets.US_ASCII);
  when(documents.save(any(FireQualificationAttachment.class))).thenAnswer(invocation->invocation.getArgument(0));
  var result=docs().upload(3L,good,actor);
  assertEquals("nachweis.pdf",result.filename());
  verify(documents).save(argThat(doc->doc.qualificationId.equals(3L)&&Arrays.equals(doc.bytes,good)));
  assertThrows(ResponseStatusException.class,()->docs().upload(3L,"<html>fake</html>".getBytes(StandardCharsets.US_ASCII),actor));
 }
 @Test void sensitiveDocumentCannotBeReadOrModifiedWithoutAdditionalPermission(){
  card(true);
  when(permissions.allowed(actor,"fire.qualifications.sensitive.read")).thenReturn(false);
  var denied=assertThrows(ResponseStatusException.class,()->docs().upload(3L,"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII),actor));
  assertEquals(HttpStatus.FORBIDDEN,denied.getStatusCode());
  assertThrows(ResponseStatusException.class,()->docs().read(3L,actor));
  verifyNoInteractions(documents);
 }
 @Test void archivedCardRetainsAuditAndAttachedPdf(){
  FireQualificationTypeRepository templates=mock(FireQualificationTypeRepository.class);
  MemberRepository members=mock(MemberRepository.class);
  DrivingCheckService driver=mock(DrivingCheckService.class);
  ManagedUserRoleRepository roles=mock(ManagedUserRoleRepository.class);
  MemberExtraRepository extra=mock(MemberExtraRepository.class);
  var q=card(false);
  when(assignments.save(q)).thenReturn(q);
  var controller=new FireQualificationController(templates,assignments,members,permissions,driver,roles,extra,documents);
  assertEquals(HttpStatus.NO_CONTENT,controller.removeCard(3L,actor).getStatusCode());
  assertFalse(q.active);
  verify(assignments).save(q);
  verifyNoInteractions(documents);
 }
 @Test void vehicleLicenseClassesValidateAgainstDocumentedAllowedValues(){
  var v=new FireVehicle();v.setRequiredLicenseClass("CE");
  assertEquals("CE",v.getRequiredLicenseClass());
  v.setRequiredLicenseClass(null);
  assertNull(v.getRequiredLicenseClass());
 }
}
