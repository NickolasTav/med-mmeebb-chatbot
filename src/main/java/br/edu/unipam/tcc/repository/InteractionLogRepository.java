package br.edu.unipam.tcc.repository;

import br.edu.unipam.tcc.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {
    List<InteractionLog> findByStudentIdOrderByAnsweredAtDesc(UUID studentId);
}
