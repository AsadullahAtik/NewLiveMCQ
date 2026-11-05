package com.livemcq.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEST-ONLY endpoints under the protected prefixes, so the role rules can be tested before the real
 * admin/student APIs exist. It lives in src/test, so it is never part of the real application.
 * (@SpringBootTest finds it by component scanning; slice tests such as @DataJpaTest skip controllers.)
 */
@RestController
public class RoleProbeController {

    @GetMapping("/api/admin/_probe")
    String admin() { return "admin-area"; }

    @GetMapping("/api/student/_probe")
    String student() { return "student-area"; }
}
