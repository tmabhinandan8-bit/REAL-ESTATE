package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.model.rental.RentalAgreement;
import com.realestate.model.rental.RentPayment;
import com.realestate.model.user.User;
import com.realestate.services.RentalService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/rentals/*")
public class RentalServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private RentalService rentalService;

    @Override
    public void init() throws ServletException {
        this.rentalService = new RentalService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Please login to view rental details", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        try {
            if ("/payments".equals(pathInfo)) {
                int agreementId = Integer.parseInt(req.getParameter("agreementId"));
                List<RentPayment> list = rentalService.getRentPayments(agreementId);
                out.print(JsonUtil.success("Rent payment history loaded", serializePayments(list)));
            } else {
                List<RentalAgreement> agreements = rentalService.getAgreementsForUser(user.getUserId());
                out.print(JsonUtil.success("Rental agreements loaded", serializeAgreements(agreements)));
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
            out.print(JsonUtil.error("Please login to perform rental operations", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        try {
            if ("/pay".equals(pathInfo)) {
                // Record simulated rent payment
                RentPayment payment = new RentPayment();
                payment.setAgreementId(Integer.parseInt(req.getParameter("agreementId")));
                payment.setPaymentMonth(req.getParameter("paymentMonth"));
                payment.setAmountPaid(Double.parseDouble(req.getParameter("amount")));
                payment.setPaymentDate(new Date(System.currentTimeMillis()));
                payment.setPaymentMethod(req.getParameter("paymentMethod") != null ? req.getParameter("paymentMethod") : "SIMULATED_UPI");

                RentPayment recorded = rentalService.payRent(payment);
                out.print(JsonUtil.success("Rent payment processed successfully!", "{\"txnRef\":" + JsonUtil.escape(recorded.getTransactionRef()) + "}"));
            } else {
                // Create Rental Agreement
                RentalAgreement ra = new RentalAgreement();
                ra.setPropertyId(Integer.parseInt(req.getParameter("propertyId")));
                ra.setOwnerId(Integer.parseInt(req.getParameter("ownerId")));
                ra.setTenantId(user.getUserId());
                ra.setMonthlyRent(Double.parseDouble(req.getParameter("monthlyRent")));
                ra.setSecurityDeposit(Double.parseDouble(req.getParameter("securityDeposit")));
                ra.setAgreementStartDate(Date.valueOf(req.getParameter("startDate")));
                ra.setAgreementEndDate(Date.valueOf(req.getParameter("endDate")));
                ra.setTermsAndConditions(req.getParameter("terms"));

                RentalAgreement created = rentalService.createAgreement(ra);
                out.print(JsonUtil.success("Rental agreement created successfully!", "{\"agreementId\":" + created.getAgreementId() + "}"));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }

    private String serializeAgreements(List<RentalAgreement> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            RentalAgreement ra = list.get(i);
            sb.append("{")
              .append("\"agreementId\":").append(ra.getAgreementId())
              .append(",\"propertyId\":").append(ra.getPropertyId())
              .append(",\"propertyTitle\":").append(JsonUtil.escape(ra.getPropertyTitle()))
              .append(",\"ownerName\":").append(JsonUtil.escape(ra.getOwnerName()))
              .append(",\"tenantName\":").append(JsonUtil.escape(ra.getTenantName()))
              .append(",\"monthlyRent\":").append(ra.getMonthlyRent())
              .append(",\"securityDeposit\":").append(ra.getSecurityDeposit())
              .append(",\"startDate\":").append(JsonUtil.escape(ra.getAgreementStartDate().toString()))
              .append(",\"endDate\":").append(JsonUtil.escape(ra.getAgreementEndDate().toString()))
              .append(",\"status\":").append(JsonUtil.escape(ra.getAgreementStatus()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializePayments(List<RentPayment> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            RentPayment rp = list.get(i);
            sb.append("{")
              .append("\"paymentId\":").append(rp.getRentPaymentId())
              .append(",\"month\":").append(JsonUtil.escape(rp.getPaymentMonth()))
              .append(",\"amount\":").append(rp.getAmountPaid())
              .append(",\"date\":").append(JsonUtil.escape(rp.getPaymentDate().toString()))
              .append(",\"method\":").append(JsonUtil.escape(rp.getPaymentMethod()))
              .append(",\"txnRef\":").append(JsonUtil.escape(rp.getTransactionRef()))
              .append(",\"status\":").append(JsonUtil.escape(rp.getPaymentStatus()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
