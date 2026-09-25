package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface ArchiveDocumentRepository extends JpaRepository<ArchiveDocument,Long>{
 List<ArchiveDocument> findByArchivedFalseOrderByUpdatedAtDesc();
 List<ArchiveDocument> findByArchivedFalseAndExpiresOnLessThanEqual(LocalDate date);
}
