package com.open.spring.mvc.bathroom;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller  // This is needed to serve Thymeleaf templates
public class ApprovalRequestsController {

    @Autowired
    private ApprovalRequestJPARepository approvalRepository;

    // Teacher whose approval queue this page shows (ADMIN_EMAIL)
    @Value("${kit.admin-email}")
    private String teacherEmail;

    @GetMapping("/admin/approval-requests")
    public String showApprovalRequests(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        // ✅ Fetch approval requests for the configured teacher email
        List<ApprovalRequest> pendingRequests = approvalRepository.findByTeacherEmail(teacherEmail);
        System.out.println("Pending Requests: " + pendingRequests.size()); // Debugging

        // ✅ Pass data to Thymeleaf template
        model.addAttribute("pendingRequests", pendingRequests);

        return "approval-requests"; 
    }
}
