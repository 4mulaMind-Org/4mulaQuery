/**
 * ============================================================================
 *                     FlexibleRecordRepository
 * ============================================================================
 *
 * Purpose:
 * Provides database operations for FlexibleRecord documents
 * stored in MongoDB.
 *
 * Features:
 * • Save flexible records.
 * • Retrieve records by dataset name.
 * • Delete an entire dataset.
 * • Count records in a dataset.
 * • Supports all default MongoRepository CRUD operations.
 *
 * Collection:
 *      flexible_records
 *
 * Inherits:
 *      MongoRepository<FlexibleRecord, String>
 *
 * Author  : FormulaMind
 * Project : FormulaQuery
 * ============================================================================
 */

package com.formulaquery.api;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface FlexibleRecordRepository extends MongoRepository<FlexibleRecord, String> {
    List<FlexibleRecord> findByDatasetName(String datasetName);
    void deleteByDatasetName(String datasetName);
    long countByDatasetName(String datasetName);
}