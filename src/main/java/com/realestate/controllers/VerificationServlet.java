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
import com.realestate.model.verification.OwnerVerification;
import com.realestate.services.VerificationService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/verifications/*")
public class VerificationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private VerificationService verificationService;

    @Override
    public void init() throws ServletException {
        this.verificationService = new VerificationService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Please login to view verification status", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        try {
            OwnerVerification v = verificationService.getByUserId(user.getUserId());
            if (v != null) {
                String data = "{\"verificationId\":" + v.getVerificationId() + 
                              ",\"status\":" + JsonUtil.escape(v.getVerificationStatus()) + 
                              ",\"proofType\":" + JsonUtil.escape(v.getIdentityProofType()) + 
                              ",\"proofNumber\":" + JsonUtil.escape(v.getIdentityProofNumber()) + 
                              ",\"propertyAddress\":" + JsonUtil.escape(v.getPropertyAddress()) + 
                              ",\"adminNotes\":" + JsonUtil.escape(v.getAdminNotes()) + "}";
                out.print(JsonUtil.success("Verification record found", data));
            } else {
                out.print(JsonUtil.success("No verification record found", "{\"status\":\"NOT_SUBMITTED\"}"));
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
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Please login to submit verification", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");

        try {
            OwnerVerification v = new OwnerVerification();
            v.setUserId(user.getUserId());
            v.setIdentityProofType(req.getParameter("proofType"));
            v.setIdentityProofNumber(req.getParameter("proofNumber"));
            v.setDocumentPath(req.getParameter("documentPath") != null ? req.getParameter("documentPath") : "/uploads/docs/doc_" + user.getUserId() + ".pdf");
            v.setPropertyAddress(req.getParameter("propertyAddress"));

            OwnerVerification submitted = verificationService.submitVerification(v);
            out.print(JsonUtil.success("Verification documents submitted for admin approval.", "{\"verificationId\":" + submitted.getVerificationId() + "}"));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }
}
