package com.formulaquery.api;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DatabaseConnectionRepository extends MongoRepository<DatabaseConnectionConfig, String> {
}