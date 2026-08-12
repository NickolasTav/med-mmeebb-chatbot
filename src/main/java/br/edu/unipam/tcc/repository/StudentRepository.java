package br.edu.unipam.tcc.repository;

import br.edu.unipam.tcc.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByPhoneNumber(String phoneNumber);
    Optional<Student> findByRa(String ra);
    boolean existsByRa(String ra);
}
