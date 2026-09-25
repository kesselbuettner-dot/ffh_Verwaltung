package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArchiveDocumentRevisionRepository extends JpaRepository<ArchiveDocumentRevision,Long>{
 List<ArchiveDocumentRevision> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
 Optional<ArchiveDocumentRevision> findByDocumentIdAndVersionNumber(Long documentId,int versionNumber);
}
