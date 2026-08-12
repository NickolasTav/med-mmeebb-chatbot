package br.edu.unipam.tcc.repository;

import br.edu.unipam.tcc.entity.UnipamAcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnipamAcademicRecordRepository extends JpaRepository<UnipamAcademicRecord, Long> {

    Optional<UnipamAcademicRecord> findByPhoneNumber(String phoneNumber);

    Optional<UnipamAcademicRecord> findByRa(String ra);

    boolean existsByRa(String ra);
}
