-- =============================================================================
-- REAL ESTATE MANAGEMENT SYSTEM - DATABASE SCHEMA
-- Normalized 18+ Tables with Constraints, Foreign Keys, and Indexes
-- =============================================================================

CREATE DATABASE IF NOT EXISTS real_estate_management
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE real_estate_management;

-- 1. USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    mobile_number VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    user_role ENUM('BUYER', 'SELLER', 'TENANT', 'PROPERTY_OWNER', 'AGENT', 'ADMIN') NOT NULL DEFAULT 'BUYER',
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_email (email),
    INDEX idx_user_role (user_role)
) ENGINE=InnoDB;

-- 2. PROPERTY CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS property_categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL UNIQUE,
    category_code VARCHAR(30) NOT NULL UNIQUE,
    description TEXT,
    icon_class VARCHAR(50) DEFAULT 'bi-building',
    is_active BOOLEAN DEFAULT TRUE
) ENGINE=InnoDB;

-- 3. PROPERTY LOCATIONS TABLE
CREATE TABLE IF NOT EXISTS property_locations (
    location_id INT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    pincode VARCHAR(10),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_city_locality (city, locality)
) ENGINE=InnoDB;

-- 4. PROPERTIES (Core Table - Table-per-Type Base)
CREATE TABLE IF NOT EXISTS properties (
    property_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    category_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    purpose ENUM('BUY', 'RENT', 'LEASE') NOT NULL,
    price DECIMAL(14, 2) NOT NULL,
    property_area DECIMAL(10, 2) NOT NULL COMMENT 'in Sq. Ft. or Cents / Acres',
    area_unit VARCHAR(20) DEFAULT 'Sq. Ft.',
    state VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    address TEXT NOT NULL,
    pincode VARCHAR(10),
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    listing_status ENUM('AVAILABLE', 'UNDER_OFFER', 'SOLD', 'RENTED', 'INACTIVE') DEFAULT 'AVAILABLE',
    is_featured BOOLEAN DEFAULT FALSE,
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES property_categories(category_id),
    INDEX idx_prop_city (city),
    INDEX idx_prop_price (price),
    INDEX idx_prop_purpose (purpose),
    INDEX idx_prop_status (approval_status, listing_status)
) ENGINE=InnoDB;

-- 5. RESIDENTIAL DETAILS (Sub-table for Residential properties)
CREATE TABLE IF NOT EXISTS residential_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    residential_type ENUM('APARTMENT', 'INDEPENDENT_HOUSE', 'FLAT', 'VILLA', 'DUPLEX', 'PG') NOT NULL,
    bedrooms INT NOT NULL DEFAULT 1,
    bathrooms INT NOT NULL DEFAULT 1,
    built_up_area DECIMAL(10, 2) NOT NULL,
    carpet_area DECIMAL(10, 2),
    parking_slots INT DEFAULT 1,
    furnishing_status ENUM('UNFURNISHED', 'SEMI_FURNISHED', 'FULLY_FURNISHED') DEFAULT 'UNFURNISHED',
    floor_number INT DEFAULT 0,
    total_floors INT DEFAULT 1,
    property_age_years INT DEFAULT 0,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. COMMERCIAL DETAILS (Sub-table for Commercial properties)
CREATE TABLE IF NOT EXISTS commercial_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    commercial_type ENUM('SHOP', 'OFFICE_SPACE', 'SHOWROOM', 'WAREHOUSE', 'COMMERCIAL_BUILDING') NOT NULL,
    carpet_area DECIMAL(10, 2) NOT NULL,
    number_of_floors INT DEFAULT 1,
    parking_spaces INT DEFAULT 0,
    building_type VARCHAR(100),
    commercial_usage VARCHAR(255),
    power_backup BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. AGRICULTURAL DETAILS / LAND DETAILS
