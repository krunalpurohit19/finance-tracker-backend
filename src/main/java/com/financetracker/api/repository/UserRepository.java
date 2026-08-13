package com.financetracker.api.repository;

import com.financetracker.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndDeletedAtIsNull(String id);
    boolean existsByEmail(String email);
}
