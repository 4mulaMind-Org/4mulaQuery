/*
============================================================
4mulaQuery — btree.h
B+ Tree Implementation

Features:
  • O(log n) insert, search, delete
  • Leaf linked list → fast range scan / select all
  • Page-based disk storage (SQLite-style)
  • Root always at page 0

Capacity:
  • 13 records per leaf node
  • 509 keys per internal node
  • Depth 2 → 6,617 records
  • Depth 3 → 3,369,353 records
============================================================
*/
#ifndef BTREE_H
#define BTREE_H

#include <iostream>
#include <fstream>
#include <cstring>
#include <vector>
#include <filesystem>
#include "common.h"

/*
------------------------------------------------------------
BTree Class
Implements a disk-backed B+ Tree used as the primary storage
engine for table rows.

Key responsibilities:
• Manage fixed-size pages
• Maintain B+ tree structure
• Handle insert/search/delete operations
• Persist pages to disk
------------------------------------------------------------
*/
class BTree {
private:

    /* ----------------------------------------------------
       PAGE CACHE
       ----------------------------------------------------
       pages[] holds in-memory copies of disk pages.
       Pages are lazily loaded from disk when first accessed.
    ---------------------------------------------------- */
    char*       pages[TABLE_MAX_PAGES];

    /* Total number of pages currently in the database file */
    uint32_t    num_pages;

    /* File name of the table storage */
    std::string filename;

    /* File stream used for disk I/O */
    std::fstream file;

    /* ──────────────────────────────────────────────────
       PAGE MANAGEMENT
    ────────────────────────────────────────────────── */

    /*
    ----------------------------------------------------
    get_page(page_num)

    Returns pointer to page in memory.
    If page is not yet loaded, it is allocated and
    loaded from disk.

    Behaviour:
    • Allocate new PAGE_SIZE buffer
    • Load page from file if it exists
    • Otherwise initialize new empty page
    ----------------------------------------------------
    */
    char* get_page(uint32_t page_num) {
        if (!pages[page_num]) {

            /* Allocate memory for page */
            pages[page_num] = new char[PAGE_SIZE]();

            /* Load page from disk if it already exists */
            if (page_num < num_pages) {
                file.clear();
                file.seekg(page_num * PAGE_SIZE);
                file.read(pages[page_num], PAGE_SIZE);
            }
            else {
                /* Page is new → increase page count */
                num_pages = page_num + 1;
            }
        }
        return pages[page_num];
    }

    /*
    ----------------------------------------------------
    flush(page_num)

    Writes a page from memory back to disk.
    Called during destruction or manual flush.

    Ensures durability of page data.
    ----------------------------------------------------
    */
    void flush(uint32_t page_num) {
        if (!pages[page_num]) return;

        file.clear();
        file.seekp(page_num * PAGE_SIZE);
        file.write(pages[page_num], PAGE_SIZE);
        file.flush();
    }

    /*
    ----------------------------------------------------
    new_page()

    Returns next free page number.

    Pages are allocated sequentially.
    ----------------------------------------------------
    */
    uint32_t new_page() { return num_pages; }

    /* ──────────────────────────────────────────────────
       FIELD ACCESSORS — Common Node Header
    ────────────────────────────────────────────────── */

    /* Get node type (leaf/internal) */
    NodeType get_type(char* n)            { return *(NodeType*)(n);     }

    /* Set node type */
    void     set_type(char* n, NodeType t){ *(NodeType*)(n) = t;        }

    /* Check if node is root */
    bool     is_root(char* n)             { return *(uint8_t*)(n+1);    }

    /* Set root flag */
    void     set_root(char* n, bool v)    { *(uint8_t*)(n+1) = v;       }

    /* Get parent page number */
    uint32_t get_parent(char* n)          { return *(uint32_t*)(n+2);   }

    /* Set parent page */
    void     set_parent(char* n, uint32_t p){ *(uint32_t*)(n+2) = p;   }

