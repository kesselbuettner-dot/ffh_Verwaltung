package de.bierverein.api;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireQualificationReminderRepository extends JpaRepository<FireQualificationReminder,Long>{
 boolean existsByQualificationIdAndDueDateAndRecipient(Long qualificationId,LocalDate dueDate,String recipient);
}
