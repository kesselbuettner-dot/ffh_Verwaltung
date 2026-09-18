package de.bierverein.api;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="training_documents")
public class TrainingDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String title; @Column(length=2000) private String description;
 @Column(nullable=false) private String originalFileName; @Column(nullable=false,unique=true) private String storedFileName;
 @Column(nullable=false,length=120) private String contentType; @Column(nullable=false) private long fileSize;
 @Column(nullable=false) private Instant uploadedAt; @Column(nullable=false) private String uploadedBy;
 public TrainingDocument(){} public Long getId(){return id;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
 public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public String getOriginalFileName(){return originalFileName;} public void setOriginalFileName(String v){originalFileName=v;}
 public String getStoredFileName(){return storedFileName;} public void setStoredFileName(String v){storedFileName=v;}
 public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
 public long getFileSize(){return fileSize;} public void setFileSize(long v){fileSize=v;}
 public Instant getUploadedAt(){return uploadedAt;} public void setUploadedAt(Instant v){uploadedAt=v;}
 public String getUploadedBy(){return uploadedBy;} public void setUploadedBy(String v){uploadedBy=v;}
}