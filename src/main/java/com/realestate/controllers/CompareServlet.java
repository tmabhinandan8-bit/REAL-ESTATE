package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.realestate.model.property.Property;
import com.realestate.services.PropertyService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/properties/compare")
public class CompareServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private PropertyService propertyService;

    @Override
    public void init() throws ServletException {
        this.propertyService = new PropertyService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        String idsParam = req.getParameter("ids");
        if (idsParam == null || idsParam.trim().isEmpty()) {
            out.print(JsonUtil.error("Please provide at least 2 property IDs to compare", 400));
            return;
        }

        String[] parts = idsParam.split(",");
        List<Property> compareList = new ArrayList<>();

        for (String part : parts) {
            try {
                int id = Integer.parseInt(part.trim());
                Property p = propertyService.getPropertyDetails(id);
                if (p != null) {
                    compareList.add(p);
                }
            } catch (Exception ignored) {}
            if (compareList.size() >= 3) break; // Limit to 3 properties
        }

        if (compareList.isEmpty()) {
            out.print(JsonUtil.error("No valid properties found for comparison", 404));
            return;
        }

        out.print(JsonUtil.success("Comparison properties loaded", PropertyServlet.serializePropertyList(compareList)));
    }
}
