package br.edu.unipam.tcc.repository;

import br.edu.unipam.tcc.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCodeIgnoreCase(String code);

    Optional<Course> findFirstByActiveTrueOrderByIdAsc();
}
