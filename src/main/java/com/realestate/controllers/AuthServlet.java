package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.model.user.User;
import com.realestate.services.UserService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private UserService userService;

    @Override
    public void init() throws ServletException {
        this.userService = new UserService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        String pathInfo = req.getPathInfo();
        if ("/me".equals(pathInfo)) {
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("currentUser") != null) {
                User user = (User) session.getAttribute("currentUser");
                String userData = "{\"userId\":" + user.getUserId() + 
                                  ",\"fullName\":" + JsonUtil.escape(user.getFullName()) + 
                                  ",\"email\":" + JsonUtil.escape(user.getEmail()) + 
                                  ",\"mobile\":" + JsonUtil.escape(user.getMobileNumber()) + 
                                  ",\"role\":" + JsonUtil.escape(user.getUserRole()) + 
                                  ",\"isVerified\":" + user.isVerified() + "}";
                out.print(JsonUtil.success("Authenticated", userData));
            } else {
                out.print(JsonUtil.error("Not authenticated", 401));
            }
        } else if ("/logout".equals(pathInfo)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            out.print(JsonUtil.success("Logged out successfully", null));
        } else {
            out.print(JsonUtil.error("Invalid auth endpoint", 404));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        String pathInfo = req.getPathInfo();
        try {
            if ("/register".equals(pathInfo)) {
                String fullName = req.getParameter("fullName");
                String email = req.getParameter("email");
                String mobile = req.getParameter("mobile");
                String password = req.getParameter("password");
                String role = req.getParameter("role");

                User registered = userService.registerUser(fullName, email, mobile, password, role);

                // Auto login session
                HttpSession session = req.getSession(true);
                session.setAttribute("currentUser", registered);
                session.setAttribute("userId", registered.getUserId());
                session.setAttribute("userRole", registered.getUserRole());

                String userData = "{\"userId\":" + registered.getUserId() + 
                                  ",\"fullName\":" + JsonUtil.escape(registered.getFullName()) + 
                                  ",\"email\":" + JsonUtil.escape(registered.getEmail()) + 
                                  ",\"role\":" + JsonUtil.escape(registered.getUserRole()) + "}";
                out.print(JsonUtil.success("Registration successful", userData));

            } else if ("/login".equals(pathInfo)) {
                String email = req.getParameter("email");
                String password = req.getParameter("password");

                User user = userService.login(email, password);

                HttpSession session = req.getSession(true);
                session.setAttribute("currentUser", user);
                session.setAttribute("userId", user.getUserId());
                session.setAttribute("userRole", user.getUserRole());

                String userData = "{\"userId\":" + user.getUserId() + 
                                  ",\"fullName\":" + JsonUtil.escape(user.getFullName()) + 
                                  ",\"email\":" + JsonUtil.escape(user.getEmail()) + 
                                  ",\"role\":" + JsonUtil.escape(user.getUserRole()) + 
                                  ",\"isVerified\":" + user.isVerified() + "}";
                out.print(JsonUtil.success("Login successful", userData));
            } else if ("/logout".equals(pathInfo)) {
                HttpSession session = req.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                out.print(JsonUtil.success("Logged out successfully", null));
            } else if ("/validate-email".equals(pathInfo)) {
                String email = req.getParameter("email");
                com.realestate.services.EmailService.EmailValidationResult v = userService.validateEmail(email);
                boolean exists = v.valid && "true".equalsIgnoreCase(req.getParameter("checkExists")) && userService.emailExists(v.normalizedEmail);
                java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("valid", v.valid);
                data.put("normalizedEmail", v.normalizedEmail);
                data.put("message", v.message);
                data.put("suggestion", v.suggestion);
                data.put("mxVerified", v.mxVerified);
                data.put("registered", exists);
                out.print(JsonUtil.success(v.message, JsonUtil.toJson(data)));
            } else if ("/forgot-password".equals(pathInfo)) {
                java.util.Map<String, Object> otpResult = userService.requestPasswordResetOtp(req.getParameter("email"));
                out.print(JsonUtil.success(String.valueOf(otpResult.get("infoMessage")), JsonUtil.toJson(otpResult)));
            } else if ("/verify-otp".equals(pathInfo)) {
                String resetToken = userService.verifyPasswordResetOtp(req.getParameter("email"), req.getParameter("otp"));
                out.print(JsonUtil.success("OTP verified successfully", "{\"resetToken\":" + JsonUtil.escape(resetToken) + "}"));
            } else if ("/reset-password".equals(pathInfo)) {
                userService.resetPassword(req.getParameter("email"), req.getParameter("resetToken"), req.getParameter("otp"), req.getParameter("newPassword"));
                out.print(JsonUtil.success("Password updated successfully! You can now sign in with your new password.", null));
            } else {
                out.print(JsonUtil.error("Endpoint not found", 404));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }
}
