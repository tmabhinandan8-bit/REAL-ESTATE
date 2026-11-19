package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Concrete class representing Commercial Properties:
 * Shops, office spaces, showrooms, warehouses, and commercial buildings.
 */
public class CommercialProperty extends Property {
    private String commercialType; // SHOP, OFFICE_SPACE, SHOWROOM, WAREHOUSE, COMMERCIAL_BUILDING
    private double carpetArea;
    private int numberOfFloors;
    private int parkingSpaces;
    private String buildingType;
    private String commercialUsage;
    private boolean powerBackup;

    public CommercialProperty() {
        super();
        this.setCategoryId(2);
        this.setCategoryCode("COMMERCIAL");
        this.setCategoryName("Commercial Properties");
        this.numberOfFloors = 1;
        this.powerBackup = true;
    }

    @Override
    public String getPropertyTypeDetails() {
        return (commercialType != null ? commercialType.replace('_', ' ') : "Commercial") + 
               " | Carpet: " + (carpetArea > 0 ? carpetArea : getPropertyArea()) + " Sq. Ft. | " + 
               parkingSpaces + " Car Parks";
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Commercial maintenance typically Rs. 4.5 per sq ft per month for common area and HVAC
        return (carpetArea > 0 ? carpetArea : getPropertyArea()) * 4.5;
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (carpetArea <= 0 && getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Commercial property must specify a valid carpet area.");
        }
    }

    // Getters and Setters
    public String getCommercialType() { return commercialType; }
    public void setCommercialType(String commercialType) { this.commercialType = commercialType; }

    public double getCarpetArea() { return carpetArea; }
    public void setCarpetArea(double carpetArea) { this.carpetArea = carpetArea; }

    public int getNumberOfFloors() { return numberOfFloors; }
    public void setNumberOfFloors(int numberOfFloors) { this.numberOfFloors = numberOfFloors; }

    public int getParkingSpaces() { return parkingSpaces; }
    public void setParkingSpaces(int parkingSpaces) { this.parkingSpaces = parkingSpaces; }

    public String getBuildingType() { return buildingType; }
    public void setBuildingType(String buildingType) { this.buildingType = buildingType; }

    public String getCommercialUsage() { return commercialUsage; }
    public void setCommercialUsage(String commercialUsage) { this.commercialUsage = commercialUsage; }

    public boolean isPowerBackup() { return powerBackup; }
    public void setPowerBackup(boolean powerBackup) { this.powerBackup = powerBackup; }
}
