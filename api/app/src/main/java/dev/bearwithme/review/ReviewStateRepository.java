package dev.bearwithme.review;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewStateRepository extends JpaRepository<ReviewState, Long> {
}
