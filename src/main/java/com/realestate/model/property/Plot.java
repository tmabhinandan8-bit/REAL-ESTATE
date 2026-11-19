package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Concrete class representing Plots and Sites:
 * Residential plots, commercial plots, corner sites, empty land, and construction sites.
 */
public class Plot extends Property {
    private String plotType; // RESIDENTIAL_PLOT, COMMERCIAL_PLOT, CORNER_SITE, EMPTY_LAND, GATED_COMMUNITY
    private String plotDimensions; // e.g., "30x40", "40x60", "50x80"
    private double totalArea;
    private String facingDirection; // NORTH, SOUTH, EAST, WEST, NORTH_EAST, etc.
    private int roadWidthFeet;
    private boolean isApprovedLayout;
    private String approvalAuthority; // e.g., MUDA, BDA, BBMP, DTCP

    public Plot() {
        super();
        this.setCategoryId(5);
        this.setCategoryCode("PLOT");
        this.setCategoryName("Plots and Sites");
        this.roadWidthFeet = 30;
        this.isApprovedLayout = true;
    }

    @Override
    public String getPropertyTypeDetails() {
        return (plotType != null ? plotType.replace('_', ' ') : "Plot") + 
               " | Dim: " + (plotDimensions != null ? plotDimensions : "Custom") + 
               " | Facing: " + (facingDirection != null ? facingDirection : "East") + 
               " | Authority: " + (approvalAuthority != null ? approvalAuthority : "Approved");
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Empty plots have modest layout maintenance (Rs. 500 per month)
        return 500.0;
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (totalArea <= 0 && getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Plot must have valid dimensions or total area.");
        }
    }

    // Getters and Setters
    public String getPlotType() { return plotType; }
    public void setPlotType(String plotType) { this.plotType = plotType; }

    public String getPlotDimensions() { return plotDimensions; }
    public void setPlotDimensions(String plotDimensions) { this.plotDimensions = plotDimensions; }

    public double getTotalArea() { return totalArea; }
    public void setTotalArea(double totalArea) { this.totalArea = totalArea; }

    public String getFacingDirection() { return facingDirection; }
    public void setFacingDirection(String facingDirection) { this.facingDirection = facingDirection; }

    public int getRoadWidthFeet() { return roadWidthFeet; }
    public void setRoadWidthFeet(int roadWidthFeet) { this.roadWidthFeet = roadWidthFeet; }

    public boolean isApprovedLayout() { return isApprovedLayout; }
    public void setApprovedLayout(boolean approvedLayout) { isApprovedLayout = approvedLayout; }

    public String getApprovalAuthority() { return approvalAuthority; }
    public void setApprovalAuthority(String approvalAuthority) { this.approvalAuthority = approvalAuthority; }
}
