package br.edu.unipam.tcc.repository;

import br.edu.unipam.tcc.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    Optional<ReviewSchedule> findByStudentIdAndQuestionId(UUID studentId, Long questionId);

    List<ReviewSchedule> findByStudentId(UUID studentId);

    @Query("SELECT rs FROM ReviewSchedule rs " +
           "JOIN FETCH rs.student s " +
           "JOIN FETCH rs.question q " +
           "JOIN FETCH q.options " +
           "WHERE rs.nextDueDate <= :dueDate AND rs.status IN ('PENDING', 'OVERDUE') AND s.active = true")
    List<ReviewSchedule> findPendingReviewsForDate(@Param("dueDate") LocalDate dueDate);

    @Query("SELECT rs FROM ReviewSchedule rs " +
           "JOIN FETCH rs.question q " +
           "JOIN FETCH q.options " +
           "WHERE rs.student.id = :studentId AND rs.status = 'NOTIFIED' " +
           "ORDER BY rs.updatedAt DESC")
    List<ReviewSchedule> findCurrentlyAwaitingAnswer(@Param("studentId") UUID studentId);
}
