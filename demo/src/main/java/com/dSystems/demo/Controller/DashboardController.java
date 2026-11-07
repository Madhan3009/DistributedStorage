package com.dSystems.demo.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects the root and {@code /dashboard} paths to the dashboard's static page.
 */
@Controller
public class DashboardController {

    /**
     * @return a redirect to the dashboard's {@code index.html}
     */
    @GetMapping({"/", "/dashboard"})
    public String index() {
        return "redirect:/dashboard/index.html";
    }
}
