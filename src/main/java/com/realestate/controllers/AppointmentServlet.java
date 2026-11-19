package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.model.appointment.PropertyVisit;
import com.realestate.model.user.User;
import com.realestate.services.AppointmentService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/appointments/*")
public class AppointmentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private AppointmentService appointmentService;

    @Override
    public void init() throws ServletException {
        this.appointmentService = new AppointmentService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Please log in to view appointments", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        try {
            List<PropertyVisit> visits;
            if (user.isAdministrator()) {
                visits = appointmentService.getAllVisits();
            } else if ("PROPERTY_OWNER".equalsIgnoreCase(user.getUserRole()) || "SELLER".equalsIgnoreCase(user.getUserRole()) || "AGENT".equalsIgnoreCase(user.getUserRole())) {
                visits = appointmentService.getVisitsByOwner(user.getUserId());
            } else {
                visits = appointmentService.getVisitsByUser(user.getUserId());
            }
            out.print(JsonUtil.success("Appointments loaded", serializeVisits(visits)));
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
            out.print(JsonUtil.error("Please log in to schedule or manage visits", 401));
            return;
        }

        User user = (User) session.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        try {
            if ("/status".equals(pathInfo)) {
                int visitId = Integer.parseInt(req.getParameter("visitId"));
                String status = req.getParameter("status"); // APPROVED, REJECTED, COMPLETED
                boolean ok = appointmentService.updateVisitStatus(visitId, status);
                if (ok) {
                    out.print(JsonUtil.success("Appointment status updated to " + status, null));
                } else {
                    out.print(JsonUtil.error("Failed to update status", 400));
                }
            } else {
                // Schedule Visit
                PropertyVisit pv = new PropertyVisit();
                pv.setPropertyId(Integer.parseInt(req.getParameter("propertyId")));
                pv.setUserId(user.getUserId());
                pv.setUserEmail(user.getEmail());
                pv.setVisitDate(Date.valueOf(req.getParameter("visitDate")));
                
                String timeStr = req.getParameter("visitTime");
                if (timeStr != null && timeStr.length() == 5) timeStr += ":00"; // format HH:mm:ss
                pv.setVisitTime(Time.valueOf(timeStr != null ? timeStr : "10:00:00"));
                
                pv.setContactNumber(req.getParameter("contactNumber") != null ? req.getParameter("contactNumber") : user.getMobileNumber());
                pv.setNotes(req.getParameter("notes"));

                PropertyVisit scheduled = appointmentService.scheduleVisit(pv);
                out.print(JsonUtil.success("Visit appointment scheduled successfully!", "{\"visitId\":" + scheduled.getVisitId() + "}"));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }

    private String serializeVisits(List<PropertyVisit> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            PropertyVisit v = list.get(i);
            sb.append("{")
              .append("\"visitId\":").append(v.getVisitId())
              .append(",\"propertyId\":").append(v.getPropertyId())
              .append(",\"propertyTitle\":").append(JsonUtil.escape(v.getPropertyTitle()))
              .append(",\"propertyCity\":").append(JsonUtil.escape(v.getPropertyCity()))
              .append(",\"userId\":").append(v.getUserId())
              .append(",\"userName\":").append(JsonUtil.escape(v.getUserName()))
              .append(",\"visitDate\":").append(JsonUtil.escape(v.getVisitDate() != null ? v.getVisitDate().toString() : ""))
              .append(",\"visitTime\":").append(JsonUtil.escape(v.getVisitTime() != null ? v.getVisitTime().toString() : ""))
              .append(",\"contactNumber\":").append(JsonUtil.escape(v.getContactNumber()))
              .append(",\"notes\":").append(JsonUtil.escape(v.getNotes()))
              .append(",\"status\":").append(JsonUtil.escape(v.getAppointmentStatus()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