    /* ──────────────────────────────────────────────────
       FIELD ACCESSORS — Leaf Node
    ────────────────────────────────────────────────── */

    /* Number of cells in leaf node */
    uint32_t leaf_ncells(char* n)           { return *(uint32_t*)(n+6);  }

    /* Set number of cells */
    void     leaf_set_ncells(char* n, uint32_t v){ *(uint32_t*)(n+6)=v; }

    /* Pointer to next leaf (for linked list scan) */
    uint32_t leaf_next(char* n)             { return *(uint32_t*)(n+10); }

    /* Set next leaf pointer */
    void     leaf_set_next(char* n, uint32_t v){ *(uint32_t*)(n+10)=v;  }

    /*
    ----------------------------------------------------
    leaf_cell

    Returns pointer to a specific leaf cell.
    Layout:
      [ key | row payload ]
    ----------------------------------------------------
    */
    char*    leaf_cell(char* n, uint32_t i) {
        return n + LEAF_HDR_SIZE + i * LEAF_CELL_SIZE;
    }

    /* Get key stored in leaf cell */
    uint32_t leaf_key(char* n, uint32_t i)  {
        return *(uint32_t*)leaf_cell(n,i);
    }

    /* Set key in leaf cell */
    void     leaf_set_key(char* n, uint32_t i, uint32_t k){
        *(uint32_t*)leaf_cell(n,i) = k;
    }

    /* Get row payload stored in leaf cell */
    Row*     leaf_val(char* n, uint32_t i)  {
        return (Row*)(leaf_cell(n,i) + LEAF_KEY_SIZE);
    }

    /* ──────────────────────────────────────────────────
       FIELD ACCESSORS — Internal Node
    ────────────────────────────────────────────────── */

    /* Number of keys in internal node */
    uint32_t int_nkeys(char* n)             { return *(uint32_t*)(n+6);  }

    /* Set number of keys */
    void     int_set_nkeys(char* n, uint32_t v){ *(uint32_t*)(n+6)=v;   }

    /* Rightmost child pointer */
    uint32_t int_right(char* n)             { return *(uint32_t*)(n+10); }

    /* Set rightmost child */
    void     int_set_right(char* n, uint32_t v){ *(uint32_t*)(n+10)=v;  }

    /*
    ----------------------------------------------------
    int_cell

    Returns pointer to internal node cell.

    Layout:
      [ child_page | key ]
    ----------------------------------------------------
    */
    char*    int_cell(char* n, uint32_t i)  {
        return n + INT_HDR_SIZE + i * INT_CELL_SIZE;
    }

    /* Get child pointer for index */
    uint32_t int_child(char* n, uint32_t i) {
        if (i == int_nkeys(n)) return int_right(n);
        return *(uint32_t*)int_cell(n,i);
    }

    /* Set child pointer */
    void     int_set_child(char* n, uint32_t i, uint32_t c){
        if (i == int_nkeys(n)) int_set_right(n,c);
        else *(uint32_t*)int_cell(n,i) = c;
    }

    /* Get key at index */
    uint32_t int_key(char* n, uint32_t i)   {
        return *(uint32_t*)(int_cell(n,i) + INT_CHILD_SIZE);
    }

    /* Set key */
    void     int_set_key(char* n, uint32_t i, uint32_t k){
        *(uint32_t*)(int_cell(n,i) + INT_CHILD_SIZE) = k;
    }

    /* ──────────────────────────────────────────────────
       INIT HELPERS
    ────────────────────────────────────────────────── */

    /*
    Initialize a leaf node.
    Clears memory and sets node type.
    */
    void init_leaf(char* n) {
        memset(n, 0, PAGE_SIZE);
        set_type(n, LEAF_NODE);
        leaf_set_ncells(n, 0);
        leaf_set_next(n, INVALID_PAGE);
    }

