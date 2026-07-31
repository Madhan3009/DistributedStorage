package com.dSystems.demo.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * THE DASHBOARD REDIRECTION DESK.
 * 
 * Think of this class as a simple welcoming sign at the main entrance (`/`).
 * If a user opens the main page of our storage website (like `http://localhost:8080/`),
 * this class catches them and instantly redirects their browser to the actual dashboard webpage.
 * 
 * Annotations:
 * - @Controller: Tells Spring Boot that this class is used to serve web page navigation or redirections
 *   (rather than returning raw JSON/text data).
 */
@Controller
public class DashboardController {

    /**
     * Intercepts visitors accessing the root address of the website (`/`).
     * 
     * @return A instruction telling the browser to redirect to the dashboard's index.html page.
     */
    @GetMapping({"/", "/dashboard"})
    public String index() {
        return "redirect:/dashboard/index.html";
    }
}
