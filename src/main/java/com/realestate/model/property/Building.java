package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;
import java.sql.Date;

/**
 * Concrete class representing Buildings and Projects:
 * Apartment buildings, residential projects, commercial complexes, and under-construction developments.
 */
public class Building extends Property {
    private String buildingType; // APARTMENT_BUILDING, RESIDENTIAL_PROJECT, COMMERCIAL_COMPLEX, UNDER_CONSTRUCTION, COMPLETED
    private int numberOfFloors;
    private int totalUnits;
    private String constructionStatus; // UNDER_CONSTRUCTION, READY_TO_MOVE, NEW_LAUNCH
    private String builderName;
    private Date projectCompletionDate;
    private String reraNumber;

    public Building() {
        super();
        this.setCategoryId(6);
        this.setCategoryCode("BUILDING");
        this.setCategoryName("Buildings and Projects");
        this.numberOfFloors = 1;
        this.totalUnits = 1;
        this.constructionStatus = "READY_TO_MOVE";
    }

    @Override
    public String getPropertyTypeDetails() {
        return (buildingType != null ? buildingType.replace('_', ' ') : "Building") + 
               " | " + totalUnits + " Units | G+" + numberOfFloors + " Floors | " + 
               (constructionStatus != null ? constructionStatus.replace('_', ' ') : "Completed");
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Multi-unit complex maintenance roughly Rs. 1500 per unit per month
        return totalUnits * 1500.0;
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (totalUnits <= 0) {
            throw new InvalidPropertyDataException("Building project must indicate the total number of units.");
        }
    }

    // Getters and Setters
    public String getBuildingType() { return buildingType; }
    public void setBuildingType(String buildingType) { this.buildingType = buildingType; }

    public int getNumberOfFloors() { return numberOfFloors; }
    public void setNumberOfFloors(int numberOfFloors) { this.numberOfFloors = numberOfFloors; }

    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }

    public String getConstructionStatus() { return constructionStatus; }
    public void setConstructionStatus(String constructionStatus) { this.constructionStatus = constructionStatus; }

    public String getBuilderName() { return builderName; }
    public void setBuilderName(String builderName) { this.builderName = builderName; }

    public Date getProjectCompletionDate() { return projectCompletionDate; }
    public void setProjectCompletionDate(Date projectCompletionDate) { this.projectCompletionDate = projectCompletionDate; }

    public String getReraNumber() { return reraNumber; }
    public void setReraNumber(String reraNumber) { this.reraNumber = reraNumber; }
}
