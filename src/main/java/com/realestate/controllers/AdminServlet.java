package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.dao.AnalyticsDAO;
import com.realestate.dao.UserDAO;
import com.realestate.model.property.Property;
import com.realestate.model.user.User;
import com.realestate.model.verification.OwnerVerification;
import com.realestate.services.AppointmentService;
import com.realestate.services.PropertyService;
import com.realestate.services.PurchaseService;
import com.realestate.services.UserService;
import com.realestate.services.VerificationService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/admin/*")
public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private AnalyticsDAO analyticsDAO;
    private PropertyService propertyService;
    private VerificationService verificationService;
    private UserService userService;
    private AppointmentService appointmentService;
    private PurchaseService purchaseService;

    @Override
    public void init() throws ServletException {
        this.analyticsDAO = new AnalyticsDAO();
        this.propertyService = new PropertyService();
        this.verificationService = new VerificationService();
        this.userService = new UserService();
        this.appointmentService = new AppointmentService();
        this.purchaseService = new PurchaseService();
    }

    private boolean checkAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().print(JsonUtil.error("Admin authentication required", 401));
            return false;
        }
        User user = (User) session.getAttribute("currentUser");
        if (!user.isAdministrator()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().print(JsonUtil.error("Access forbidden: Administrator privileges required.", 403));
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (!checkAdmin(req, resp)) return;

        PrintWriter out = resp.getWriter();
        String pathInfo = req.getPathInfo();

        try {
            if ("/stats".equals(pathInfo)) {
                Map<String, Integer> stats = analyticsDAO.getAdminDashboardStats();
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, Integer> e : stats.entrySet()) {
                    if (i++ > 0) sb.append(",");
                    sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
                }
                sb.append("}");
                out.print(JsonUtil.success("Stats loaded", sb.toString()));

            } else if ("/properties/pending".equals(pathInfo)) {
                List<Property> pending = propertyService.getPendingProperties();
                out.print(JsonUtil.success("Pending properties loaded", PropertyServlet.serializePropertyList(pending)));

            } else if ("/verifications".equals(pathInfo)) {
                List<OwnerVerification> list = verificationService.getPendingVerifications();
                StringBuilder sb = new StringBuilder("[");
                for (int j = 0; j < list.size(); j++) {
                    if (j > 0) sb.append(",");
                    OwnerVerification v = list.get(j);
                    sb.append("{")
                      .append("\"verificationId\":").append(v.getVerificationId())
                      .append(",\"userId\":").append(v.getUserId())
                      .append(",\"userName\":").append(JsonUtil.escape(v.getUserName()))
                      .append(",\"userEmail\":").append(JsonUtil.escape(v.getUserEmail()))
                      .append(",\"proofType\":").append(JsonUtil.escape(v.getIdentityProofType()))
                      .append(",\"proofNumber\":").append(JsonUtil.escape(v.getIdentityProofNumber()))
                      .append(",\"address\":").append(JsonUtil.escape(v.getPropertyAddress()))
                      .append(",\"documentPath\":").append(JsonUtil.escape(v.getDocumentPath()))
                      .append(",\"status\":").append(JsonUtil.escape(v.getVerificationStatus()))
                      .append("}");
                }
                sb.append("]");
                out.print(JsonUtil.success("Pending owner verifications loaded", sb.toString()));

            } else if ("/users".equals(pathInfo)) {
                List<User> users = userService.getAllUsers();
                StringBuilder sb = new StringBuilder("[");
                for (int k = 0; k < users.size(); k++) {
                    if (k > 0) sb.append(",");
                    User u = users.get(k);
                    sb.append("{")
                      .append("\"userId\":").append(u.getUserId())
                      .append(",\"fullName\":").append(JsonUtil.escape(u.getFullName()))
                      .append(",\"email\":").append(JsonUtil.escape(u.getEmail()))
                      .append(",\"mobile\":").append(JsonUtil.escape(u.getMobileNumber()))
                      .append(",\"role\":").append(JsonUtil.escape(u.getUserRole()))
                      .append(",\"isVerified\":").append(u.isVerified())
                      .append(",\"isActive\":").append(u.isActive())
                      .append("}");
                }
                sb.append("]");
                out.print(JsonUtil.success("Users loaded", sb.toString()));

            } else {
                out.print(JsonUtil.error("Unknown admin endpoint", 404));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(JsonUtil.error(e.getMessage(), 500));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (!checkAdmin(req, resp)) return;

        PrintWriter out = resp.getWriter();
        String pathInfo = req.getPathInfo();

        try {
            if ("/properties/approve".equals(pathInfo)) {
                int propertyId = Integer.parseInt(req.getParameter("propertyId"));
                boolean ok = propertyService.approveProperty(propertyId);
                out.print(ok ? JsonUtil.success("Property approved and is now live publicly.", null) : JsonUtil.error("Failed to approve property", 400));

            } else if ("/properties/reject".equals(pathInfo)) {
                int propertyId = Integer.parseInt(req.getParameter("propertyId"));
                String reason = req.getParameter("reason") != null ? req.getParameter("reason") : "Does not meet platform guidelines";
                boolean ok = propertyService.rejectProperty(propertyId, reason);
                out.print(ok ? JsonUtil.success("Property listing rejected.", null) : JsonUtil.error("Failed to reject property", 400));

            } else if ("/verifications/action".equals(pathInfo)) {
                int verificationId = Integer.parseInt(req.getParameter("verificationId"));
                String status = req.getParameter("status"); // APPROVED, REJECTED
                String notes = req.getParameter("notes") != null ? req.getParameter("notes") : "Documents reviewed by Admin";
                boolean ok = verificationService.updateStatus(verificationId, status, notes);
                out.print(ok ? JsonUtil.success("Owner verification updated to " + status, null) : JsonUtil.error("Failed to update verification", 400));

            } else {
                out.print(JsonUtil.error("Unknown admin post endpoint", 404));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }
}
