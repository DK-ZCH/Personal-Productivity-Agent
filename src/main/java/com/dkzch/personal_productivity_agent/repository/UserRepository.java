package com.dkzch.personal_productivity_agent.repository;

import com.dkzch.personal_productivity_agent.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
