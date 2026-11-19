package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.model.purchase.PurchaseTransaction;
import com.realestate.model.user.User;
import com.realestate.services.PurchaseService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/purchases/*")
public class PurchaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private PurchaseService purchaseService;

    @Override
    public void init() throws ServletException {
        this.purchaseService = new PurchaseService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Please login to view purchase history", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        try {
            List<PurchaseTransaction> list;
            if (user.isAdministrator()) {
                list = purchaseService.getAllTransactions();
            } else {
                list = purchaseService.getPurchasesForUser(user.getUserId());
            }
            out.print(JsonUtil.success("Purchase transactions loaded", serializeTransactions(list)));
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
            out.print(JsonUtil.error("Please login to complete purchase", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");

        try {
            PurchaseTransaction txn = new PurchaseTransaction();
            txn.setPropertyId(Integer.parseInt(req.getParameter("propertyId")));
            txn.setBuyerId(user.getUserId());
            txn.setSellerId(Integer.parseInt(req.getParameter("sellerId")));
            txn.setPurchasePrice(Double.parseDouble(req.getParameter("purchasePrice")));
            txn.setRemarks(req.getParameter("remarks") != null ? req.getParameter("remarks") : "Simulated Project Purchase Transaction");

            PurchaseTransaction completed = purchaseService.executeSimulatedPurchase(txn);
            out.print(JsonUtil.success("Property purchased successfully! Listing marked as SOLD.", 
                      "{\"txnId\":" + completed.getTransactionId() + ",\"txnRef\":" + JsonUtil.escape(completed.getTransactionReference()) + "}"));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }

    private String serializeTransactions(List<PurchaseTransaction> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            PurchaseTransaction t = list.get(i);
            sb.append("{")
              .append("\"transactionId\":").append(t.getTransactionId())
              .append(",\"propertyId\":").append(t.getPropertyId())
              .append(",\"propertyTitle\":").append(JsonUtil.escape(t.getPropertyTitle()))
              .append(",\"buyerName\":").append(JsonUtil.escape(t.getBuyerName()))
              .append(",\"sellerName\":").append(JsonUtil.escape(t.getSellerName()))
              .append(",\"purchasePrice\":").append(t.getPurchasePrice())
              .append(",\"date\":").append(JsonUtil.escape(t.getTransactionDate() != null ? t.getTransactionDate().toString() : ""))
              .append(",\"status\":").append(JsonUtil.escape(t.getPaymentStatus()))
              .append(",\"txnRef\":").append(JsonUtil.escape(t.getTransactionReference()))
              .append(",\"remarks\":").append(JsonUtil.escape(t.getRemarks()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
