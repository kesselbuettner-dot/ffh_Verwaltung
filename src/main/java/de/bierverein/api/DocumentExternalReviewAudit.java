package de.bierverein.api;
import jakarta.persistence.*;
import java.time.Instant;
/** Consent metadata only; transferred texts and AI responses are not persisted. */
@Entity @Table(name="document_external_review_audit")
public class DocumentExternalReviewAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false) public Long documentId;
 @Column(nullable=false) public int versionNumber;
 @Column(nullable=false,length=100) public String approvedBy;
 @Column(nullable=false) public Instant approvedAt=Instant.now();
 @Column(nullable=false,length=64) public String contentSha256;
 @Column(nullable=false,length=120) public String providerHost;
 @Column(nullable=false,length=20) public String result="REQUESTED";
 protected DocumentExternalReviewAudit(){}
 public DocumentExternalReviewAudit(Long id,int version,String user,String sha,String host){
  documentId=id;versionNumber=version;approvedBy=user;contentSha256=sha;providerHost=host;
 }
}
