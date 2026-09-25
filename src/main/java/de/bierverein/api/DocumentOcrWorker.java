package de.bierverein.api;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes a few private archive versions per run; never transfers bytes over the network. */
@Service
public class DocumentOcrWorker {
 private final ArchiveDocumentRevisionRepository revisions;
 private final DocumentTextRecognitionService recognition;
 private final Path storage;
 private final String profiles;
 public DocumentOcrWorker(ArchiveDocumentRevisionRepository revisions,DocumentTextRecognitionService recognition,
  @Value("${app.private-documents.path:/app/private-documents}") String storage,
  @Value("${spring.profiles.active:}") String profiles){
  this.revisions=revisions;this.recognition=recognition;this.storage=Paths.get(storage).toAbsolutePath().normalize();
  this.profiles=profiles;
 }
 @Scheduled(fixedDelayString="${app.document-ocr.poll-ms:20000}")
 @Transactional
 public void poll(){
  if(!Arrays.asList(profiles.split(",")).contains("worker"))return;
  // A single worker container processes both new versions and revisions predating OCR.
  List<ArchiveDocumentRevision> pending=new ArrayList<>(revisions.findTop3ByExtractionMethodOrderByUploadedAtAsc("PENDING"));
  if(pending.size()<3)pending.addAll(revisions.findTop3ByExtractionMethodIsNullOrderByUploadedAtAsc().stream()
   .limit(3-pending.size()).toList());
  for(ArchiveDocumentRevision rev:pending)process(rev);
 }
 void process(ArchiveDocumentRevision rev){
  try{
   Path file=storage.resolve(rev.storageName).normalize();
   if(!file.startsWith(storage)||!Files.isRegularFile(file)||Files.size(file)>20L*1024*1024)
    throw new IllegalArgumentException("Private Dokumentendatei nicht vorhanden");
   byte[] data=Files.readAllBytes(file);
   var result=recognition.extract(data,rev.contentType);
   rev.extractedText=result.text();
   rev.extractionMethod=result.method();
   rev.extractionWarning=result.warning();
  }catch(Exception ex){
   rev.extractionMethod="FAILED";
   rev.extractionWarning="Die lokale Texterkennung konnte nicht abgeschlossen werden.";
  }
  revisions.save(rev);
 }
}
