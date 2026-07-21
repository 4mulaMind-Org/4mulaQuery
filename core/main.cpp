/*
============================================================
4mulaQuery — main.cpp (B+ Tree version)

Main database execution layer.
Acts as a command interpreter between the Java server
(API layer) and the B+ Tree storage engine.

Interface unchanged — same commands as before:
  insert,id,name,email
  search,id
  delete,id
  all

Upgrade:
  Linear O(n) → B+ Tree O(log n)
  13 records/leaf, 509 keys/internal node
  Leaf linked list for fast full scan
============================================================
*/

#include <iostream>
#include <string>
#include <sstream>
#include <vector>
#include "btree.h"
#include "common.h"

/*
------------------------------------------------------------
DatabaseEngine

Acts as the command processor for the database.

Responsibilities:
• Receive commands from stdin (Java bridge)
• Parse commands
• Call BTree operations
• Format output for API response
------------------------------------------------------------
*/
class DatabaseEngine {
private:

    /* B+ Tree storage engine instance */
    BTree tree;

public:

    /*
    --------------------------------------------------------
    Constructor

    Initializes database using disk file:
        data/4mulaQuery.db
    --------------------------------------------------------
    */
    DatabaseEngine() : tree("data/4mulaQuery.db") {}

    /* ── INSERT ──────────────────────────────────────── */

    /*
    Handles insert command.

    Input format:
        insert,id,name,email

    Example:
        insert,1,Ali,ali@mail.com

    Steps:
      1. Parse values from stream
      2. Convert ID to integer
      3. Populate Row structure
      4. Call BTree insert
      5. Print execution result
    */
    void handleInsert(std::stringstream& ss) {

        std::string id_str, name, email;

        /* Parse CSV parameters */
        if (std::getline(ss, id_str, ',') &&
            std::getline(ss, name, ',')   &&
            std::getline(ss, email, ',')) {

            try {

                Row row;

                /* Convert ID string to integer */
                row.id = std::stoi(id_str);

                /* Copy username safely */
                snprintf(row.username,
                         USERNAME_SIZE,
                         "%s",
                         name.c_str());

                /* Copy email safely */
                snprintf(row.email,
                         EMAIL_SIZE,
                         "%s",
                         email.c_str());

                /* Insert into B+ tree */
                if (tree.insert(row.id, &row))

                    std::cout
                        << "Executed.\n"
                        << std::flush;

                else

                    std::cout
                        << "Error: ID already exists.\n"
                        << std::flush;
            }

            catch (...) {

                /* Invalid numeric conversion */
                std::cout
                    << "Error: Invalid ID format.\n"
                    << std::flush;
            }
        }
    }

    /* ── SELECT ALL ──────────────────────────────────── */

    /*
    Handles full table scan.

    Command:
        select
        OR
        all

    Implementation:
        Uses BTree::select_all()

    Output format:
        id,username,email
    */
    void handleSelect() {

        auto rows = tree.select_all();

        if (rows.empty()) {

            std::cout
                << "Database is empty.\n"
                << std::flush;

            return;
        }

        /* Print all rows */
        for (auto& r : rows)

            std::cout
                << r.id << ","
                << r.username << ","
                << r.email
                << "\n";

        std::cout << std::flush;
    }

    /* ── SEARCH ──────────────────────────────────────── */

    /*
    Handles search command.

    Input format:
        search,id

    Example:
        search,5

    Uses B+ tree O(log n) lookup.
    */
    void handleSearch(std::stringstream& ss) {

        std::string s_id;

        if (std::getline(ss, s_id, ',')) {

            try {

                uint32_t id = std::stoi(s_id);

                /* Lookup record in B+ tree */
                Row* row = tree.search(id);

                if (row)

                    std::cout
                        << row->id << ","
                        << row->username << ","
                        << row->email
                        << "\n"
                        << std::flush;

                else

                    std::cout
                        << "ID " << id
                        << " Not Found.\n"
                        << std::flush;
            }

            catch (...) {

                std::cout
                    << "Error: Search ID format.\n"
                    << std::flush;
            }
        }
    }

    /* ── DELETE ──────────────────────────────────────── */

    /*
    Handles delete command.

    Input format:
        delete,id

    Example:
        delete,10

    Operation:
        1. Search key
        2. Remove record from leaf node
        3. Shift remaining cells

    Note:
        Tree rebalancing is not implemented
        (simple leaf deletion).
    */
    void handleDelete(std::stringstream& ss) {

        std::string s_id;

        if (std::getline(ss, s_id, ',')) {

            try {

                uint32_t id = std::stoi(s_id);

                if (tree.remove(id))

                    std::cout
                        << "Deleted ID "
                        << id
                        << ".\n"
                        << std::flush;

                else

                    std::cout
                        << "ID "
                        << id
                        << " Not Found.\n"
                        << std::flush;
            }

            catch (...) {

                std::cout
                    << "Error: Delete ID format.\n"
                    << std::flush;
            }
        }
    }

    /* ── COMMAND DISPATCHER ──────────────────────────── */

    /*
    run()

    Main execution loop.

    Reads commands from stdin and dispatches them
    to the appropriate handler.

    Commands supported:

      insert,id,name,email
      search,id
      delete,id
      select
      all
      exit
    */
    void run() {

        std::string line;

        while (std::getline(std::cin, line)) {

            /* Trim trailing whitespace */
            line.erase(
                line.find_last_not_of(" \n\r\t") + 1
            );

            if (line.empty() || line == "exit")
                break;

            std::stringstream ss(line);

            std::string command;

            /*
            Detect whether command uses CSV format
            */
            if (line.find(',') != std::string::npos)

                std::getline(ss, command, ',');

            else

                ss >> command;

            /* Dispatch command */

            if (command == "insert")

                handleInsert(ss);

            else if (command == "select"
                  || command == "all")

                handleSelect();

            else if (command == "search")

                handleSearch(ss);

            else if (command == "delete")

                handleDelete(ss);

            /*
            IMPORTANT

            Only one command per process.

            Required because the Java API
            spawns a new process for each query.
            */
            break;
        }
    }
};

/*
------------------------------------------------------------
Program Entry Point
------------------------------------------------------------
*/
int main() {

    DatabaseEngine engine;

    /* Start command processor */
    engine.run();

    return 0;
}