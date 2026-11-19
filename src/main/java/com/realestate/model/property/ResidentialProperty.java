package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Concrete class representing Residential Properties:
 * Independent houses, apartments, flats, villas, duplexes, and PG accommodations.
 */
public class ResidentialProperty extends Property {
    private String residentialType; // APARTMENT, INDEPENDENT_HOUSE, FLAT, VILLA, DUPLEX, PG
    private int bedrooms;
    private int bathrooms;
    private double builtUpArea;
    private double carpetArea;
    private int parkingSlots;
    private String furnishingStatus; // UNFURNISHED, SEMI_FURNISHED, FULLY_FURNISHED
    private int floorNumber;
    private int totalFloors;
    private int propertyAgeYears;

    public ResidentialProperty() {
        super();
        this.setCategoryId(1);
        this.setCategoryCode("RESIDENTIAL");
        this.setCategoryName("Residential Properties");
        this.bedrooms = 2;
        this.bathrooms = 2;
    }

    public ResidentialProperty(int propertyId, int ownerId, String title, String purpose, double price, double propertyArea, String state, String city, int bedrooms, int bathrooms) {
        super(propertyId, ownerId, title, purpose, price, propertyArea, state, city);
        this.setCategoryId(1);
        this.setCategoryCode("RESIDENTIAL");
        this.setCategoryName("Residential Properties");
        this.bedrooms = bedrooms;
        this.bathrooms = bathrooms;
        this.builtUpArea = propertyArea;
    }

    @Override
    public String getPropertyTypeDetails() {
        return bedrooms + " BHK " + (residentialType != null ? residentialType : "Residential") + 
               " | " + bathrooms + " Baths | " + furnishingStatus;
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Average residential maintenance Rs. 2.5 per sq ft built-up area per month
        return (builtUpArea > 0 ? builtUpArea : getPropertyArea()) * 2.5;
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (bedrooms <= 0) {
            throw new InvalidPropertyDataException("Residential property must have at least 1 bedroom/unit.");
        }
        if (builtUpArea <= 0 && getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Residential property must have a valid built-up or carpet area.");
        }
    }

    // Getters and Setters
    public String getResidentialType() { return residentialType; }
    public void setResidentialType(String residentialType) { this.residentialType = residentialType; }

    public int getBedrooms() { return bedrooms; }
    public void setBedrooms(int bedrooms) { this.bedrooms = bedrooms; }

    public int getBathrooms() { return bathrooms; }
    public void setBathrooms(int bathrooms) { this.bathrooms = bathrooms; }

    public double getBuiltUpArea() { return builtUpArea; }
    public void setBuiltUpArea(double builtUpArea) { this.builtUpArea = builtUpArea; }

    public double getCarpetArea() { return carpetArea; }
    public void setCarpetArea(double carpetArea) { this.carpetArea = carpetArea; }

    public int getParkingSlots() { return parkingSlots; }
    public void setParkingSlots(int parkingSlots) { this.parkingSlots = parkingSlots; }

    public String getFurnishingStatus() { return furnishingStatus; }
    public void setFurnishingStatus(String furnishingStatus) { this.furnishingStatus = furnishingStatus; }

    public int getFloorNumber() { return floorNumber; }
    public void setFloorNumber(int floorNumber) { this.floorNumber = floorNumber; }

    public int getTotalFloors() { return totalFloors; }
    public void setTotalFloors(int totalFloors) { this.totalFloors = totalFloors; }

    public int getPropertyAgeYears() { return propertyAgeYears; }
    public void setPropertyAgeYears(int propertyAgeYears) { this.propertyAgeYears = propertyAgeYears; }
}
