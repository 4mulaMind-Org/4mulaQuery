package com.formulaquery.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * ------------------------------------------------------------
 * User Repository
 * ------------------------------------------------------------
 * Handles database operations related to the User entity.
 *
 * Extends:
 * JpaRepository<User, Integer>
 *
 * Features:
 * - CRUD Operations
 * - Find user by email
 * - Check email existence
 * - Find user by phone number
 *
 * Spring Data JPA automatically generates
 * query implementations from method names.
 * ------------------------------------------------------------
 */

public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Finds a user using email address
     *
     * @param email user's email
     * @return Optional<User>
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether an email
     * already exists in database
     *
     * Used during registration
     * to prevent duplicate accounts
     *
     * @param email user's email
     * @return true if exists
     */
    boolean existsByEmail(String email);

    /**
     * Finds a user using phone number
     *
     * @param phone user's phone number
     * @return Optional<User>
     */
    Optional<User> findByPhone(String phone);
}