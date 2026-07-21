#ifndef PAGER_H
#define PAGER_H

#include <iostream>
#include <fstream>
#include <filesystem>
#include "common.h"

/*
============================================================
Pager Class
------------------------------------------------------------
Purpose:
- Database file ko manage karta hai
- Rows ko disk par read/write karta hai
- File resize (truncate) kar sakta hai

Role in 4mulaQuery Engine:
- Low-level storage layer
- B-Tree / Engine ko persistent storage provide karta hai
============================================================
*/

class Pager {
private:
    std::fstream file;     // Database file stream
    std::string filename;  // Database file name

public:

    /*
    ------------------------------------------------------------
    Constructor
    ------------------------------------------------------------
    - Database file open karta hai
    - Agar file exist nahi karti to create karta hai
    */
    Pager(std::string fname) : filename(fname) {

        // Try opening existing database file
        file.open(filename, std::ios::in | std::ios::out | std::ios::binary);

        // Agar file exist nahi karti to create karo
        if (!file.is_open()) {
            file.open(filename, std::ios::out | std::ios::binary);
            file.close();

            // Reopen in read/write mode
            file.open(filename, std::ios::in | std::ios::out | std::ios::binary);
        }

        // Critical error agar file ab bhi open nahi hui
        if (!file.is_open()) {
            std::cerr << "CRITICAL ERROR: Could not open database file: "
                      << fname << std::endl;
        }
    }

    /*
    ------------------------------------------------------------
    Destructor
    ------------------------------------------------------------
    - File stream safely close karta hai
    */
    ~Pager() {
        if (file.is_open())
            file.close();
    }


    /*
    ------------------------------------------------------------
    write_row()
    ------------------------------------------------------------
    Purpose:
    - Row ko database file me specific position par write karta hai

    Parameters:
    - row: pointer to Row object
    - row_num: row index in database file
    */
    void write_row(Row* row, uint32_t row_num) {

        // Reset EOF / fail state
        file.clear();

        // Move write pointer
        file.seekp(row_num * sizeof(Row));

        // Write row data
        file.write(reinterpret_cast<char*>(row), sizeof(Row));

        // Ensure data is flushed to disk
        file.flush();
    }


    /*
    ------------------------------------------------------------
    read_row()
    ------------------------------------------------------------
    Purpose:
    - Database file se specific row read karta hai

    Return:
    - true  → row successfully read
    - false → row exist nahi karti
    */
    bool read_row(Row* row, uint32_t row_num) {

        // Reset EOF / fail state
        file.clear();

        // Move read pointer
        file.seekg(row_num * sizeof(Row));

        // Read row data
        file.read(reinterpret_cast<char*>(row), sizeof(Row));

        // Check agar full row read hui hai
        return file.gcount() == sizeof(Row);
    }


    /*
    ------------------------------------------------------------
    truncate()
    ------------------------------------------------------------
    Purpose:
    - Database file ka size reduce karta hai
    - Mostly delete operations ke baad use hota hai
    */
    void truncate(uint32_t new_row_count) {

        // File close karo resize se pehle
        file.close();

        // Resize database file
        std::filesystem::resize_file(
            filename,
            (uintmax_t)(new_row_count * sizeof(Row))
        );

        // File reopen in read/write mode
        file.open(filename, std::ios::in | std::ios::out | std::ios::binary);
    }
};

#endif