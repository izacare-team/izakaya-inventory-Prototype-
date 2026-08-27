package com.izacare.repository;

import com.izacare.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByStoreIdOrderByIdAsc(Long storeId);
    boolean existsByStoreIdAndName(Long storeId, String name);
    Optional<Course> findByStoreIdAndName(Long storeId, String name);
}
