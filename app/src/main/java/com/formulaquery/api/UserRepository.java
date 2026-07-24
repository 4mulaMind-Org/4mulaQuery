package com.formulaquery.api;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * Repository interface for performing database operations
 * on the User collection in MongoDB.
 *
 * <p>Spring Data MongoDB automatically provides the implementation
 * for this interface at runtime. By extending MongoRepository,
 * basic CRUD operations such as save(), findAll(), findById(),
 * delete(), etc. are available without writing any implementation.</p>
 *
 * <p>This repository also declares custom query methods for
 * searching users by email and checking email availability.</p>
 *
 * @author Abdul Qadir
 * @version 1.0
 */
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Finds a user by email address.
     *
     * @param email User's email address.
     * @return An Optional containing the User if found,
     *         otherwise Optional.empty().
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether a user already exists with the given email.
     *
     * @param email User's email address.
     * @return true if the email exists,
     *         otherwise false.
     */
    boolean existsByEmail(String email);
}