/*
============================================================
4mulaQuery - Web Routing Controller
============================================================

File Location: 
/src/main/java/com/formulaquery/api/WebController.java

Purpose:
This controller manages the primary web routing for the 
application. It acts as the entry point for the browser, 
forwarding requests to the static frontend assets.

Features:
• Root URL Mapping ("/")
• Static Resource Forwarding
• Seamless Frontend-Backend Integration

============================================================
*/

package com.formulaquery.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ====================================================
 * CLASS: WebController
 * ====================================================
 * Manages the navigation logic for the 4mulaQuery web interface.
 * Annotated with @Controller to serve HTML/static content.
 * ====================================================
 */
@Controller
public class WebController {

    /**
     * ----------------------------------------------------
     * METHOD: index()
     * ----------------------------------------------------
     * Maps the root context path ("/") to the static index.html.
     * * Logic:
     * Uses "forward:" to bypass template engines and directly 
     * serve the file located in: src/main/resources/static/
     * ----------------------------------------------------
     * @return String path to the static index.html
     */
    @GetMapping("/")
    public String index() {

        /* Forwarding to index.html ensures the browser stays at "/" 
           while rendering the static frontend of 4mulaQuery.
        */
        return "forward:/index.html";

    }
}