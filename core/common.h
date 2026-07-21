#ifndef COMMON_H
#define COMMON_H

#include <stdint.h>

/*
------------------------------------------------------------
4mulaQuery - Core Database Definitions
------------------------------------------------------------
This header defines the fundamental constants, data structures,
and node configuration parameters used by the B+ Tree storage
engine of the 4mulaQuery database system.
------------------------------------------------------------
*/


/* Maximum length allowed for username field */
const uint32_t USERNAME_SIZE = 32;

/* Maximum length allowed for email field */
const uint32_t EMAIL_SIZE = 255;

/* Fixed database page size (4KB) */
const uint32_t PAGE_SIZE = 4096;

/* Special marker representing an invalid or null page */
const uint32_t INVALID_PAGE = 0xFFFFFFFF;

/* Maximum number of pages that the table can allocate */
const uint32_t TABLE_MAX_PAGES = 400;


/*
------------------------------------------------------------
Row Structure
------------------------------------------------------------
Represents a single record stored in the database table.
Each row corresponds to one user entry.
------------------------------------------------------------
*/
struct Row {
    uint32_t id;                     /* Unique user identifier */
    char username[USERNAME_SIZE];    /* Username field */
    char email[EMAIL_SIZE];          /* Email address field */
};


/*
------------------------------------------------------------
B+ Tree Node Types
------------------------------------------------------------
LEAF_NODE     : Stores actual data rows
INTERNAL_NODE : Stores keys and child page references
------------------------------------------------------------
*/
enum NodeType : uint8_t {
    LEAF_NODE = 0,
    INTERNAL_NODE = 1
};


/*
------------------------------------------------------------
Leaf Node Layout Configuration
------------------------------------------------------------
Defines memory layout for leaf nodes where actual records
are stored in the B+ Tree structure.
------------------------------------------------------------
*/

/* Header size for a leaf node */
const uint32_t LEAF_HDR_SIZE = 14;

/* Size of the key stored in leaf nodes */
const uint32_t LEAF_KEY_SIZE = 4;

/* Size of the row payload stored in leaf nodes */
const uint32_t LEAF_VALUE_SIZE = sizeof(Row);

/* Total size of a single leaf cell (key + row data) */
const uint32_t LEAF_CELL_SIZE = LEAF_KEY_SIZE + LEAF_VALUE_SIZE;

/* Maximum number of cells that fit in a leaf node */
const uint32_t LEAF_MAX_CELLS = (PAGE_SIZE - LEAF_HDR_SIZE) / LEAF_CELL_SIZE;

/* Number of cells moved to the right node during a split */
const uint32_t LEAF_R_SPLIT = (LEAF_MAX_CELLS + 1) / 2;

/* Number of cells retained in the left node during a split */
const uint32_t LEAF_L_SPLIT = (LEAF_MAX_CELLS + 1) - LEAF_R_SPLIT;


/*
------------------------------------------------------------
Internal Node Layout Configuration
------------------------------------------------------------
Defines structure of internal nodes which maintain tree
hierarchy by storing keys and child page references.
------------------------------------------------------------
*/

/* Header size for an internal node */
const uint32_t INT_HDR_SIZE = 14;

/* Size of the key stored in internal nodes */
const uint32_t INT_KEY_SIZE = 4;

/* Size of child page pointer */
const uint32_t INT_CHILD_SIZE = 4;

/* Total size of one internal node cell */
const uint32_t INT_CELL_SIZE = INT_KEY_SIZE + INT_CHILD_SIZE;

/* Maximum number of keys allowed in an internal node */
const uint32_t INT_MAX_KEYS =
    (PAGE_SIZE - INT_HDR_SIZE - INT_CHILD_SIZE) / INT_CELL_SIZE;

#endif