    /*
    Initialize an internal node.
    */
    void init_internal(char* n) {
        memset(n, 0, PAGE_SIZE);
        set_type(n, INTERNAL_NODE);
        int_set_nkeys(n, 0);
        int_set_right(n, INVALID_PAGE);
    }

    /* ──────────────────────────────────────────────────
       MAX KEY HELPERS
    ────────────────────────────────────────────────── */

    /*
    Returns maximum key contained in node.
    Used when updating parent nodes.
    */
    uint32_t max_key(char* n) {
        if (get_type(n) == LEAF_NODE)
            return leaf_key(n, leaf_ncells(n)-1);
        return int_key(n, int_nkeys(n)-1);
    }

    /* ──────────────────────────────────────────────────
       BINARY SEARCH — find position for key
    ────────────────────────────────────────────────── */

    /*
    find(page, key)

    Traverses B+ tree recursively using binary search.

    Returns:
      {page_number, cell_index}

    Used for:
      • Insert
      • Search
      • Delete
    */
    std::pair<uint32_t,uint32_t> find(uint32_t page, uint32_t key) {

        char* n = get_page(page);

        if (get_type(n) == LEAF_NODE) {

            /* Binary search inside leaf node */
            uint32_t lo=0, hi=leaf_ncells(n);

            while (lo < hi) {
                uint32_t mid=(lo+hi)/2;
                if (leaf_key(n,mid) >= key)
                    hi=mid;
                else
                    lo=mid+1;
            }

            return {page, lo};
        }

        else {

            /* Binary search inside internal node */
            uint32_t lo=0, hi=int_nkeys(n);

            while (lo < hi) {
                uint32_t mid=(lo+hi)/2;
                if (int_key(n,mid) >= key)
                    hi=mid;
                else
                    lo=mid+1;
            }

            /* Recurse into appropriate child */
            return find(int_child(n, lo), key);
        }
    }

    /* ──────────────────────────────────────────────────
       INTERNAL NODE — add child after split
    ────────────────────────────────────────────────── */

    /*
    Adds a child node to an internal node.

    Used when leaf split produces a new child.
    */
    void internal_add_child(uint32_t parent_pg,
                            uint32_t child_pg,
                            uint32_t child_max_key) {

        char* p = get_page(parent_pg);
        uint32_t nk = int_nkeys(p);

        /* Safety check */
        if (nk >= INT_MAX_KEYS) {
            std::cerr << "[BTree] Internal node full — tree too deep\n";
            return;
        }

        /* Determine correct insert position */
        uint32_t idx = nk;

        for (uint32_t i=0; i<nk; i++) {
            if (int_key(p,i) > child_max_key) {
                idx=i;
                break;
            }
        }

        /* Shift cells to make space */
        for (uint32_t i=nk; i>idx; i--) {
            memcpy(int_cell(p,i), int_cell(p,i-1), INT_CELL_SIZE);
        }

        /* Insert child pointer + key */
        int_set_child(p, idx, child_pg);
        int_set_key(p, idx, child_max_key);

        int_set_nkeys(p, nk+1);
    }

    /* ──────────────────────────────────────────────────
       LEAF SPLIT — called when leaf is full
    ────────────────────────────────────────────────── */

