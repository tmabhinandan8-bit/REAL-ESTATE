package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Concrete class representing Farmhouses:
 * Farmhouses for sale, rent, or lease with orchards, gardens, and amenities.
 */
public class FarmHouse extends Property {
    private double landArea;
    private double builtUpArea;
    private int bedrooms;
    private int bathrooms;
    private boolean hasGarden;
    private boolean hasSwimmingPool;
    private int parkingCapacity;
    private String waterFacilities;

    public FarmHouse() {
        super();
        this.setCategoryId(4);
        this.setCategoryCode("FARMHOUSE");
        this.setCategoryName("Farmhouses");
        this.hasGarden = true;
        this.bedrooms = 2;
        this.bathrooms = 2;
    }

    @Override
    public String getPropertyTypeDetails() {
        return "Farmhouse on " + (landArea > 0 ? landArea : getPropertyArea()) + " Acres | " + 
               bedrooms + " Bed Villa | " + (hasSwimmingPool ? "Private Pool" : "Lush Garden");
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Farmhouses have garden and estate maintenance: base Rs. 5000 + Rs. 1000 per acre
        return 5000.0 + (landArea * 1000.0);
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (landArea <= 0 && getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Farmhouse must have a designated land area.");
        }
    }

    // Getters and Setters
    public double getLandArea() { return landArea; }
    public void setLandArea(double landArea) { this.landArea = landArea; }

    public double getBuiltUpArea() { return builtUpArea; }
    public void setBuiltUpArea(double builtUpArea) { this.builtUpArea = builtUpArea; }

    public int getBedrooms() { return bedrooms; }
    public void setBedrooms(int bedrooms) { this.bedrooms = bedrooms; }

    public int getBathrooms() { return bathrooms; }
    public void setBathrooms(int bathrooms) { this.bathrooms = bathrooms; }

    public boolean isHasGarden() { return hasGarden; }
    public void setHasGarden(boolean hasGarden) { this.hasGarden = hasGarden; }

    public boolean isHasSwimmingPool() { return hasSwimmingPool; }
    public void setHasSwimmingPool(boolean hasSwimmingPool) { this.hasSwimmingPool = hasSwimmingPool; }

    public int getParkingCapacity() { return parkingCapacity; }
    public void setParkingCapacity(int parkingCapacity) { this.parkingCapacity = parkingCapacity; }

    public String getWaterFacilities() { return waterFacilities; }
    public void setWaterFacilities(String waterFacilities) { this.waterFacilities = waterFacilities; }
}
