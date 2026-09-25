package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface ArchiveDocumentRepository extends JpaRepository<ArchiveDocument,Long>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select d from ArchiveDocument d where d.id=:id")
 java.util.Optional<ArchiveDocument> findLocked(@org.springframework.data.repository.query.Param("id") Long id);
 List<ArchiveDocument> findByArchivedFalseOrderByUpdatedAtDesc();
 List<ArchiveDocument> findByArchivedFalseAndExpiresOnLessThanEqual(LocalDate date);
}
