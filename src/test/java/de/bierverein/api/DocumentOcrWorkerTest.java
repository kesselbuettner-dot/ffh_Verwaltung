package de.bierverein.api;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentOcrWorkerTest {
 @TempDir Path folder;
 @Test void workerIndexesPrivateDocumentWithoutExternalCalls()throws Exception{
  ArchiveDocumentRevisionRepository revisions=mock(ArchiveDocumentRevisionRepository.class);
  ArchiveDocumentRevision version=new ArchiveDocumentRevision(1L,1,"document.txt","safe-file","text/plain",12,"","tester");
  version.extractionMethod="PENDING";
  Files.write(folder.resolve("safe-file"),"Seriennummer: FL-2026-123".getBytes(StandardCharsets.UTF_8));
  when(revisions.findTop3ByExtractionMethodOrderByUploadedAtAsc("PENDING")).thenReturn(List.of(version));
  when(revisions.findTop3ByExtractionMethodIsNullOrderByUploadedAtAsc()).thenReturn(List.of());
  DocumentOcrWorker worker=new DocumentOcrWorker(revisions,new DocumentTextRecognitionService(),folder.toString(),"docker,worker");
  worker.poll();
  assertEquals("TEXT",version.extractionMethod);
  assertTrue(version.extractedText.contains("FL-2026-123"));
  verify(revisions).save(version);
 }
 @Test void webApplicationProfileNeverRunsBackgroundOcr(){
  ArchiveDocumentRevisionRepository revisions=mock(ArchiveDocumentRevisionRepository.class);
  var worker=new DocumentOcrWorker(revisions,new DocumentTextRecognitionService(),folder.toString(),"docker");
  worker.poll();
  verifyNoInteractions(revisions);
 }
 @Test void missingPrivateFileFailsWithoutLeakingItsPath(){
  ArchiveDocumentRevisionRepository revisions=mock(ArchiveDocumentRevisionRepository.class);
  var version=new ArchiveDocumentRevision(1L,1,"deleted.pdf","missing","application/pdf",5,"","tester");
  var worker=new DocumentOcrWorker(revisions,new DocumentTextRecognitionService(),folder.toString(),"docker,worker");
  worker.process(version);
  assertEquals("FAILED",version.extractionMethod);
  assertFalse(version.extractionWarning.contains(folder.toString()));
  verify(revisions).save(version);
 }
}