    /*
    Splits a full leaf node into two nodes.

    Steps:
      1. Create new leaf
      2. Redistribute entries
      3. Update linked list pointers
      4. Update parent node
      5. Create new root if needed
    */
    void leaf_split(uint32_t old_pg,
                    uint32_t cell_num,
                    uint32_t key,
                    Row* val) {

        char* old_n = get_page(old_pg);

        /* Allocate new leaf page */
        uint32_t new_pg = new_page();
        char* new_n = get_page(new_pg);

        init_leaf(new_n);

        set_parent(new_n, get_parent(old_n));

        /* Maintain leaf linked list */
        leaf_set_next(new_n, leaf_next(old_n));
        leaf_set_next(old_n, new_pg);

        /* Redistribute entries */
        uint32_t total = LEAF_MAX_CELLS + 1;

        for (int32_t i = (int32_t)total-1; i >= 0; i--) {

            bool goes_right = (uint32_t)i >= LEAF_L_SPLIT;

            char* dest = goes_right ? new_n : old_n;

            uint32_t didx =
                goes_right ? (uint32_t)i - LEAF_L_SPLIT
                           : (uint32_t)i;

            if ((uint32_t)i == cell_num) {

                leaf_set_key(dest, didx, key);
                memcpy(leaf_val(dest, didx), val, sizeof(Row));

            }

            else {

                uint32_t src =
                    (uint32_t)i > cell_num ? (uint32_t)i-1
                                           : (uint32_t)i;

                leaf_set_key(dest, didx, leaf_key(old_n, src));

                memcpy(leaf_val(dest, didx),
                       leaf_val(old_n, src),
                       sizeof(Row));
            }
        }

        leaf_set_ncells(old_n, LEAF_L_SPLIT);
        leaf_set_ncells(new_n, LEAF_R_SPLIT);

        /* Root split case */
        if (is_root(old_n)) {

            uint32_t root_pg = new_page();
            char* root = get_page(root_pg);

            init_internal(root);

            set_root(root, true);

            int_set_nkeys(root, 1);

            int_set_child(root, 0, old_pg);

            int_set_key(root, 0, max_key(old_n));

            int_set_right(root, new_pg);

            set_root(old_n, false);

            set_parent(old_n, root_pg);
            set_parent(new_n, root_pg);

            root_page = root_pg;
        }

        else {

            uint32_t par = get_parent(old_n);

            set_parent(new_n, par);

            internal_add_child(par,
                               new_pg,
                               max_key(new_n));
        }
    }

    /* ──────────────────────────────────────────────────
       LEFTMOST LEAF — for select all
    ────────────────────────────────────────────────── */

    /*
    Traverses tree to find the leftmost leaf.

    Used for:
      SELECT * queries
      full table scans
    */
    uint32_t leftmost_leaf(uint32_t page) {

        char* n = get_page(page);

        while (get_type(n) == INTERNAL_NODE) {

            page = int_child(n, 0);

            n = get_page(page);
        }

        return page;
    }

    /* Root page number of B+ tree */
    uint32_t root_page = 0;

public:

    /* ──────────────────────────────────────────────────
       CONSTRUCTOR / DESTRUCTOR
    ────────────────────────────────────────────────── */

    /*
    Constructor

    Opens database file and initializes B+ tree.
    If database file is empty, creates root leaf node.
    */
    BTree(std::string fname)
        : filename(fname),
          num_pages(0),
          root_page(0)
    {

        for (int i=0; i<TABLE_MAX_PAGES; i++)
            pages[i] = nullptr;

        /* Open file for read/write */
        file.open(filename,
                  std::ios::in |
                  std::ios::out |
                  std::ios::binary);

        /* Create file if missing */
        if (!file.is_open()) {

            file.open(filename,
                      std::ios::out |
                      std::ios::binary);

            file.close();

            file.open(filename,
                      std::ios::in |
                      std::ios::out |
                      std::ios::binary);
        }

        /* Determine number of existing pages */
        file.seekg(0, std::ios::end);

        uint32_t file_size =
            (uint32_t)file.tellg();

        num_pages = file_size / PAGE_SIZE;

        if (num_pages == 0) {

    // Fresh database case
    // --------------------------------------------------
    // When the database file is empty, we initialize the
    // B+ Tree with a single root node.
    //
    // Root is created as a LEAF node at page 0.
    // This node will store the first records inserted
    // into the database.
    // --------------------------------------------------

    char* root = get_page(0);
    init_leaf(root);
    set_root(root, true);

    // Root page is page 0 for a new database
    root_page = 0;
}
else {

    // Existing database case
    // --------------------------------------------------
    // If the database file already contains pages, the
    // root node may not necessarily be page 0 (because
    // previous insertions might have caused root splits).
    //
    // Therefore we scan all pages to locate the node
    // marked as the root.
    // --------------------------------------------------

    root_page = 0;

    for (uint32_t i = 0; i < num_pages; i++) {

        char* p = get_page(i);

        // Check root flag in node header
        if (is_root(p)) {
            root_page = i;
            break;
        }
    }
}
    }

