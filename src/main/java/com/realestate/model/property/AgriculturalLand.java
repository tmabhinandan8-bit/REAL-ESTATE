package com.realestate.model.property;

import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Concrete class representing Agricultural Land:
 * Farmland, agricultural plots, plantations, and farm land for lease.
 */
public class AgriculturalLand extends Property {
    private String landType; // FARMLAND, AGRICULTURAL_PLOT, PLANTATION, LEASE_LAND
    private double totalLandArea;
    private String landUnit; // ACRES, GUNTHAS, CENTS, HECTARES
    private String soilType;
    private String waterAvailability;
    private boolean hasBorewell;
    private String roadConnectivity;
    private boolean hasElectricity;
    private String landDocumentType;
    private String surveyNumber;

    public AgriculturalLand() {
        super();
        this.setCategoryId(3);
        this.setCategoryCode("AGRICULTURAL");
        this.setCategoryName("Agricultural Land");
        this.setAreaUnit("Acres");
        this.landUnit = "ACRES";
    }

    @Override
    public String getPropertyTypeDetails() {
        return (landType != null ? landType.replace('_', ' ') : "Farmland") + 
               " | Area: " + (totalLandArea > 0 ? totalLandArea : getPropertyArea()) + " " + landUnit + 
               " | Soil: " + (soilType != null ? soilType : "Agricultural");
    }

    @Override
    public double calculateEstimatedMaintenanceCost() {
        // Agricultural land has low municipal maintenance, roughly Rs. 1500 per acre / month
        return (totalLandArea > 0 ? totalLandArea : getPropertyArea()) * 1500.0;
    }

    @Override
    public void validateCategorySpecifics() throws InvalidPropertyDataException {
        if (totalLandArea <= 0 && getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Agricultural land must specify a positive land area in acres/cents.");
        }
    }

    // Getters and Setters
    public String getLandType() { return landType; }
    public void setLandType(String landType) { this.landType = landType; }

    public double getTotalLandArea() { return totalLandArea; }
    public void setTotalLandArea(double totalLandArea) { this.totalLandArea = totalLandArea; }

    public String getLandUnit() { return landUnit; }
    public void setLandUnit(String landUnit) { this.landUnit = landUnit; }

    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }

    public String getWaterAvailability() { return waterAvailability; }
    public void setWaterAvailability(String waterAvailability) { this.waterAvailability = waterAvailability; }

    public boolean isHasBorewell() { return hasBorewell; }
    public void setHasBorewell(boolean hasBorewell) { this.hasBorewell = hasBorewell; }

    public String getRoadConnectivity() { return roadConnectivity; }
    public void setRoadConnectivity(String roadConnectivity) { this.roadConnectivity = roadConnectivity; }

    public boolean isHasElectricity() { return hasElectricity; }
    public void setHasElectricity(boolean hasElectricity) { this.hasElectricity = hasElectricity; }

    public String getLandDocumentType() { return landDocumentType; }
    public void setLandDocumentType(String landDocumentType) { this.landDocumentType = landDocumentType; }

    public String getSurveyNumber() { return surveyNumber; }
    public void setSurveyNumber(String surveyNumber) { this.surveyNumber = surveyNumber; }
}