CREATE TABLE IF NOT EXISTS land_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    land_type ENUM('FARMLAND', 'AGRICULTURAL_PLOT', 'PLANTATION', 'LEASE_LAND') NOT NULL,
    total_land_area DECIMAL(10, 2) NOT NULL,
    land_unit ENUM('ACRES', 'GUNTHAS', 'CENTS', 'HECTARES') DEFAULT 'ACRES',
    soil_type VARCHAR(80),
    water_availability VARCHAR(100),
    has_borewell BOOLEAN DEFAULT FALSE,
    road_connectivity VARCHAR(100),
    has_electricity BOOLEAN DEFAULT TRUE,
    land_document_type VARCHAR(100),
    survey_number VARCHAR(80),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 8. FARMHOUSE DETAILS
CREATE TABLE IF NOT EXISTS farmhouse_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    land_area DECIMAL(10, 2) NOT NULL,
    built_up_area DECIMAL(10, 2) NOT NULL,
    bedrooms INT DEFAULT 2,
    bathrooms INT DEFAULT 2,
    has_garden BOOLEAN DEFAULT TRUE,
    has_swimming_pool BOOLEAN DEFAULT FALSE,
    parking_capacity INT DEFAULT 2,
    water_facilities VARCHAR(150),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 9. PLOTS AND SITES DETAILS
CREATE TABLE IF NOT EXISTS plot_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    plot_type ENUM('RESIDENTIAL_PLOT', 'COMMERCIAL_PLOT', 'CORNER_SITE', 'EMPTY_LAND', 'GATED_COMMUNITY') NOT NULL,
    plot_dimensions VARCHAR(60) COMMENT 'e.g. 30x40, 40x60, 50x80',
    total_area DECIMAL(10, 2) NOT NULL,
    facing_direction ENUM('NORTH', 'SOUTH', 'EAST', 'WEST', 'NORTH_EAST', 'NORTH_WEST', 'SOUTH_EAST', 'SOUTH_WEST'),
    road_width_feet INT DEFAULT 30,
    is_approved_layout BOOLEAN DEFAULT TRUE,
    approval_authority VARCHAR(100) COMMENT 'e.g. MUDA, BDA, BBMP, DTCP',
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 10. BUILDING DETAILS / PROJECTS
CREATE TABLE IF NOT EXISTS building_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    building_type ENUM('APARTMENT_BUILDING', 'RESIDENTIAL_PROJECT', 'COMMERCIAL_COMPLEX', 'UNDER_CONSTRUCTION', 'COMPLETED') NOT NULL,
    number_of_floors INT NOT NULL DEFAULT 1,
    total_units INT NOT NULL DEFAULT 1,
    construction_status ENUM('UNDER_CONSTRUCTION', 'READY_TO_MOVE', 'NEW_LAUNCH') DEFAULT 'READY_TO_MOVE',
    builder_name VARCHAR(120),
    project_completion_date DATE,
    rera_number VARCHAR(100),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 11. PROJECTS (Standalone project portfolio table)