    /*
    Destructor

    Flushes all cached pages to disk
    and frees memory.
    */
    ~BTree() {

        for (uint32_t i=0; i<num_pages; i++) {

            if (pages[i]) {

                flush(i);

                delete[] pages[i];

                pages[i]=nullptr;
            }
        }

        if (file.is_open())
            file.close();
    }

    /* ──────────────────────────────────────────────────
       INSERT — O(log n)
    ────────────────────────────────────────────────── */

    /*
    Inserts a new key/value pair.

    Steps:
      1. Locate leaf node
      2. Check duplicate
      3. Insert if space available
      4. Otherwise split leaf
    */
    bool insert(uint32_t key, Row* val) {

        auto [pg, cell] = find(root_page, key);

        char* n = get_page(pg);

        /* Prevent duplicate keys */
        if (cell < leaf_ncells(n) &&
            leaf_key(n, cell) == key)
            return false;

        if (leaf_ncells(n) < LEAF_MAX_CELLS) {

            /* Shift cells right */
            for (uint32_t i=leaf_ncells(n); i>cell; i--)
                memcpy(leaf_cell(n,i),
                       leaf_cell(n,i-1),
                       LEAF_CELL_SIZE);

            /* Insert key/value */
            leaf_set_key(n, cell, key);

            memcpy(leaf_val(n, cell),
                   val,
                   sizeof(Row));

            leaf_set_ncells(n,
                            leaf_ncells(n)+1);
        }

        else {

            /* Leaf full → split */
            leaf_split(pg, cell, key, val);
        }

        return true;
    }

    /* ──────────────────────────────────────────────────
       SEARCH — O(log n)
    ────────────────────────────────────────────────── */

    /*
    Finds row with given key.

    Returns:
      pointer to Row if found
      nullptr if not found
    */
    Row* search(uint32_t key) {

        auto [pg, cell] = find(root_page, key);

        char* n = get_page(pg);

        if (cell < leaf_ncells(n) &&
            leaf_key(n, cell) == key)

            return leaf_val(n, cell);

        return nullptr;
    }

    /* ──────────────────────────────────────────────────
       SELECT ALL — O(n)
    ────────────────────────────────────────────────── */

    /*
    Returns all rows in table.

    Implementation:
      Traverse linked list of leaf nodes.
    */
    std::vector<Row> select_all() {

        std::vector<Row> result;

        uint32_t pg = leftmost_leaf(root_page);

        while (pg != INVALID_PAGE) {

            char* n = get_page(pg);

            for (uint32_t i=0; i<leaf_ncells(n); i++)
                result.push_back(*leaf_val(n,i));

            pg = leaf_next(n);
        }

        return result;
    }

    /* ──────────────────────────────────────────────────
       DELETE — O(log n) search + O(n) shift
    ────────────────────────────────────────────────── */

    /*
    Removes row with given key.

    NOTE:
    Does not rebalance tree (simple delete).
    */
    bool remove(uint32_t key) {

        auto [pg, cell] = find(root_page, key);

        char* n = get_page(pg);

        if (cell >= leaf_ncells(n) ||
            leaf_key(n, cell) != key)

            return false;

        uint32_t nc = leaf_ncells(n);

        /* Shift cells left */
        for (uint32_t i=cell; i<nc-1; i++)
            memcpy(leaf_cell(n,i),
                   leaf_cell(n,i+1),
                   LEAF_CELL_SIZE);

        leaf_set_ncells(n, nc-1);

        return true;
    }
};

#endif