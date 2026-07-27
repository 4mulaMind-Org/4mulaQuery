/**
 * ============================================================================
 *                          FlexibleRecord
 * ============================================================================
 *
 * Purpose:
 * Represents a dynamic database record stored in MongoDB.
 *
 * Features:
 * • Stores records with flexible (dynamic) fields.
 * • Supports datasets having different column structures.
 * • Automatically records the import timestamp.
 *
 * Collection:
 *      flexible_records
 *
 * Fields:
 * • id          -> Unique MongoDB document ID.
 * • datasetName -> Name of the imported dataset.
 * • fields      -> Dynamic key-value pairs of record data.
 * • importedAt  -> Date and time when the record was imported.
 *
 * Example:
 * Dataset : Students
 *
 * {
 *     "Name"  : "John",
 *     "Email" : "john@gmail.com",
 *     "Age"   : "21"
 * }
 *
 * Author  : FormulaMind
 * Project : FormulaQuery
 * ============================================================================
 */
package com.formulaquery.api;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;
import java.time.LocalDateTime;

@Document(collection = "flexible_records")
public class FlexibleRecord {

    @Id
    private String id;
    private String datasetName;
    private Map<String, String> fields;
    private LocalDateTime importedAt;

    public FlexibleRecord() {}

    public FlexibleRecord(String datasetName, Map<String, String> fields) {
        this.datasetName = datasetName;
        this.fields = fields;
        this.importedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}