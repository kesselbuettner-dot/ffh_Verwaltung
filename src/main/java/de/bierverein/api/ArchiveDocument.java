package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="archive_documents")
public class ArchiveDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,length=160) public String title;
 @Column(length=2000) public String description="";
 @Column(nullable=false,length=20) public String visibility="RESTRICTED";
 @Column(nullable=false,length=80) public String category="GENERAL";
 public LocalDate expiresOn;
 @Column(nullable=false,length=100) public String owner;
 @Column(nullable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 public int currentVersion;
 public boolean archived;
 public LocalDate lastExpiryNotification;
 protected ArchiveDocument(){}
 public ArchiveDocument(String title,String description,String visibility,String category,LocalDate expiresOn,String owner){
  this.title=title;this.description=description;this.visibility=visibility;this.category=category;this.expiresOn=expiresOn;this.owner=owner;
 }
}