CREATE TABLE IF NOT EXISTS projects (
    project_id INT AUTO_INCREMENT PRIMARY KEY,
    builder_id INT NOT NULL,
    project_name VARCHAR(150) NOT NULL,
    project_type VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    rera_approved BOOLEAN DEFAULT TRUE,
    rera_number VARCHAR(100),
    total_towers INT DEFAULT 1,
    total_units INT DEFAULT 10,
    launch_date DATE,
    possession_date DATE,
    status ENUM('PLANNING', 'ACTIVE', 'COMPLETED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (builder_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 12. PROPERTY IMAGES TABLE
CREATE TABLE IF NOT EXISTS property_images (
    image_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    caption VARCHAR(150),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_prop_images (property_id)
) ENGINE=InnoDB;

-- 13. PROPERTY AMENITIES TABLE
CREATE TABLE IF NOT EXISTS property_amenities (
    amenity_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    amenity_name VARCHAR(80) NOT NULL,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_prop_amenity (property_id, amenity_name)
) ENGINE=InnoDB;

-- 14. PROPERTY REVIEWS TABLE
CREATE TABLE IF NOT EXISTS property_reviews (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    user_id INT NOT NULL,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 15. PROPERTY FAVOURITES TABLE
CREATE TABLE IF NOT EXISTS property_favourites (
    favourite_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    property_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_property_fav (user_id, property_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 16. PROPERTY ENQUIRIES TABLE
CREATE TABLE IF NOT EXISTS property_enquiries (
    enquiry_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    sender_email VARCHAR(120) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    status ENUM('NEW', 'CONTACTED', 'CLOSED') DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 17. PROPERTY VISITS / APPOINTMENTS TABLE
CREATE TABLE IF NOT EXISTS property_visits (
    visit_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    user_id INT NOT NULL,
    visit_date DATE NOT NULL,
    visit_time TIME NOT NULL,
    contact_number VARCHAR(20) NOT NULL,
    notes TEXT,
    appointment_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_visit_date_time (visit_date, visit_time)
) ENGINE=InnoDB;

-- 18. OWNER VERIFICATIONS TABLE
CREATE TABLE IF NOT EXISTS owner_verifications (
    verification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    identity_proof_type VARCHAR(60) NOT NULL COMMENT 'Aadhaar, PAN, Passport',
    identity_proof_number VARCHAR(100) NOT NULL,
    document_path VARCHAR(255) NOT NULL,
    property_address TEXT NOT NULL,
    verification_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    admin_notes TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 19. RENTAL AGREEMENTS TABLE
CREATE TABLE IF NOT EXISTS rental_agreements (
    agreement_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    owner_id INT NOT NULL,
    tenant_id INT NOT NULL,
    monthly_rent DECIMAL(12, 2) NOT NULL,
    security_deposit DECIMAL(12, 2) NOT NULL,
    agreement_start_date DATE NOT NULL,
    agreement_end_date DATE NOT NULL,
    agreement_status ENUM('ACTIVE', 'EXPIRED', 'TERMINATED') DEFAULT 'ACTIVE',
    terms_and_conditions TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(user_id),
    FOREIGN KEY (tenant_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 20. RENT PAYMENTS TABLE
CREATE TABLE IF NOT EXISTS rent_payments (
    rent_payment_id INT AUTO_INCREMENT PRIMARY KEY,
    agreement_id INT NOT NULL,
    payment_month VARCHAR(20) NOT NULL COMMENT 'e.g. October 2026',
    amount_paid DECIMAL(12, 2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50) DEFAULT 'SIMULATED_UPI',
    transaction_ref VARCHAR(100) NOT NULL UNIQUE,
    payment_status ENUM('SUCCESS', 'PENDING', 'FAILED') DEFAULT 'SUCCESS',
    FOREIGN KEY (agreement_id) REFERENCES rental_agreements(agreement_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 21. PURCHASE TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS purchase_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    buyer_id INT NOT NULL,
    seller_id INT NOT NULL,
    purchase_price DECIMAL(14, 2) NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    payment_status ENUM('PENDING', 'SUCCESS', 'FAILED') DEFAULT 'SUCCESS',
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    remarks VARCHAR(255) DEFAULT 'Simulated College Project Purchase Transaction',
    FOREIGN KEY (property_id) REFERENCES properties(property_id),
    FOREIGN KEY (buyer_id) REFERENCES users(user_id),
    FOREIGN KEY (seller_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 22. GENERAL PAYMENTS AUDIT TABLE
CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    property_id INT,
    amount DECIMAL(12, 2) NOT NULL,
    payment_purpose VARCHAR(80) NOT NULL COMMENT 'BOOKING_FEE, RENT, PURCHASE_ADVANCE',
    payment_status ENUM('INITIATED', 'COMPLETED', 'FAILED') DEFAULT 'COMPLETED',
    payment_gateway_ref VARCHAR(100) NOT NULL UNIQUE,
    payment_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
