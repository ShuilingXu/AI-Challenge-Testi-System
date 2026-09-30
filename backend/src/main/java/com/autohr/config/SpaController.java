package com.autohr.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browser history routes for the frontend embedded in release JARs. */
@Controller
public class SpaController {
    public static final String[] ROUTES = {
            "/", "/login", "/student", "/student/register", "/exam/take/{processId}",
            "/change-password", "/changepasswd", "/admin", "/admin/exams", "/admin/classes",
            "/admin/students", "/admin/analytics", "/admin/score-review",
            "/admin/score-review/{processId}", "/admin/knowledge", "/admin/settings",
            "/admin/site-settings", "/admin/staff"
    };

    @GetMapping({"/", "/login", "/student", "/student/register", "/exam/take/{processId}",
            "/change-password", "/changepasswd", "/admin", "/admin/exams", "/admin/classes",
            "/admin/students", "/admin/analytics", "/admin/score-review",
            "/admin/score-review/{processId}", "/admin/knowledge", "/admin/settings",
            "/admin/site-settings", "/admin/staff"})
    public String frontend() {
        return "forward:/index.html";
    }
}
