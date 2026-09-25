package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="archive_document_revisions",uniqueConstraints=@UniqueConstraint(columnNames={"document_id","version_number"}))
public class ArchiveDocumentRevision {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="document_id",nullable=false) public Long documentId;
 @Column(name="version_number",nullable=false) public int versionNumber;
 @Column(nullable=false,length=250) public String originalName;
 @Column(nullable=false,length=120) public String storageName;
 @Column(nullable=false,length=120) public String contentType;
 @Column(nullable=false) public long sizeBytes;
 @Column(length=22000) public String extractedText="";
 @Column(length=24) public String extractionMethod="PENDING";
 @Column(length=260) public String extractionWarning;
 @Column(nullable=false,length=100) public String uploadedBy;
 @Column(nullable=false) public Instant uploadedAt=Instant.now();
 protected ArchiveDocumentRevision(){}
 public ArchiveDocumentRevision(Long doc,int version,String file,String storage,String type,long size,String text,String user){
  documentId=doc;versionNumber=version;originalName=file;storageName=storage;contentType=type;sizeBytes=size;extractedText=text;uploadedBy=user;
 }
}
