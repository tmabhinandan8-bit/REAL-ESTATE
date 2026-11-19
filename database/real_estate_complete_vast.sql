-- =============================================================================
-- REAL ESTATE MANAGEMENT SYSTEM - VAST COMPLETE DATABASE SCRIPT (SQL EDITOR READY)
-- Tagline: "Find. Buy. Sell. Rent. Build."
-- Compatible with: MySQL 8.0+ / MariaDB 10.4+ (Workbench, phpMyAdmin, DBeaver, CLI)
--
-- Includes:
--   * 28 Fully Normalized Tables (Table-per-Type OOP Inheritance + Enterprise Modules)
--   * 20 Users across all 6 Roles (Admin, Owner, Seller, Buyer, Tenant, RERA Agent)
--   * 30 Indian Cities & Localities (Karnataka, TN, TS, MH, KL, GJ, RJ, DL, WB)
--   * 30 Rich Properties covering ALL 6 Categories (Buy / Rent / Lease)
--   * 6 Builder Projects, 12 Project Units, 45+ Images, 95+ Amenities
--   * Reviews, Favourites, Enquiries, Visits, KYC Verifications, Rentals, Purchases
--   * Price History, Notifications, Unified Payments, Audit Logs
--   * 3 Analytical Views, 3 Automation Triggers, 2 Stored Procedures
--
-- Demo Credentials (Salt: salt1234567890ab):
--   Admin  : admin@realestate.com        / Admin@123
--   Owner  : rajesh.owner@example.com    / Password@123
--   Seller : anand.seller@example.com    / Password@123
--   Buyer  : priya.buyer@example.com     / Password@123
--   Tenant : kiran.tenant@example.com    / Password@123
--   Agent  : suresh.agent@example.com    / Password@123
-- =============================================================================

DROP DATABASE IF EXISTS real_estate_management;

CREATE DATABASE real_estate_management
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE real_estate_management;

-- =============================================================================
-- PART 1: CORE SCHEMA DEFINITION (28 NORMALIZED TABLES)
-- =============================================================================

-- 1. USERS TABLE
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    mobile_number VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    user_role ENUM('BUYER', 'SELLER', 'TENANT', 'PROPERTY_OWNER', 'AGENT', 'ADMIN') NOT NULL DEFAULT 'BUYER',
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_email (email),
    INDEX idx_user_role (user_role),
    INDEX idx_user_verified (is_verified, is_active)
) ENGINE=InnoDB;

-- 2. USER PROFILES (Extended KYC, Agent RERA & Builder Details)
CREATE TABLE user_profiles (
    profile_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    company_or_agency VARCHAR(150),
    rera_registration_no VARCHAR(100),
    pan_masked VARCHAR(20),
    city VARCHAR(80),
    state VARCHAR(80),
    bio TEXT,
    experience_years INT DEFAULT 0,
    profile_image_url VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. PROPERTY CATEGORIES TABLE
CREATE TABLE property_categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(60) NOT NULL UNIQUE,
    category_code VARCHAR(30) NOT NULL UNIQUE,
    description TEXT,
    icon_class VARCHAR(50) DEFAULT 'bi-building',
    display_order INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE
) ENGINE=InnoDB;

-- 4. PROPERTY LOCATIONS TABLE (Normalized Indian Geo-Directory)
CREATE TABLE property_locations (
    location_id INT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    zone_type ENUM('METRO', 'TIER_1', 'TIER_2', 'RURAL_AGRI', 'HILL_ESTATE', 'COASTAL') DEFAULT 'TIER_1',
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    avg_price_per_sqft DECIMAL(10, 2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_city_locality (city, locality),
    INDEX idx_state_city (state, city)
) ENGINE=InnoDB;

-- 5. PROPERTIES TABLE (Table-per-Type OOP Base Table)
CREATE TABLE properties (
    property_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    category_id INT NOT NULL,
    location_id INT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    purpose ENUM('BUY', 'RENT', 'LEASE') NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    price_negotiable BOOLEAN DEFAULT TRUE,
    property_area DECIMAL(12, 2) NOT NULL COMMENT 'Area in Sq. Ft. or Acres',
    area_unit VARCHAR(20) DEFAULT 'Sq. Ft.',
    state VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    address TEXT NOT NULL,
    pincode VARCHAR(10),
    primary_image_url VARCHAR(255),
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    listing_status ENUM('AVAILABLE', 'UNDER_OFFER', 'SOLD', 'RENTED', 'INACTIVE') DEFAULT 'AVAILABLE',
    is_featured BOOLEAN DEFAULT FALSE,
    views_count INT DEFAULT 0,
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES property_categories(category_id),
    FOREIGN KEY (location_id) REFERENCES property_locations(location_id) ON DELETE SET NULL,
    INDEX idx_prop_city (city),
    INDEX idx_prop_price (price),
    INDEX idx_prop_purpose (purpose),
    INDEX idx_prop_category (category_id),
    INDEX idx_prop_status (approval_status, listing_status),
    INDEX idx_prop_featured (is_featured, approval_status)
) ENGINE=InnoDB;

-- 6. RESIDENTIAL DETAILS (TPT Sub-table A)
CREATE TABLE residential_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    residential_type ENUM('APARTMENT', 'INDEPENDENT_HOUSE', 'FLAT', 'VILLA', 'DUPLEX', 'PG') NOT NULL,
    bedrooms INT NOT NULL DEFAULT 1,
    bathrooms INT NOT NULL DEFAULT 1,
    balconies INT DEFAULT 1,
    built_up_area DECIMAL(10, 2) NOT NULL,
    carpet_area DECIMAL(10, 2),
    parking_slots INT DEFAULT 1,
    furnishing_status ENUM('UNFURNISHED', 'SEMI_FURNISHED', 'FULLY_FURNISHED') DEFAULT 'UNFURNISHED',
    floor_number INT DEFAULT 0,
    total_floors INT DEFAULT 1,
    facing_direction ENUM('NORTH', 'SOUTH', 'EAST', 'WEST', 'NORTH_EAST', 'NORTH_WEST', 'SOUTH_EAST', 'SOUTH_WEST') DEFAULT 'EAST',
    property_age_years INT DEFAULT 0,
    maintenance_monthly DECIMAL(10, 2) DEFAULT 0.00,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. COMMERCIAL DETAILS (TPT Sub-table B)
CREATE TABLE commercial_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    commercial_type ENUM('SHOP', 'OFFICE_SPACE', 'SHOWROOM', 'WAREHOUSE', 'COMMERCIAL_BUILDING') NOT NULL,
    carpet_area DECIMAL(10, 2) NOT NULL,
    super_built_up_area DECIMAL(10, 2),
    number_of_floors INT DEFAULT 1,
    parking_spaces INT DEFAULT 0,
    seating_capacity INT DEFAULT 0,
    washrooms INT DEFAULT 1,
    building_type VARCHAR(100),
    commercial_usage VARCHAR(255),
    power_backup BOOLEAN DEFAULT TRUE,
    central_ac BOOLEAN DEFAULT FALSE,
    fire_noc_approved BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 8. AGRICULTURAL LAND DETAILS (TPT Sub-table C)
CREATE TABLE land_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    land_type ENUM('FARMLAND', 'AGRICULTURAL_PLOT', 'PLANTATION', 'LEASE_LAND') NOT NULL,
    total_land_area DECIMAL(10, 2) NOT NULL,
    land_unit ENUM('ACRES', 'GUNTHAS', 'CENTS', 'HECTARES') DEFAULT 'ACRES',
    soil_type VARCHAR(80),
    crop_suitability VARCHAR(150),
    water_availability VARCHAR(120),
    has_borewell BOOLEAN DEFAULT FALSE,
    borewell_count INT DEFAULT 0,
    road_connectivity VARCHAR(120),
    has_electricity BOOLEAN DEFAULT TRUE,
    fencing_status ENUM('FULLY_FENCED', 'PARTIAL', 'OPEN') DEFAULT 'FULLY_FENCED',
    land_document_type VARCHAR(120),
    survey_number VARCHAR(80),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 9. FARMHOUSE DETAILS (TPT Sub-table D)
CREATE TABLE farmhouse_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    land_area DECIMAL(10, 2) NOT NULL,
    built_up_area DECIMAL(10, 2) NOT NULL,
    bedrooms INT DEFAULT 2,
    bathrooms INT DEFAULT 2,
    has_garden BOOLEAN DEFAULT TRUE,
    has_swimming_pool BOOLEAN DEFAULT FALSE,
    has_caretaker_quarters BOOLEAN DEFAULT TRUE,
    parking_capacity INT DEFAULT 2,
    water_facilities VARCHAR(150),
    plantation_details VARCHAR(200),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 10. PLOTS AND SITES DETAILS (TPT Sub-table E)
CREATE TABLE plot_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    plot_type ENUM('RESIDENTIAL_PLOT', 'COMMERCIAL_PLOT', 'CORNER_SITE', 'EMPTY_LAND', 'GATED_COMMUNITY') NOT NULL,
    plot_dimensions VARCHAR(60) COMMENT 'e.g. 30x40, 40x60, 50x80',
    total_area DECIMAL(10, 2) NOT NULL,
    facing_direction ENUM('NORTH', 'SOUTH', 'EAST', 'WEST', 'NORTH_EAST', 'NORTH_WEST', 'SOUTH_EAST', 'SOUTH_WEST'),
    road_width_feet INT DEFAULT 30,
    boundary_wall_built BOOLEAN DEFAULT FALSE,
    is_approved_layout BOOLEAN DEFAULT TRUE,
    approval_authority VARCHAR(120) COMMENT 'MUDA, BDA, BBMP, DTCP, HMDA, CMDA',
    khata_type VARCHAR(60) DEFAULT 'A-Khata / Clear Title',
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 11. BUILDING DETAILS (TPT Sub-table F)
CREATE TABLE building_details (
    detail_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL UNIQUE,
    building_type ENUM('APARTMENT_BUILDING', 'RESIDENTIAL_PROJECT', 'COMMERCIAL_COMPLEX', 'UNDER_CONSTRUCTION', 'COMPLETED') NOT NULL,
    number_of_floors INT NOT NULL DEFAULT 1,
    total_units INT NOT NULL DEFAULT 1,
    available_units INT DEFAULT 1,
    construction_status ENUM('UNDER_CONSTRUCTION', 'READY_TO_MOVE', 'NEW_LAUNCH') DEFAULT 'READY_TO_MOVE',
    builder_name VARCHAR(120),
    project_completion_date DATE,
    rera_number VARCHAR(100),
    occupancy_certificate BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 12. PROJECTS TABLE (Builder Portfolio & Townships)
CREATE TABLE projects (
    project_id INT AUTO_INCREMENT PRIMARY KEY,
    builder_id INT NOT NULL,
    location_id INT NULL,
    project_name VARCHAR(150) NOT NULL,
    project_type VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    locality VARCHAR(120) NOT NULL,
    total_land_acres DECIMAL(8, 2) DEFAULT 1.00,
    rera_approved BOOLEAN DEFAULT TRUE,
    rera_number VARCHAR(100),
    total_towers INT DEFAULT 1,
    total_units INT DEFAULT 10,
    starting_price DECIMAL(14, 2) DEFAULT 0.00,
    launch_date DATE,
    possession_date DATE,
    status ENUM('PLANNING', 'ACTIVE', 'COMPLETED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (builder_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES property_locations(location_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 13. PROJECT UNITS INVENTORY TABLE
CREATE TABLE project_units (
    unit_id INT AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL,
    tower_name VARCHAR(50) NOT NULL,
    unit_number VARCHAR(30) NOT NULL,
    unit_config VARCHAR(40) NOT NULL COMMENT '2 BHK, 3 BHK, Office Suite, Penthouse',
    carpet_area DECIMAL(10, 2) NOT NULL,
    floor_no INT NOT NULL,
    unit_price DECIMAL(14, 2) NOT NULL,
    unit_status ENUM('AVAILABLE', 'BOOKED', 'SOLD') DEFAULT 'AVAILABLE',
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    UNIQUE KEY uq_project_tower_unit (project_id, tower_name, unit_number)
) ENGINE=InnoDB;

-- 14. PROPERTY IMAGES TABLE
CREATE TABLE property_images (
    image_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    caption VARCHAR(150),
    sort_order INT DEFAULT 1,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_prop_images (property_id)
) ENGINE=InnoDB;

-- 15. MASTER AMENITIES CATALOG
CREATE TABLE amenities_master (
    master_amenity_id INT AUTO_INCREMENT PRIMARY KEY,
    amenity_name VARCHAR(80) NOT NULL UNIQUE,
    amenity_group ENUM('SECURITY', 'LEISURE', 'UTILITY', 'FARM_LAND', 'COMMERCIAL', 'ENVIRONMENT') DEFAULT 'UTILITY',
    icon_class VARCHAR(50) DEFAULT 'bi-check-circle'
) ENGINE=InnoDB;

-- 16. PROPERTY AMENITIES TABLE
CREATE TABLE property_amenities (
    amenity_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    amenity_name VARCHAR(80) NOT NULL,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_prop_amenity (property_id, amenity_name)
) ENGINE=InnoDB;

-- 17. PROPERTY REVIEWS TABLE
CREATE TABLE property_reviews (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    user_id INT NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_title VARCHAR(120),
    comment TEXT,
    is_verified_visit BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_review_prop (property_id)
) ENGINE=InnoDB;

-- 18. PROPERTY FAVOURITES TABLE
CREATE TABLE property_favourites (
    favourite_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    property_id INT NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_property_fav (user_id, property_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 19. PROPERTY ENQUIRIES TABLE
CREATE TABLE property_enquiries (
    enquiry_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    user_id INT NULL,
    sender_name VARCHAR(100) NOT NULL,
    sender_email VARCHAR(120) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    status ENUM('NEW', 'CONTACTED', 'FOLLOW_UP', 'CLOSED') DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 20. PROPERTY VISITS / APPOINTMENTS TABLE
CREATE TABLE property_visits (
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
    INDEX idx_visit_date_time (visit_date, visit_time),
    INDEX idx_visit_status (appointment_status)
) ENGINE=InnoDB;

-- 21. OWNER VERIFICATIONS (KYC & Title Deed Vault)
CREATE TABLE owner_verifications (
    verification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    identity_proof_type ENUM('AADHAAR', 'PAN', 'PASSPORT', 'DRIVING_LICENSE', 'RERA_CERT') NOT NULL,
    identity_proof_number VARCHAR(100) NOT NULL,
    ownership_document_type VARCHAR(100) DEFAULT 'Registered Sale Deed / RTC',
    document_path VARCHAR(255) NOT NULL COMMENT 'Protected non-public server path',
    supporting_doc_path VARCHAR(255),
    property_address TEXT NOT NULL,
    verification_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    admin_notes TEXT,
    reviewed_by INT NULL,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 22. RENTAL AGREEMENTS TABLE
CREATE TABLE rental_agreements (
    agreement_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    owner_id INT NOT NULL,
    tenant_id INT NOT NULL,
    monthly_rent DECIMAL(12, 2) NOT NULL,
    security_deposit DECIMAL(12, 2) NOT NULL,
    maintenance_charges DECIMAL(10, 2) DEFAULT 0.00,
    annual_escalation_pct DECIMAL(4, 2) DEFAULT 5.00,
    agreement_start_date DATE NOT NULL,
    agreement_end_date DATE NOT NULL,
    agreement_status ENUM('ACTIVE', 'EXPIRED', 'TERMINATED') DEFAULT 'ACTIVE',
    terms_and_conditions TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(user_id),
    FOREIGN KEY (tenant_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 23. RENT PAYMENTS TABLE
CREATE TABLE rent_payments (
    rent_payment_id INT AUTO_INCREMENT PRIMARY KEY,
    agreement_id INT NOT NULL,
    payment_month VARCHAR(25) NOT NULL COMMENT 'e.g. November 2026',
    amount_paid DECIMAL(12, 2) NOT NULL,
    late_fee DECIMAL(10, 2) DEFAULT 0.00,
    payment_date DATE NOT NULL,
    payment_method ENUM('SIMULATED_UPI', 'SIMULATED_NEFT', 'SIMULATED_CARD', 'BANK_CHEQUE') DEFAULT 'SIMULATED_UPI',
    transaction_ref VARCHAR(100) NOT NULL UNIQUE,
    payment_status ENUM('SUCCESS', 'PENDING', 'FAILED') DEFAULT 'SUCCESS',
    FOREIGN KEY (agreement_id) REFERENCES rental_agreements(agreement_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 24. PURCHASE TRANSACTIONS TABLE
CREATE TABLE purchase_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    buyer_id INT NOT NULL,
    seller_id INT NOT NULL,
    purchase_price DECIMAL(15, 2) NOT NULL,
    stamp_duty_est DECIMAL(14, 2) DEFAULT 0.00,
    registration_fee_est DECIMAL(14, 2) DEFAULT 0.00,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    payment_mode ENUM('SIMULATED_RTGS', 'SIMULATED_HOME_LOAN', 'SIMULATED_UPI', 'SIMULATED_ESCROW') DEFAULT 'SIMULATED_RTGS',
    payment_status ENUM('PENDING', 'SUCCESS', 'FAILED') DEFAULT 'SUCCESS',
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    remarks VARCHAR(255) DEFAULT 'Simulated College Project Purchase Transaction (Not Legal Registration)',
    FOREIGN KEY (property_id) REFERENCES properties(property_id),
    FOREIGN KEY (buyer_id) REFERENCES users(user_id),
    FOREIGN KEY (seller_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 25. GENERAL PAYMENTS AUDIT TABLE
CREATE TABLE payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    property_id INT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    payment_purpose ENUM('BOOKING_TOKEN', 'MONTHLY_RENT', 'SECURITY_DEPOSIT', 'PURCHASE_SETTLEMENT', 'FEATURED_LISTING_FEE') NOT NULL,
    payment_status ENUM('INITIATED', 'COMPLETED', 'FAILED', 'REFUNDED') DEFAULT 'COMPLETED',
    payment_gateway_ref VARCHAR(100) NOT NULL UNIQUE,
    payment_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 26. PROPERTY PRICE HISTORY TABLE (Analytics & Appreciation Tracking)
CREATE TABLE property_price_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    old_price DECIMAL(15, 2) NOT NULL,
    new_price DECIMAL(15, 2) NOT NULL,
    change_reason VARCHAR(150),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 27. NOTIFICATIONS LOG TABLE (Persists AsyncNotificationService Queue)
CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    notification_type ENUM('APPROVAL', 'VISIT', 'PAYMENT', 'VERIFICATION', 'ENQUIRY', 'SYSTEM') DEFAULT 'SYSTEM',
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 28. SYSTEM AUDIT LOGS TABLE
CREATE TABLE audit_logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id INT NULL,
    action_type VARCHAR(80) NOT NULL,
    entity_table VARCHAR(60) NOT NULL,
    entity_id INT NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor_user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_audit_entity (entity_table, entity_id)
) ENGINE=InnoDB;


-- =============================================================================
-- PART 2: VAST REALISTIC INDIAN SAMPLE DATASET
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. USERS (20 Users across all 6 Roles)
-- Salt: salt1234567890ab | Admin: Admin@123 | Others: Password@123
-- -----------------------------------------------------------------------------
INSERT INTO users (user_id, full_name, email, mobile_number, password_hash, password_salt, user_role, is_verified, is_active) VALUES
(1,  'System Administrator',        'admin@realestate.com',       '+91 9880011223', '21406a47caa8fa774455ce9f311d8cedd00d176ef0333cde739bac5486e78e59', 'salt1234567890ab', 'ADMIN',          TRUE,  TRUE),
(2,  'Rajesh Gowda',                'rajesh.owner@example.com',   '+91 9845012345', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', TRUE,  TRUE),
(3,  'Anand Kulkarni',              'anand.seller@example.com',   '+91 9845198765', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'SELLER',         TRUE,  TRUE),
(4,  'Priya Sharma',                'priya.buyer@example.com',    '+91 9980123456', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'BUYER',          FALSE, TRUE),
(5,  'Kiran Kumar',                 'kiran.tenant@example.com',   '+91 9740112233', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'TENANT',         FALSE, TRUE),
(6,  'Suresh Reddy Properties',     'suresh.agent@example.com',   '+91 9900887766', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'AGENT',          TRUE,  TRUE),
(7,  'Meenakshi Iyer',              'meenakshi.owner@example.com','+91 9840123490', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', TRUE,  TRUE),
(8,  'Vikramaditya Deshmukh',       'vikram.seller@example.com',  '+91 9820456712', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'SELLER',         TRUE,  TRUE),
(9,  'Sneha Nair',                  'sneha.buyer@example.com',    '+91 9447112244', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'BUYER',          FALSE, TRUE),
(10, 'Rohan Mehta',                 'rohan.tenant@example.com',   '+91 9898223344', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'TENANT',         FALSE, TRUE),
(11, 'Deccan Horizon Builders',     'deccan.agent@example.com',   '+91 9849012300', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'AGENT',          TRUE,  TRUE),
(12, 'Basavaraj Patil',             'basavaraj.owner@example.com','+91 9448119900', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', FALSE, TRUE),
(13, 'Kavitha Subramanian',         'kavitha.owner@example.com',  '+91 9841223399', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', TRUE,  TRUE),
(14, 'Arjun Malhotra',              'arjun.buyer@example.com',    '+91 9811009988', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'BUYER',          FALSE, TRUE),
(15, 'Deepika Hegde',               'deepika.seller@example.com', '+91 9845667788', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'SELLER',         TRUE,  TRUE),
(16, 'Siddharth Joshi',             'siddharth.tenant@example.com','+91 9765443322','451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'TENANT',         FALSE, TRUE),
(17, 'Malnad Estates & Plantations','malnad.agent@example.com',   '+91 9448055667', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'AGENT',          TRUE,  TRUE),
(18, 'Farhan Siddiqui',             'farhan.buyer@example.com',   '+91 9866123987', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'BUYER',          FALSE, TRUE),
(19, 'Lakshmi Narayanan',           'lakshmi.owner@example.com',  '+91 9940556611', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', FALSE, TRUE),
(20, 'Compliance Admin Mysuru',     'audit.admin@realestate.com', '+91 9880099112', '21406a47caa8fa774455ce9f311d8cedd00d176ef0333cde739bac5486e78e59', 'salt1234567890ab', 'ADMIN',          TRUE,  TRUE);

-- -----------------------------------------------------------------------------
-- 2. USER PROFILES
-- -----------------------------------------------------------------------------
INSERT INTO user_profiles (user_id, company_or_agency, rera_registration_no, pan_masked, city, state, bio, experience_years) VALUES
(2,  'Gowda Heritage Holdings',       NULL,                                    'ABCPG12XXF', 'Mysuru',    'Karnataka',   'Verified landowner & villa developer in Mysuru and Sakleshpur.', 12),
(3,  'Kulkarni Estates',              NULL,                                    'BCDEK45XXM', 'Bengaluru', 'Karnataka',   'Direct property seller specializing in urban apartments & agricultural lands.', 9),
(6,  'Suresh Reddy Properties LLP',   'PRM/KA/RERA/1251/446/AG/210412/002311', 'CDEPR78XXK', 'Bengaluru', 'Karnataka',   'RERA certified commercial & residential real estate consultancy.', 15),
(7,  'Iyer Family Trust',             NULL,                                    'DEFPI90XXQ', 'Chennai',   'Tamil Nadu',  'Owner of prime residential apartments and OMR commercial plots.', 10),
(8,  'Deshmukh Infra & Land',         NULL,                                    'EFGPD34XXP', 'Pune',      'Maharashtra', 'Direct seller of Hinjewadi IT suites and Pune weekend farmhouses.', 11),
(11, 'Deccan Horizon Builders Pvt Ltd','P02400004512',                         'FGHPD56XXN', 'Hyderabad', 'Telangana',   'Leading RERA builder delivering residential towers and tech parks.', 18),
(13, 'Subramanian Assets',            NULL,                                    'GHIPS89XXR', 'Coimbatore','Tamil Nadu',  'Verified owner of coconut plantations and gated residential layouts.', 8),
(15, 'Hegde Coastal Realty',          NULL,                                    'HIJPH11XXT', 'Mangaluru', 'Karnataka',   'Coastal villas, commercial complexes, and Udupi residential sites.', 7),
(17, 'Malnad Estates & Plantations',  'PRM/KA/RERA/1250/303/AG/220819/004892', 'IJKPM22XXZ', 'Hassan',    'Karnataka',   'Specialist brokerage for coffee estates, spice farms, and hill farmhouses.', 14);

-- -----------------------------------------------------------------------------
-- 3. PROPERTY CATEGORIES (6 Core Categories)
-- -----------------------------------------------------------------------------
INSERT INTO property_categories (category_id, category_name, category_code, description, icon_class, display_order) VALUES
(1, 'Residential Properties', 'RESIDENTIAL',  'Independent houses, Apartments, Flats, Villas, Duplex houses and PG accommodations', 'bi-house-heart', 1),
(2, 'Commercial Properties',  'COMMERCIAL',   'Shops, Office spaces, Showrooms, Warehouses and Commercial buildings',              'bi-building',    2),
(3, 'Agricultural Land',      'AGRICULTURAL', 'Farmlands, Agricultural plots, Coffee/Coconut Plantations and Lease lands',         'bi-tree',        3),
(4, 'Farmhouses',             'FARMHOUSE',    'Luxury farmhouses, Country estates and Weekend getaway villas for Sale/Rent/Lease',   'bi-flower1',     4),
(5, 'Plots and Sites',        'PLOT',         'Residential sites, Commercial plots, Corner sites and Approved Gated layouts',        'bi-geo-alt',     5),
(6, 'Buildings and Projects', 'BUILDING',     'Apartment complexes, Gated townships, Commercial towers and Under-construction',      'bi-bricks',      6);

-- -----------------------------------------------------------------------------
-- 4. PROPERTY LOCATIONS (30 Indian Cities & Localities)
-- -----------------------------------------------------------------------------
INSERT INTO property_locations (location_id, city, state, locality, pincode, zone_type, latitude, longitude, avg_price_per_sqft) VALUES
(1,  'Mysuru',            'Karnataka',     'Vijayanagar 2nd Stage',      '570017', 'TIER_2',      12.33330000, 76.61220000,  5800.00),
(2,  'Mysuru',            'Karnataka',     'Jayalakshmipuram',           '570012', 'TIER_2',      12.32670000, 76.62580000,  7200.00),
(3,  'Mysuru',            'Karnataka',     'Gokulam 3rd Stage',          '570002', 'TIER_2',      12.33010000, 76.63350000,  6900.00),
(4,  'Mysuru',            'Karnataka',     'Hunsur Road',                '570018', 'TIER_2',      12.32040000, 76.57890000,  4200.00),
(5,  'Bengaluru',         'Karnataka',     'Indiranagar 100ft Road',     '560038', 'METRO',       12.97840000, 77.64080000, 16500.00),
(6,  'Bengaluru',         'Karnataka',     'Whitefield IT Corridor',     '560066', 'METRO',       12.96980000, 77.75000000,  9800.00),
(7,  'Bengaluru',         'Karnataka',     'Koramangala 4th Block',      '560034', 'METRO',       12.93520000, 77.62450000, 15200.00),
(8,  'Bengaluru',         'Karnataka',     'Devanahalli Airport Road',   '562110', 'METRO',       13.24730000, 77.71390000,  6400.00),
(9,  'Bengaluru',         'Karnataka',     'Kanakapura Road',            '560062', 'METRO',       12.86340000, 77.54120000,  7100.00),
(10, 'Mandya',            'Karnataka',     'Maddur Highway',             '571428', 'RURAL_AGRI',  12.58440000, 77.04390000,   680.00),
(11, 'Mandya',            'Karnataka',     'Srirangapatna River Belt',   '571438', 'RURAL_AGRI',  12.42370000, 76.68290000,   920.00),
(12, 'Hassan',            'Karnataka',     'Sakleshpur Estate Belt',     '573134', 'HILL_ESTATE', 12.94420000, 75.78660000,  1150.00),
(13, 'Hassan',            'Karnataka',     'BM Road Commercial Zone',    '573201', 'TIER_2',      13.00680000, 76.10040000,  4500.00),
(14, 'Kodagu',            'Karnataka',     'Madikeri Hilltop',           '571201', 'HILL_ESTATE', 12.42440000, 75.73820000,  1600.00),
(15, 'Mangaluru',         'Karnataka',     'Kadri Hills',                '575002', 'COASTAL',     12.88560000, 74.85590000,  5900.00),
(16, 'Mangaluru',         'Karnataka',     'Bejai Main Road',            '575004', 'COASTAL',     12.89020000, 74.84140000,  6300.00),
(17, 'Udupi',             'Karnataka',     'Manipal University Road',    '576104', 'COASTAL',     13.35250000, 74.79280000,  5100.00),
(18, 'Hubballi',          'Karnataka',     'Vidyanagar',                 '580021', 'TIER_2',      15.36920000, 75.12170000,  4100.00),
(19, 'Belagavi',          'Karnataka',     'Tilakwadi',                  '590006', 'TIER_2',      15.83900000, 74.50420000,  3900.00),
(20, 'Chennai',           'Tamil Nadu',    'OMR Sholinganallur',         '600119', 'METRO',       12.90100000, 80.22790000,  8900.00),
(21, 'Chennai',           'Tamil Nadu',    'ECR Mahabalipuram Stretch',  '603104', 'COASTAL',     12.78920000, 80.24910000,  7600.00),
(22, 'Coimbatore',        'Tamil Nadu',    'Pollachi Coconut Belt',      '642001', 'RURAL_AGRI',  10.65830000, 77.00850000,   750.00),
(23, 'Hyderabad',         'Telangana',     'Gachibowli Financial Dist',  '500032', 'METRO',       17.44010000, 78.34890000, 10800.00),
(24, 'Hyderabad',         'Telangana',     'Jubilee Hills',              '500033', 'METRO',       17.43260000, 78.40710000, 18500.00),
(25, 'Mumbai',            'Maharashtra',   'Bandra West',                '400050', 'METRO',       19.05960000, 72.82950000, 48000.00),
(26, 'Pune',              'Maharashtra',   'Hinjewadi Phase 1',          '411057', 'TIER_1',      18.59130000, 73.73890000,  8400.00),
(27, 'Pune',              'Maharashtra',   'Mulshi Lake Valley',         '412108', 'HILL_ESTATE', 18.50110000, 73.51290000,  2100.00),
(28, 'Kochi',             'Kerala',        'Marine Drive Ernakulam',     '682031', 'COASTAL',      9.98160000, 76.27550000,  8800.00),
(29, 'Gurugram',          'Haryana',       'Golf Course Extension',      '122018', 'METRO',       28.40890000, 77.06970000, 14500.00),
(30, 'Jaipur',            'Rajasthan',     'Vaishali Nagar',             '302021', 'TIER_1',      26.91240000, 75.74100000,  5600.00);

-- -----------------------------------------------------------------------------
-- 5. PROPERTIES (30 Rich Properties across ALL 6 Categories)
--    1..6   = RESIDENTIAL   | 7..11  = COMMERCIAL
--    12..16 = AGRICULTURAL  | 17..21 = FARMHOUSE
--    22..26 = PLOTS & SITES | 27..30 = BUILDINGS & PROJECTS
-- -----------------------------------------------------------------------------
INSERT INTO properties (property_id, owner_id, category_id, location_id, title, description, purpose, price, price_negotiable, property_area, area_unit, state, city, locality, address, pincode, primary_image_url, approval_status, listing_status, is_featured, views_count) VALUES
-- === CATEGORY 1: RESIDENTIAL PROPERTIES (1 to 6) ===
(1,  2,  1, 1,  'Luxury 4 BHK Independent Villa in Vijayanagar',
     'Exquisite brand-new 4 BHK duplex villa with Burma teakwood finish, landscaped private garden, Italian marble flooring, rainwater harvesting, and solar heating.',
     'BUY',  18500000.00, TRUE, 3200.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Vijayanagar 2nd Stage', 'No 45, 12th Cross, Vijayanagar 2nd Stage, Mysuru', '570017',
     'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 412),

(2,  3,  1, 5,  'Premium 3 BHK High-Rise Apartment in Indiranagar',
     'Spacious semi-furnished apartment with panoramic Bengaluru skyline views, modular kitchen, 2 covered car parks, swimming pool, and 24/7 clubhouse access.',
     'RENT', 65000.00, TRUE, 1950.00, 'Sq. Ft.', 'Karnataka', 'Bengaluru', 'Indiranagar 100ft Road', 'Flat 802, Prestige Ozone Towers, Indiranagar, Bengaluru', '560038',
     'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 389),

(3,  7,  1, 20, 'Sea-Breeze 3 BHK Flat on OMR Sholinganallur',
     'Bright east-facing 3 BHK flat inside a gated IT corridor community with walking track, kids play zone, piped gas, and 5 mins from Elcot SEZ.',
     'BUY',  9400000.00, TRUE, 1680.00, 'Sq. Ft.', 'Tamil Nadu', 'Chennai', 'OMR Sholinganallur', 'Tower C-404, Casagrand Crest, OMR, Chennai', '600119',
     'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 275),

(4,  8,  1, 25, 'Designer 2 BHK Sea-View Apartment in Bandra West',
     'Fully furnished luxury apartment on Pali Hill slope with oakwood interiors, concierge lobby, modular European kitchen, and rooftop gym.',
     'RENT', 145000.00, FALSE, 1250.00, 'Sq. Ft.', 'Maharashtra', 'Mumbai', 'Bandra West', '1102, Sea Crest Residency, Pali Hill, Bandra West, Mumbai', '400050',
     'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'RENTED', FALSE, 510),

(5,  15, 1, 17, 'Student & Executive PG Accommodation near Manipal',
     'Clean, well-maintained 18-room executive PG with high-speed Wi-Fi, biometric entry, dining hall, power backup, and laundry services.',
     'RENT', 12000.00, FALSE, 450.00, 'Sq. Ft.', 'Karnataka', 'Udupi', 'Manipal University Road', 'No 14, Academic Enclave, Eshwar Nagar, Manipal, Udupi', '576104',
     'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 198),

(6,  3,  1, 18, '2 BHK Budget Flat in Vidyanagar, Hubballi',
     'Newly constructed 2 BHK apartment near KLE Technological University (BVB Campus), lift access, covered two-wheeler & car parking.',
     'BUY',  4200000.00, TRUE, 1100.00, 'Sq. Ft.', 'Karnataka', 'Hubballi', 'Vidyanagar', 'Flat 204, Shanti Nilaya, Vidyanagar, Hubballi', '580021',
     'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1000&q=80', 'PENDING', 'AVAILABLE', FALSE, 64),

-- === CATEGORY 2: COMMERCIAL PROPERTIES (7 to 11) ===
(7,  2,  2, 2,  'Main Road Corner Commercial Showroom in Jayalakshmipuram',
     'Prime corner showroom with 60ft glass frontage on Temple Road, ideal for branded retail, banks, diagnostics, or cafes. 100% DG backup.',
     'RENT', 120000.00, TRUE, 2400.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Jayalakshmipuram', 'Shop 3, Heritage Commercial Hub, Temple Road, Mysuru', '570012',
     'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 230),

(8,  6,  2, 6,  'Grade-A Plug & Play IT Office in Whitefield',
     'Fully furnished 90-seater IT office space with 2 boardrooms, server room, cafeteria, biometric access, and 24/7 facility management.',
     'LEASE', 450000.00, TRUE, 7500.00, 'Sq. Ft.', 'Karnataka', 'Bengaluru', 'Whitefield IT Corridor', 'Level 4, Sigma Tech Park, Varthur Main Road, Whitefield', '560066',
     'https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 478),

(9,  11, 2, 23, 'Corporate Glass Tower Office Floor in Gachibowli',
     'LEED Gold certified warm-shell corporate office floor in Financial District with 15 reserved basement car parks and metro feeder connectivity.',
     'BUY',  115000000.00, TRUE, 10500.00, 'Sq. Ft.', 'Telangana', 'Hyderabad', 'Gachibowli Financial Dist', 'Floor 9, Deccan FinTech Plaza, Gachibowli, Hyderabad', '500032',
     'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 315),

(10, 8,  2, 26, 'Industrial Logistics Warehouse near Hinjewadi Highway',
     'High-ceiling (32ft) PEB warehouse with 4 loading docks, heavy-duty VDF flooring, container turning bay, and fire sprinkler network.',
     'LEASE', 280000.00, TRUE, 14000.00, 'Sq. Ft.', 'Maharashtra', 'Pune', 'Hinjewadi Phase 1', 'Plot 19, Chakan-Hinjewadi Logistics Park, Pune', '411057',
     'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 184),

(11, 15, 2, 16, 'High-Street Ground Floor Retail Shop in Bejai',
     'Busy commercial complex ground floor retail shop with mezzanine storage, rolling shutter, and heavy daily pedestrian traffic.',
     'BUY',  14800000.00, TRUE, 1150.00, 'Sq. Ft.', 'Karnataka', 'Mangaluru', 'Bejai Main Road', 'Unit G-07, KSRTC Junction Plaza, Bejai, Mangaluru', '575004',
     'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 142),

-- === CATEGORY 3: AGRICULTURAL LAND (12 to 16) ===
(12, 3,  3, 10, '5.5 Acres Fertile Sugarcane & Paddy Farmland in Mandya',
     'Highly fertile irrigated agricultural land with perennial Cauvery canal water supply, 2 running borewells, 3-phase electricity, and tar highway approach.',
     'BUY',  16500000.00, TRUE, 5.50, 'Acres', 'Karnataka', 'Mandya', 'Maddur Highway', 'Survey No 142/3, Maddur Taluk, Mandya District', '571428',
     'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 520),

(13, 17, 3, 14, '12 Acres Arabica Coffee & Pepper Plantation in Madikeri',
     'Yielding shade-grown Arabica coffee, black pepper, and silver oak estate with natural spring pond, drying yard, and labor quarters.',
     'BUY',  38400000.00, TRUE, 12.00, 'Acres', 'Karnataka', 'Kodagu', 'Madikeri Hilltop', 'Survey No 88/1A, Galibeedu Village, Madikeri, Kodagu', '571201',
     'https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 610),

(14, 13, 3, 22, '8 Acres Yielding Coconut & Areca Farm near Pollachi',
     '600+ mature hybrid coconut trees with drip irrigation installed across entire 8 acres, farmhouse shed, free agricultural EB connection, and clear patta.',
     'BUY',  26000000.00, TRUE, 8.00, 'Acres', 'Tamil Nadu', 'Coimbatore', 'Pollachi Coconut Belt', 'SF No 311/2, Anaimalai Road, Pollachi, Coimbatore', '642001',
     'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 334),

(15, 12, 3, 11, '4 Acres Organic Vegetable Farmland for Long-Term Lease',
     'River-belt alluvial soil ideal for polyhouse floriculture, banana, or organic vegetables. Fully chain-link fenced with 3HP submersible pump.',
     'LEASE', 240000.00, TRUE, 4.00, 'Acres', 'Karnataka', 'Mandya', 'Srirangapatna River Belt', 'Survey No 64/B, Ganjam Road, Srirangapatna, Mandya', '571438',
     'https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 205),

(16, 12, 3, 4,  '3.2 Acres Red Soil Mango Orchard on Hunsur Road',
     'Scenic red-soil agricultural plot with 180 Badami & Mallika mango trees, 20 minutes from Mysuru Ring Road, direct mud-to-tar road access.',
     'BUY',  11200000.00, TRUE, 3.20, 'Acres', 'Karnataka', 'Mysuru', 'Hunsur Road', 'Survey No 219/4, Bilikere Hobli, Hunsur Road, Mysuru', '570018',
     'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', 'PENDING', 'AVAILABLE', FALSE, 88),

-- === CATEGORY 4: FARMHOUSES (17 to 21) ===
(17, 2,  4, 12, 'Boutique Coffee Estate Farmhouse with Natural Stream',
     'Scenic weekend luxury farmhouse nestled inside a 3-acre silver oak and coffee plantation with private mountain stream, outdoor gazebo, and organic orchard.',
     'BUY',  22000000.00, TRUE, 3.00, 'Acres', 'Karnataka', 'Hassan', 'Sakleshpur Estate Belt', 'Valley View Estate, NH 75 Highway, Sakleshpur, Hassan', '573134',
     'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 540),

(18, 8,  4, 27, 'Lake-Facing 4 BHK Luxury Farmhouse in Mulshi Valley',
     'Architectural stone-and-glass weekend retreat overlooking Mulshi Lake with infinity swimming pool, barbecue deck, manicured lawn, and caretaker house.',
     'RENT', 95000.00, TRUE, 1.50, 'Acres', 'Maharashtra', 'Pune', 'Mulshi Lake Valley', 'Villa 7, Sahyadri Mist Farms, Mulshi Road, Pune', '412108',
     'https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 415),

(19, 6,  4, 9,  'Gated Organic 3 BHK Farmhouse on Kanakapura Road',
     'Peaceful 1-acre gated farmland villa 35 mins from South Bengaluru with lap pool, fruit orchard, solar inverter backup, and children treehouse.',
     'BUY',  19500000.00, TRUE, 1.00, 'Acres', 'Karnataka', 'Bengaluru', 'Kanakapura Road', 'Plot 12, Banyan Greens Retreat, Kanakapura Road, Bengaluru', '560062',
     'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 302),

(20, 7,  4, 21, 'Beachside 4 BHK Courtyard Farmhouse on ECR Chennai',
     'Private coastal farmhouse 300m from the Bay of Bengal with coconut grove, lagoon pool, open-air pavilion, ideal for family living or boutique stays.',
     'LEASE', 160000.00, TRUE, 0.75, 'Acres', 'Tamil Nadu', 'Chennai', 'ECR Mahabalipuram Stretch', 'Plot 28, Palm Cove Lane, East Coast Road, Chennai', '603104',
     'https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 367),

(21, 17, 4, 14, 'Coorg Heritage Timber 3 BHK Country Farmhouse',
     'Restored Kodava heritage style tiled-roof farmhouse surrounded by misty hills, cardamom gardens, and natural spring water.',
     'BUY',  17800000.00, TRUE, 2.25, 'Acres', 'Karnataka', 'Kodagu', 'Madikeri Hilltop', 'Misty Ridge Estate, Abbey Falls Road, Madikeri, Kodagu', '571201',
     'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 289),

-- === CATEGORY 5: PLOTS AND SITES (22 to 26) ===
(22, 3,  5, 1,  'Prime 50x80 North-East Facing MUDA Corner Plot',
     'MUDA-approved A-Khata residential corner site in a fully developed gated layout. 60ft asphalt road, underground drainage, Cauvery water & Bescom lines ready.',
     'BUY',  11500000.00, FALSE, 4000.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Vijayanagar 2nd Stage', 'Site No 102, H-Block, Vijayanagar 2nd Stage, Mysuru', '570017',
     'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'SOLD', FALSE, 441),

(23, 6,  5, 8,  '30x50 East-Facing Villa Plot near Kempegowda Airport',
     'BIAAPA & RERA approved luxury plotted development with clubhouse, avenue plantation, underground power cables, 10 mins from Airport Terminal 2.',
     'BUY',  9600000.00, TRUE, 1500.00, 'Sq. Ft.', 'Karnataka', 'Bengaluru', 'Devanahalli Airport Road', 'Site 44, AeroVista Enclave, Devanahalli, Bengaluru', '562110',
     'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 392),

(24, 11, 5, 24, '60x80 Prime Commercial Corner Site in Jubilee Hills',
     'Rare HMDA-approved commercial-zoned corner plot on 80ft road suitable for corporate headquarters, boutique hospital, or flagship retail complex.',
     'BUY',  88000000.00, TRUE, 4800.00, 'Sq. Ft.', 'Telangana', 'Hyderabad', 'Jubilee Hills', 'Plot 89, Road No 36, Jubilee Hills, Hyderabad', '500033',
     'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 298),

(25, 19, 5, 3,  '40x60 Ready-to-Build Residential Site in Gokulam',
     'Peaceful tree-lined street in Mysuru most sought-after Gokulam neighborhood, compound wall already built, clear title deed.',
     'BUY',  16200000.00, TRUE, 2400.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Gokulam 3rd Stage', 'Site 18, Contour Road, Gokulam 3rd Stage, Mysuru', '570002',
     'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 351),

(26, 13, 5, 30, 'JDA Approved 30x60 Residential Plot in Vaishali Nagar',
     'East-facing park-front residential site in prime Jaipur colony with 40ft concrete road, water pipeline, and immediate registry.',
     'BUY',  10200000.00, TRUE, 1800.00, 'Sq. Ft.', 'Rajasthan', 'Jaipur', 'Vaishali Nagar', 'Plot B-72, Amrapali Marg, Vaishali Nagar, Jaipur', '302021',
     'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', 'PENDING', 'AVAILABLE', FALSE, 77),

-- === CATEGORY 6: BUILDINGS AND PROJECTS (27 to 30) ===
(27, 6,  6, 15, 'Coastal Palms 24-Unit Residential Apartment Building',
     'Brand-new G+4 RERA-approved residential apartment complex featuring modern 2 & 3 BHK flats, ocean breeze, automatic Schindler elevators, and solar rooftop.',
     'BUY',  145000000.00, TRUE, 32000.00, 'Sq. Ft.', 'Karnataka', 'Mangaluru', 'Kadri Hills', 'Coastal Palms, Airport Road, Kadri Hills, Mangaluru', '575002',
     'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 465),

(28, 11, 6, 23, 'Deccan Skyview 120-Unit Luxury Township Project',
     'Under-construction 18-floor twin tower residential project with skywalk, Olympic pool, EV charging pods, and co-working lounge.',
     'BUY',  11200000.00, FALSE, 1850.00, 'Sq. Ft.', 'Telangana', 'Hyderabad', 'Gachibowli Financial Dist', 'Deccan Skyview, Nanakramguda Circle, Gachibowli, Hyderabad', '500032',
     'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 590),

(29, 8,  6, 29, 'Emerald Boulevard Commercial Complex in Gurugram',
     'Completed Grade-A mixed-use commercial retail & office tower with double-height atrium, 3-level basement parking, and high rental yield.',
     'BUY',  285000000.00, TRUE, 45000.00, 'Sq. Ft.', 'Haryana', 'Gurugram', 'Golf Course Extension', 'Tower 2, Emerald Boulevard, Sector 65, Gurugram', '122018',
     'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', FALSE, 312),

(30, 15, 6, 28, 'Marina Waterfront 40-Unit Boutique Condominiums',
     'Waterfront G+10 residential building overlooking Kochi backwaters with private boat jetty access, infinity deck, and smart home automation.',
     'BUY',  16500000.00, TRUE, 2250.00, 'Sq. Ft.', 'Kerala', 'Kochi', 'Marine Drive Ernakulam', 'Marina Waterfront Towers, Shanmugham Road, Kochi', '682031',
     'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', 'APPROVED', 'AVAILABLE', TRUE, 405);

-- -----------------------------------------------------------------------------
-- 6. RESIDENTIAL DETAILS (IDs 1..6)
-- -----------------------------------------------------------------------------
INSERT INTO residential_details (property_id, residential_type, bedrooms, bathrooms, balconies, built_up_area, carpet_area, parking_slots, furnishing_status, floor_number, total_floors, facing_direction, property_age_years, maintenance_monthly) VALUES
(1, 'VILLA',      4, 4, 3, 3200.00, 2750.00, 2, 'SEMI_FURNISHED',  0,  2, 'NORTH_EAST', 1, 2500.00),
(2, 'APARTMENT',  3, 3, 2, 1950.00, 1680.00, 2, 'SEMI_FURNISHED',  8, 14, 'EAST',       3, 5500.00),
(3, 'FLAT',       3, 3, 2, 1680.00, 1420.00, 1, 'UNFURNISHED',     4, 12, 'EAST',       1, 4200.00),
(4, 'APARTMENT',  2, 2, 1, 1250.00, 1020.00, 1, 'FULLY_FURNISHED', 11, 16, 'WEST',      4, 9000.00),
(5, 'PG',         1, 1, 1,  450.00,  380.00, 1, 'FULLY_FURNISHED', 2,  3, 'NORTH',      2, 1000.00),
(6, 'FLAT',       2, 2, 1, 1100.00,  950.00, 1, 'UNFURNISHED',     2,  4, 'EAST',       0, 1800.00);

-- -----------------------------------------------------------------------------
-- 7. COMMERCIAL DETAILS (IDs 7..11)
-- -----------------------------------------------------------------------------
INSERT INTO commercial_details (property_id, commercial_type, carpet_area, super_built_up_area, number_of_floors, parking_spaces, seating_capacity, washrooms, building_type, commercial_usage, power_backup, central_ac, fire_noc_approved) VALUES
(7,  'SHOWROOM',     2100.00,  2400.00, 1,  6,  20, 2, 'Stand-alone Commercial Building', 'Retail Brand / Bank / Clinic',      TRUE,  TRUE,  TRUE),
(8,  'OFFICE_SPACE', 6800.00,  7500.00, 1, 15,  90, 4, 'Grade-A IT Tech Park',            'IT / Software / Corporate Office',  TRUE,  TRUE,  TRUE),
(9,  'OFFICE_SPACE', 9200.00, 10500.00, 1, 20, 140, 6, 'LEED Gold Corporate Tower',       'FinTech / MNC Regional HQ',         TRUE,  TRUE,  TRUE),
(10, 'WAREHOUSE',   13200.00, 14000.00, 1, 12,  10, 3, 'PEB Industrial Logistics Hub',    'E-commerce Fulfillment / Storage',  TRUE,  FALSE, TRUE),
(11, 'SHOP',         1020.00,  1150.00, 1,  3,   8, 1, 'High-Street Retail Arcade',       'Pharmacy / Boutique / Electronics', TRUE,  FALSE, TRUE);

-- -----------------------------------------------------------------------------
-- 8. AGRICULTURAL LAND DETAILS (IDs 12..16)
-- -----------------------------------------------------------------------------
INSERT INTO land_details (property_id, land_type, total_land_area, land_unit, soil_type, crop_suitability, water_availability, has_borewell, borewell_count, road_connectivity, has_electricity, fencing_status, land_document_type, survey_number) VALUES
(12, 'FARMLAND',          5.50, 'ACRES', 'Red Loamy & Black Clay', 'Sugarcane, Paddy, Banana',          'Cauvery Canal + 2 Borewells',    TRUE, 2, '30ft Tar Highway Frontage',  TRUE, 'FULLY_FENCED', 'Clear Title Deed, RTC & EC',     'Sy. No 142/3'),
(13, 'PLANTATION',       12.00, 'ACRES', 'Rich Forest Loam',       'Arabica Coffee, Pepper, Cardamom',  'Natural Perennial Spring Pond',  TRUE, 1, '20ft Concrete Estate Road',  TRUE, 'FULLY_FENCED', 'Jamma / Clear Sale Deed & RTC',  'Sy. No 88/1A'),
(14, 'PLANTATION',        8.00, 'ACRES', 'Fertile Red Soil',       'Hybrid Coconut, Arecanut, Nutmeg',  'PAP Canal + 3 Open/Borewells',   TRUE, 3, '24ft Panchayat Tar Road',    TRUE, 'FULLY_FENCED', 'Clear Patta, Chitta & Adangal',  'SF No 311/2'),
(15, 'LEASE_LAND',        4.00, 'ACRES', 'River Alluvial Soil',    'Organic Vegetables, Polyhouse',     'River Lift Irrigation + Bore',   TRUE, 1, '20ft Gravel-to-Tar Approach',TRUE, 'FULLY_FENCED', 'Registered Lease Agreement',     'Sy. No 64/B'),
(16, 'AGRICULTURAL_PLOT', 3.20, 'ACRES', 'Red Gravelly Loam',      'Mango Orchard, Teak, Sandalwood',   'High Yield 4-Inch Borewell',     TRUE, 1, '25ft Village Approach Road', TRUE, 'PARTIAL',      'Clear Title Deed & Pahani',      'Sy. No 219/4');

-- -----------------------------------------------------------------------------
-- 9. FARMHOUSE DETAILS (IDs 17..21)
-- -----------------------------------------------------------------------------
INSERT INTO farmhouse_details (property_id, land_area, built_up_area, bedrooms, bathrooms, has_garden, has_swimming_pool, has_caretaker_quarters, parking_capacity, water_facilities, plantation_details) VALUES
(17, 3.00, 2800.00, 3, 3, TRUE, TRUE,  TRUE, 5, 'Perennial mountain spring + Deep borewell', 'Coffee, Silver Oak, Pepper & Organic Fruit Orchard'),
(18, 1.50, 3600.00, 4, 4, TRUE, TRUE,  TRUE, 6, '24/7 Borewell + Rainwater Filtration Tank', 'Mango, Chikoo, Bamboo Grove & Manicured Lawn'),
(19, 1.00, 2400.00, 3, 3, TRUE, TRUE,  TRUE, 4, 'Gated Community Overhead Tank + Borewell',  'Guava, Coconut, Jackfruit & Organic Kitchen Garden'),
(20, 0.75, 3400.00, 4, 4, TRUE, TRUE,  TRUE, 5, 'Desalinated Sweet Water Borewell',          '50 Mature Coconut Palms & Tropical Frangipani'),
(21, 2.25, 2200.00, 3, 3, TRUE, FALSE, TRUE, 4, 'Natural Hill Spring + Overhead Tank',       'Cardamom, Coorg Oranges & Coffee Plantation');

-- -----------------------------------------------------------------------------
-- 10. PLOT & SITE DETAILS (IDs 22..26)
-- -----------------------------------------------------------------------------
INSERT INTO plot_details (property_id, plot_type, plot_dimensions, total_area, facing_direction, road_width_feet, boundary_wall_built, is_approved_layout, approval_authority, khata_type) VALUES
(22, 'CORNER_SITE',      '50 x 80', 4000.00, 'NORTH_EAST', 60, TRUE,  TRUE, 'MUDA (Mysuru Urban Development Authority)',    'A-Khata / E-Swathu'),
(23, 'GATED_COMMUNITY',  '30 x 50', 1500.00, 'EAST',       40, TRUE,  TRUE, 'BIAAPA & Karnataka RERA Approved',             'A-Khata Clear Title'),
(24, 'COMMERCIAL_PLOT',  '60 x 80', 4800.00, 'NORTH',      80, TRUE,  TRUE, 'HMDA (Hyderabad Metropolitan Dev Authority)',  'Commercial LRS Cleared'),
(25, 'RESIDENTIAL_PLOT', '40 x 60', 2400.00, 'EAST',       40, TRUE,  TRUE, 'MUDA Approved Residential Layout',             'A-Khata Clear Title'),
(26, 'RESIDENTIAL_PLOT', '30 x 60', 1800.00, 'EAST',       40, FALSE, TRUE, 'JDA (Jaipur Development Authority)',           'JDA Patta Approved');

-- -----------------------------------------------------------------------------
-- 11. BUILDING DETAILS (IDs 27..30)
-- -----------------------------------------------------------------------------
INSERT INTO building_details (property_id, building_type, number_of_floors, total_units, available_units, construction_status, builder_name, project_completion_date, rera_number, occupancy_certificate) VALUES
(27, 'APARTMENT_BUILDING',  5,  24,  6, 'READY_TO_MOVE',      'Coastal Horizon Developers Ltd.', '2026-03-31', 'PRM/KA/RERA/1257/334/PR/240118/006501', TRUE),
(28, 'RESIDENTIAL_PROJECT', 18, 120, 42, 'UNDER_CONSTRUCTION', 'Deccan Horizon Builders Pvt Ltd', '2028-06-30', 'P02400004512',                         FALSE),
(29, 'COMMERCIAL_COMPLEX',  10,  45, 11, 'READY_TO_MOVE',      'Deshmukh Infra & Land',           '2025-12-15', 'RC/REP/HARERA/GGM/542/274/2023/11',    TRUE),
(30, 'RESIDENTIAL_PROJECT', 10,  40, 14, 'READY_TO_MOVE',      'Hegde Coastal Realty',            '2026-08-20', 'K-RERA/PRJ/ERN/091/2024',              TRUE);

-- -----------------------------------------------------------------------------
-- 12. BUILDER PROJECTS & PROJECT UNITS
-- -----------------------------------------------------------------------------
INSERT INTO projects (project_id, builder_id, location_id, project_name, project_type, city, locality, total_land_acres, rera_approved, rera_number, total_towers, total_units, starting_price, launch_date, possession_date, status) VALUES
(1, 6,  15, 'Coastal Palms Residency',     'Residential Apartments', 'Mangaluru', 'Kadri Hills',               1.80, TRUE, 'PRM/KA/RERA/1257/334/PR/240118/006501', 1,  24,  6200000.00, '2024-02-01', '2026-03-31', 'COMPLETED'),
(2, 11, 23, 'Deccan Skyview Township',     'High-Rise Township',     'Hyderabad', 'Gachibowli Financial Dist', 6.50, TRUE, 'P02400004512',                          2, 120, 11200000.00, '2025-01-15', '2028-06-30', 'ACTIVE'),
(3, 8,  29, 'Emerald Boulevard Plaza',     'Commercial Complex',     'Gurugram',  'Golf Course Extension',     3.20, TRUE, 'RC/REP/HARERA/GGM/542/274/2023/11',     2,  45, 19500000.00, '2023-05-10', '2025-12-15', 'COMPLETED'),
(4, 15, 28, 'Marina Waterfront Condos',    'Luxury Condominiums',    'Kochi',     'Marine Drive Ernakulam',    2.10, TRUE, 'K-RERA/PRJ/ERN/091/2024',               1,  40, 14800000.00, '2024-04-01', '2026-08-20', 'COMPLETED'),
(5, 6,  8,  'AeroVista Villa Plots',       'Plotted Development',    'Bengaluru', 'Devanahalli Airport Road', 14.00, TRUE, 'PRM/KA/RERA/1250/303/PR/250110/007812', 1,  96,  8900000.00, '2025-02-10', '2026-12-31', 'ACTIVE'),
(6, 2,  1,  'Heritage Greens Villas',      'Gated Villa Enclave',    'Mysuru',    'Vijayanagar 2nd Stage',     4.50, TRUE, 'PRM/KA/RERA/1268/378/PR/250615/008104', 1,  18, 17500000.00, '2025-07-01', '2027-09-30', 'ACTIVE');

INSERT INTO project_units (project_id, tower_name, unit_number, unit_config, carpet_area, floor_no, unit_price, unit_status) VALUES
(1, 'Palm Tower', '101', '2 BHK',        1120.00, 1,  6200000.00, 'SOLD'),
(1, 'Palm Tower', '302', '3 BHK',        1580.00, 3,  8400000.00, 'AVAILABLE'),
(1, 'Palm Tower', '501', '3 BHK Duplex', 1940.00, 5, 11200000.00, 'AVAILABLE'),
(2, 'Sky Tower A','804', '3 BHK',        1650.00, 8, 11200000.00, 'AVAILABLE'),
(2, 'Sky Tower A','1202','3 BHK Large',  1920.00, 12,13500000.00, 'BOOKED'),
(2, 'Sky Tower B','1701','4 BHK SkyVilla',2850.00,17,22500000.00, 'AVAILABLE'),
(3, 'Block 1',    'G-12','Retail Shop',  1100.00, 0, 19500000.00, 'SOLD'),
(3, 'Block 2',    '405', 'Office Suite', 2400.00, 4, 34000000.00, 'AVAILABLE'),
(4, 'Marina One', '602', '3 BHK SeaView',1980.00, 6, 16500000.00, 'AVAILABLE'),
(4, 'Marina One', '901', '4 BHK Penthouse',3100.00,9, 29000000.00, 'BOOKED'),
(5, 'Phase 1',    'P-44','30x50 Plot',   1500.00, 0,  9600000.00, 'AVAILABLE'),
(6, 'Enclave A',  'V-09','4 BHK Villa',  2750.00, 0, 18500000.00, 'AVAILABLE');

-- -----------------------------------------------------------------------------
-- 13. PROPERTY IMAGES (Gallery Photos across Properties)
-- -----------------------------------------------------------------------------
INSERT INTO property_images (property_id, image_url, is_primary, caption, sort_order) VALUES
(1,  'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80', TRUE,  'Front architectural elevation', 1),
(1,  'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=80', FALSE, 'Double-height living room', 2),
(1,  'https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1000&q=80', FALSE, 'Modular teak kitchen', 3),
(2,  'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', TRUE,  'High-rise tower facade', 1),
(2,  'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=80', FALSE, 'Skyline balcony deck', 2),
(3,  'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=80', TRUE,  'Community exterior view', 1),
(3,  'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1000&q=80', FALSE, 'Bright master bedroom', 2),
(4,  'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1000&q=80', TRUE,  'Bandra luxury living room', 1),
(5,  'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1000&q=80', TRUE,  'Furnished executive room', 1),
(6,  'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1000&q=80', TRUE,  '2 BHK flat interior', 1),
(7,  'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1000&q=80', TRUE,  'Main street showroom frontage', 1),
(8,  'https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1000&q=80', TRUE,  'Plug and play workstations', 1),
(8,  'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=1000&q=80', FALSE, 'Glass conference boardroom', 2),
(9,  'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', TRUE,  'Financial district glass tower', 1),
(10, 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1000&q=80', TRUE,  'PEB industrial storage bays', 1),
(11, 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1000&q=80', TRUE,  'Ground floor retail unit', 1),
(12, 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', TRUE,  'Irrigated green farmland', 1),
(13, 'https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=1000&q=80', TRUE,  'Coorg misty coffee plantation', 1),
(14, 'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1000&q=80', TRUE,  'Pollachi coconut grove', 1),
(15, 'https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=1000&q=80', TRUE,  'Alluvial farmland for lease', 1),
(16, 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', TRUE,  'Red soil mango orchard', 1),
(17, 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1000&q=80', TRUE,  'Sakleshpur estate farmhouse', 1),
(18, 'https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=1000&q=80', TRUE,  'Mulshi lake view villa & pool', 1),
(19, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=80', TRUE,  'Kanakapura gated farmhouse', 1),
(20, 'https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1000&q=80', TRUE,  'ECR coastal pool courtyard', 1),
(21, 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1000&q=80', TRUE,  'Coorg timber estate home', 1),
(22, 'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', TRUE,  'MUDA corner layout view', 1),
(23, 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', TRUE,  'Devanahalli gated plots', 1),
(24, 'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', TRUE,  'Jubilee Hills commercial plot', 1),
(25, 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', TRUE,  'Gokulam tree-lined site', 1),
(26, 'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', TRUE,  'Vaishali Nagar JDA site', 1),
(27, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', TRUE,  'Coastal Palms apartment block', 1),
(28, 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', TRUE,  'Deccan Skyview twin towers', 1),
(29, 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1000&q=80', TRUE,  'Emerald Boulevard glass atrium', 1),
(30, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', TRUE,  'Marina Waterfront Kochi', 1);

-- -----------------------------------------------------------------------------
-- 14. AMENITIES MASTER & PROPERTY AMENITIES
-- -----------------------------------------------------------------------------
INSERT INTO amenities_master (amenity_name, amenity_group, icon_class) VALUES
('24/7 CCTV Security',     'SECURITY',    'bi-shield-check'),
('Biometric Entry',        'SECURITY',    'bi-fingerprint'),
('Gated Community',        'SECURITY',    'bi-lock'),
('Swimming Pool',          'LEISURE',     'bi-water'),
('Clubhouse',              'LEISURE',     'bi-people'),
('Gymnasium',              'LEISURE',     'bi-heart-pulse'),
('Private Garden',         'LEISURE',     'bi-flower2'),
('Rooftop Lounge',         'LEISURE',     'bi-stars'),
('100% Power Backup',      'UTILITY',     'bi-lightning-charge'),
('Covered Car Parking',    'UTILITY',     'bi-car-front'),
('Automatic Elevators',    'UTILITY',     'bi-arrow-down-up'),
('Modular Kitchen',        'UTILITY',     'bi-cup-hot'),
('High-Speed Fiber Wi-Fi', 'COMMERCIAL',  'bi-wifi'),
('Central Air Conditioning','COMMERCIAL', 'bi-snow'),
('Fire Safety NOC',        'COMMERCIAL',  'bi-fire'),
('Loading Docks',          'COMMERCIAL',  'bi-truck'),
('Perennial Canal Water',  'FARM_LAND',   'bi-droplet'),
('3-Phase Electricity',    'FARM_LAND',   'bi-plug'),
('Drip Irrigation',        'FARM_LAND',   'bi-moisture'),
('Chain-Link Fencing',     'FARM_LAND',   'bi-border-all'),
('Solar Water Heater',     'ENVIRONMENT', 'bi-sun'),
('Rainwater Harvesting',   'ENVIRONMENT', 'bi-cloud-rain'),
('EV Charging Station',    'ENVIRONMENT', 'bi-ev-station'),
('Underground Drainage',   'UTILITY',     'bi-grid');

INSERT INTO property_amenities (property_id, amenity_name) VALUES
(1, 'Private Garden'), (1, 'Modular Kitchen'), (1, 'Covered Car Parking'), (1, 'Solar Water Heater'), (1, '24/7 CCTV Security'),
(2, 'Swimming Pool'), (2, 'Gymnasium'), (2, 'Clubhouse'), (2, '100% Power Backup'), (2, 'Automatic Elevators'),
(3, 'Gated Community'), (3, 'Clubhouse'), (3, 'Covered Car Parking'), (3, 'Rainwater Harvesting'),
(4, '24/7 CCTV Security'), (4, 'Modular Kitchen'), (4, 'Gymnasium'), (4, 'Automatic Elevators'),
(5, 'High-Speed Fiber Wi-Fi'), (5, 'Biometric Entry'), (5, '100% Power Backup'),
(6, 'Covered Car Parking'), (6, 'Automatic Elevators'), (6, '24/7 CCTV Security'),
(7, '100% Power Backup'), (7, 'Fire Safety NOC'), (7, 'Covered Car Parking'),
(8, 'Central Air Conditioning'), (8, 'High-Speed Fiber Wi-Fi'), (8, 'Biometric Entry'), (8, '100% Power Backup'),
(9, 'Central Air Conditioning'), (9, 'Fire Safety NOC'), (9, 'EV Charging Station'), (9, 'Automatic Elevators'),
(10,'Loading Docks'), (10,'Fire Safety NOC'), (10,'24/7 CCTV Security'), (10,'3-Phase Electricity'),
(11,'100% Power Backup'), (11,'24/7 CCTV Security'),
(12,'Perennial Canal Water'), (12,'3-Phase Electricity'), (12,'Chain-Link Fencing'),
(13,'Perennial Canal Water'), (13,'3-Phase Electricity'), (13,'Private Garden'),
(14,'Drip Irrigation'), (14,'3-Phase Electricity'), (14,'Chain-Link Fencing'),
(15,'Perennial Canal Water'), (15,'Chain-Link Fencing'), (15,'3-Phase Electricity'),
(16,'Drip Irrigation'), (16,'3-Phase Electricity'),
(17,'Swimming Pool'), (17,'Private Garden'), (17,'Solar Water Heater'), (17,'24/7 CCTV Security'),
(18,'Swimming Pool'), (18,'Private Garden'), (18,'100% Power Backup'),
(19,'Swimming Pool'), (19,'Gated Community'), (19,'Solar Water Heater'),
(20,'Swimming Pool'), (20,'Private Garden'), (20,'100% Power Backup'),
(21,'Private Garden'), (21,'Solar Water Heater'), (21,'Rainwater Harvesting'),
(22,'Gated Community'), (22,'Underground Drainage'), (22,'24/7 CCTV Security'),
(23,'Gated Community'), (23,'Clubhouse'), (23,'Underground Drainage'),
(24,'Underground Drainage'), (24,'3-Phase Electricity'),
(25,'Underground Drainage'), (25,'24/7 CCTV Security'),
(26,'Gated Community'), (26,'Underground Drainage'),
(27,'Automatic Elevators'), (27,'Rooftop Lounge'), (27,'Rainwater Harvesting'), (27,'Covered Car Parking'),
(28,'Swimming Pool'), (28,'EV Charging Station'), (28,'Clubhouse'), (28,'Gymnasium'),
(29,'Central Air Conditioning'), (29,'Fire Safety NOC'), (29,'EV Charging Station'),
(30,'Swimming Pool'), (30,'Rooftop Lounge'), (30,'Biometric Entry'), (30,'Automatic Elevators');

-- -----------------------------------------------------------------------------
-- 15. PROPERTY REVIEWS (12 Verified & Buyer Reviews)
-- -----------------------------------------------------------------------------
INSERT INTO property_reviews (property_id, user_id, rating, review_title, comment, is_verified_visit) VALUES
(1,  4,  5, 'Immaculate Construction Quality',      'Visited the villa with my family. Teakwood doors and natural sunlight are extraordinary.', TRUE),
(2,  5,  5, 'Best Location in Indiranagar',         'Metro station is 600 meters away. Society maintenance and security are top notch.', TRUE),
(3,  9,  4, 'Great Value for IT Professionals',     'Very close to Sholinganallur IT parks. Traffic during peak hours is manageable.', TRUE),
(8,  14, 5, 'Turnkey Office Ready on Day 1',        'Plug-and-play setup with clean server room and ample cafeteria space.', TRUE),
(12, 18, 5, 'Abundant Water & Clear RTC',           'Inspected both borewells and canal channel. Very genuine seller with clean documents.', TRUE),
(13, 4,  5, 'Dream Coffee Estate in Coorg',         'Breathtaking morning mist and healthy pepper vines. Worth the investment.', FALSE),
(17, 9,  5, 'Magical Weekend Sanctuary',            'The natural stream running through the edge of the estate is unforgettable.', TRUE),
(18, 10, 4, 'Stunning Lake Views',                  'Pool and lawn are spotless. Approach road last 500m is narrow but manageable.', TRUE),
(22, 4,  5, 'MUDA Corner Site Verified',            'Completed purchase smoothly. Prime corner location in Vijayanagar 2nd Stage.', TRUE),
(23, 14, 4, 'High Growth Corridor near Airport',    'Great layout infrastructure with underground wiring and clean wide roads.', FALSE),
(27, 18, 5, 'Quality Coastal Construction',         'Cross ventilation is superb and builder handed over OC on schedule.', TRUE),
(30, 9,  5, 'Unmatched Backwater Views',            'Every balcony looks directly over the Kochi waterfront. Pure luxury.', FALSE);

-- -----------------------------------------------------------------------------
-- 16. PROPERTY FAVOURITES
-- -----------------------------------------------------------------------------
INSERT INTO property_favourites (user_id, property_id, notes) VALUES
(4,  1,  'Top choice for family home in Mysuru'),
(4,  17, 'Weekend getaway estate option'),
(4,  25, 'Alternative plot if we decide to build'),
(5,  2,  'Currently renting here'),
(5,  18, 'Great for annual team retreat'),
(9,  3,  'Shortlisted for Chennai move'),
(9,  30, 'Dream retirement flat in Kochi'),
(10, 4,  'Checking similar Mumbai options'),
(14, 8,  'Shortlisted for startup expansion'),
(14, 23, 'Investment plot near Bengaluru airport'),
(18, 12, 'Agri investment shortlist'),
(18, 28, 'Comparing 3 BHK price with Gachibowli peers');

-- -----------------------------------------------------------------------------
-- 17. PROPERTY ENQUIRIES
-- -----------------------------------------------------------------------------
INSERT INTO property_enquiries (property_id, user_id, sender_name, sender_email, sender_phone, message, status) VALUES
(1,  4,  'Priya Sharma',    'priya.buyer@example.com',  '+91 9980123456', 'Is home loan approved by SBI for this villa? Can we negotiate slightly?', 'CONTACTED'),
(8,  14, 'Arjun Malhotra',  'arjun.buyer@example.com',  '+91 9811009988', 'We need a 3-year lock-in corporate lease for 80 engineers. Please share CAM charges.', 'FOLLOW_UP'),
(12, 18, 'Farhan Siddiqui', 'farhan.buyer@example.com', '+91 9866123987', 'Can you share the latest RTC and 15-year Encumbrance Certificate copy?', 'NEW'),
(17, 9,  'Sneha Nair',      'sneha.buyer@example.com',  '+91 9447112244', 'Does the Sakleshpur farmhouse have commercial homestay license potential?', 'CONTACTED'),
(23, 14, 'Arjun Malhotra',  'arjun.buyer@example.com',  '+91 9811009988', 'Please send the BIAAPA layout sanction plan and plot availability map.', 'NEW'),
(28, 18, 'Farhan Siddiqui', 'farhan.buyer@example.com', '+91 9866123987', 'Looking for an east-facing 3 BHK above the 10th floor in Sky Tower A.', 'FOLLOW_UP'),
(20, 16, 'Siddharth Joshi', 'siddharth.tenant@example.com','+91 9765443322','Interested in 2-year lease for boutique wellness center on ECR.', 'NEW'),
(15, 5,  'Kiran Kumar',     'kiran.tenant@example.com', '+91 9740112233', 'Can we install polyhouse structures on the 4-acre lease land?', 'CLOSED');

-- -----------------------------------------------------------------------------
-- 18. OWNER VERIFICATIONS (KYC & Title Documents)
-- -----------------------------------------------------------------------------
INSERT INTO owner_verifications (verification_id, user_id, identity_proof_type, identity_proof_number, ownership_document_type, document_path, supporting_doc_path, property_address, verification_status, admin_notes, reviewed_by, submitted_at, verified_at) VALUES
(1, 2,  'AADHAAR',   'XXXX-XXXX-8921', 'Registered Sale Deed + Khata', '/vault/kyc/aadhaar_rajesh_2.pdf',   '/vault/deeds/deed_rajesh_2.pdf',  'Vijayanagar 2nd Stage, Mysuru',          'APPROVED', 'Verified original sale deed and BESCOM/CESC bill.', 1, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 28 DAY),
(2, 3,  'PAN',       'ABCDE1234F',     'Sale Deed + Bhoomi RTC',       '/vault/kyc/pan_anand_3.pdf',        '/vault/deeds/rtc_anand_3.pdf',    'Indiranagar 100ft Road, Bengaluru',      'APPROVED', 'Title clear on Karnataka Bhoomi portal.',           1, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 23 DAY),
(3, 6,  'RERA_CERT', 'PRM/KA/RERA/2311','RERA Agency Certificate',     '/vault/kyc/rera_suresh_6.pdf',      '/vault/deeds/gst_suresh_6.pdf',   'Whitefield IT Corridor, Bengaluru',      'APPROVED', 'Active Karnataka RERA broker verified.',            1, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 20 DAY),
(4, 7,  'AADHAAR',   'XXXX-XXXX-4410', 'Registered Patta & Sale Deed', '/vault/kyc/aadhaar_meenakshi_7.pdf','/vault/deeds/patta_7.pdf',        'OMR Sholinganallur, Chennai',            'APPROVED', 'Tamil Nadu e-Services Patta matched.',              20,NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 16 DAY),
(5, 8,  'PASSPORT',  'Z45891XX',       '7/12 Extract & Sale Deed',     '/vault/kyc/passport_vikram_8.pdf',  '/vault/deeds/satbara_8.pdf',      'Hinjewadi Phase 1, Pune',                'APPROVED', 'Mahabhulekh 7/12 extract verified.',                20,NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 12 DAY),
(6, 13, 'PAN',       'GHIPS8912R',     'Chitta, Adangal & Sale Deed',  '/vault/kyc/pan_kavitha_13.pdf',     '/vault/deeds/chitta_13.pdf',      'Pollachi Coconut Belt, Coimbatore',      'APPROVED', 'Clear ownership confirmed.',                        1, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY),
(7, 12, 'AADHAAR',   'XXXX-XXXX-7732', 'RTC Pahani Document',          '/vault/kyc/aadhaar_basavaraj_12.pdf','/vault/deeds/rtc_12.pdf',        'Hunsur Road, Mysuru',                    'PENDING',  'Awaiting mutation register verification.',          NULL, NOW() - INTERVAL 2 DAY, NULL),
(8, 19, 'AADHAAR',   'XXXX-XXXX-3109', 'MUDA Allotment & Khata',       '/vault/kyc/aadhaar_lakshmi_19.pdf', '/vault/deeds/muda_19.pdf',        'Gokulam 3rd Stage, Mysuru',              'PENDING',  'Under queue for admin review.',                     NULL, NOW() - INTERVAL 1 DAY, NULL);

-- -----------------------------------------------------------------------------
-- 19. PROPERTY VISITS / APPOINTMENTS
-- -----------------------------------------------------------------------------
INSERT INTO property_visits (visit_id, property_id, user_id, visit_date, visit_time, contact_number, notes, appointment_status) VALUES
(1,  1,  4,  CURDATE() + INTERVAL 2 DAY, '10:30:00', '+91 9980123456', 'Visiting with parents to inspect rooms and Vastu.', 'APPROVED'),
(2,  2,  5,  CURDATE() - INTERVAL 45 DAY,'15:00:00', '+91 9740112233', 'Finalized rental move-in inspection.',              'COMPLETED'),
(3,  22, 4,  CURDATE() - INTERVAL 20 DAY,'11:00:00', '+91 9980123456', 'Checked MUDA boundary stones and road width.',      'COMPLETED'),
(4,  8,  14, CURDATE() + INTERVAL 1 DAY, '14:00:00', '+91 9811009988', 'IT infrastructure inspection with CTO.',            'APPROVED'),
(5,  12, 18, CURDATE() + INTERVAL 3 DAY, '09:30:00', '+91 9866123987', 'Morning farm visit to inspect borewells.',          'PENDING'),
(6,  17, 9,  CURDATE() + INTERVAL 4 DAY, '11:30:00', '+91 9447112244', 'Weekend trip to Sakleshpur estate.',                'APPROVED'),
(7,  23, 14, CURDATE() + INTERVAL 5 DAY, '16:00:00', '+91 9811009988', 'Checking airport proximity and clubhouse.',         'PENDING'),
(8,  28, 18, CURDATE() + INTERVAL 2 DAY, '12:00:00', '+91 9866123987', 'Sample flat tour at Gachibowli site office.',       'APPROVED'),
(9,  3,  9,  CURDATE() - INTERVAL 5 DAY, '17:00:00', '+91 9447112244', 'Visited apartment and reviewed HOA documents.',     'COMPLETED'),
(10, 7,  10, CURDATE() - INTERVAL 3 DAY, '13:00:00', '+91 9898223344', 'Owner unavailable at requested slot.',              'REJECTED');

-- -----------------------------------------------------------------------------
-- 20. RENTAL AGREEMENTS & MONTHLY RENT PAYMENTS
-- -----------------------------------------------------------------------------
INSERT INTO rental_agreements (agreement_id, property_id, owner_id, tenant_id, monthly_rent, security_deposit, maintenance_charges, annual_escalation_pct, agreement_start_date, agreement_end_date, agreement_status, terms_and_conditions) VALUES
(1, 2,  3,  5,  65000.00, 300000.00, 5500.00, 5.00, '2026-04-01', '2027-03-31', 'ACTIVE',  'Rent payable before 5th of each month. 11-month lock-in. 2 months notice period.'),
(2, 4,  8,  10,145000.00, 600000.00, 9000.00, 7.00, '2026-06-01', '2027-05-31', 'ACTIVE',  'Fully furnished inventory checklist signed. No structural modifications permitted.'),
(3, 7,  2,  16,120000.00, 600000.00, 3000.00, 6.00, '2025-09-01', '2028-08-31', 'ACTIVE',  '36-month commercial lease. Tenant bears commercial electricity & signage permits.'),
(4, 18, 8,  16, 95000.00, 285000.00, 4000.00, 5.00, '2026-07-01', '2027-06-30', 'ACTIVE',  'Pool & garden maintenance included in monthly HOA charges.'),
(5, 5,  15, 5,  12000.00,  24000.00, 1000.00, 0.00, '2025-05-01', '2026-03-31', 'EXPIRED', 'Student executive PG lease completed with full security deposit refunded.');

INSERT INTO rent_payments (rent_payment_id, agreement_id, payment_month, amount_paid, late_fee, payment_date, payment_method, transaction_ref, payment_status) VALUES
(1,  1, 'August 2026',    65000.00, 0.00, '2026-08-03', 'SIMULATED_UPI',  'TXN-RENT-202608-1001', 'SUCCESS'),
(2,  1, 'September 2026', 65000.00, 0.00, '2026-09-04', 'SIMULATED_UPI',  'TXN-RENT-202609-1002', 'SUCCESS'),
(3,  1, 'October 2026',   65000.00, 0.00, '2026-10-04', 'SIMULATED_UPI',  'TXN-RENT-202610-1003', 'SUCCESS'),
(4,  1, 'November 2026',  65000.00, 0.00, '2026-11-03', 'SIMULATED_UPI',  'TXN-RENT-202611-1004', 'SUCCESS'),
(5,  2, 'September 2026',145000.00, 0.00, '2026-09-02', 'SIMULATED_NEFT', 'TXN-RENT-202609-2001', 'SUCCESS'),
(6,  2, 'October 2026',  145000.00, 0.00, '2026-10-02', 'SIMULATED_NEFT', 'TXN-RENT-202610-2002', 'SUCCESS'),
(7,  2, 'November 2026', 145000.00, 0.00, '2026-11-05', 'SIMULATED_NEFT', 'TXN-RENT-202611-2003', 'SUCCESS'),
(8,  3, 'September 2026',120000.00, 0.00, '2026-09-05', 'SIMULATED_NEFT', 'TXN-RENT-202609-3001', 'SUCCESS'),
(9,  3, 'October 2026',  120000.00,1500.00,'2026-10-09','SIMULATED_NEFT', 'TXN-RENT-202610-3002', 'SUCCESS'),
(10, 3, 'November 2026', 120000.00, 0.00, '2026-11-04', 'SIMULATED_UPI',  'TXN-RENT-202611-3003', 'SUCCESS'),
(11, 4, 'October 2026',   95000.00, 0.00, '2026-10-03', 'SIMULATED_CARD', 'TXN-RENT-202610-4001', 'SUCCESS'),
(12, 4, 'November 2026',  95000.00, 0.00, '2026-11-02', 'SIMULATED_CARD', 'TXN-RENT-202611-4002', 'SUCCESS');

-- -----------------------------------------------------------------------------
-- 21. PURCHASE TRANSACTIONS (Simulated Purchase Records)
-- -----------------------------------------------------------------------------
INSERT INTO purchase_transactions (transaction_id, property_id, buyer_id, seller_id, purchase_price, stamp_duty_est, registration_fee_est, transaction_date, payment_mode, payment_status, transaction_reference, remarks) VALUES
(1, 22, 4,  3,  11500000.00,  575000.00, 115000.00, NOW() - INTERVAL 15 DAY, 'SIMULATED_RTGS',      'SUCCESS', 'TXN-PUR-202611-7782', 'Simulated full settlement for MUDA corner site in Mysuru.'),
(2, 3,  9,  7,   9400000.00,  658000.00,  94000.00, NOW() - INTERVAL 9 DAY,  'SIMULATED_HOME_LOAN', 'PENDING', 'TXN-PUR-202611-8814', 'Simulated bank loan sanction disbursement in progress.'),
(3, 14, 18, 13, 26000000.00, 1820000.00, 260000.00, NOW() - INTERVAL 4 DAY,  'SIMULATED_ESCROW',    'PENDING', 'TXN-PUR-202611-9120', 'Simulated escrow token booked for Pollachi coconut farm.');

-- -----------------------------------------------------------------------------
-- 22. GENERAL PAYMENTS LEDGER
-- -----------------------------------------------------------------------------
INSERT INTO payments (user_id, property_id, amount, payment_purpose, payment_status, payment_gateway_ref, payment_timestamp) VALUES
(4,  22, 11500000.00, 'PURCHASE_SETTLEMENT', 'COMPLETED', 'GW-PAY-900101', NOW() - INTERVAL 15 DAY),
(5,  2,    300000.00, 'SECURITY_DEPOSIT',    'COMPLETED', 'GW-PAY-900102', '2026-03-28 11:15:00'),
(5,  2,     65000.00, 'MONTHLY_RENT',        'COMPLETED', 'GW-PAY-900103', '2026-11-03 09:20:00'),
(10, 4,    600000.00, 'SECURITY_DEPOSIT',    'COMPLETED', 'GW-PAY-900104', '2026-05-25 14:40:00'),
(10, 4,    145000.00, 'MONTHLY_RENT',        'COMPLETED', 'GW-PAY-900105', '2026-11-05 10:05:00'),
(16, 7,    120000.00, 'MONTHLY_RENT',        'COMPLETED', 'GW-PAY-900106', '2026-11-04 16:30:00'),
(9,  3,    500000.00, 'BOOKING_TOKEN',       'COMPLETED', 'GW-PAY-900107', NOW() - INTERVAL 9 DAY),
(18, 14,  1000000.00, 'BOOKING_TOKEN',       'COMPLETED', 'GW-PAY-900108', NOW() - INTERVAL 4 DAY),
(6,  8,     15000.00, 'FEATURED_LISTING_FEE','COMPLETED', 'GW-PAY-900109', NOW() - INTERVAL 12 DAY),
(11, 28,    25000.00, 'FEATURED_LISTING_FEE','COMPLETED', 'GW-PAY-900110', NOW() - INTERVAL 11 DAY);

-- -----------------------------------------------------------------------------
-- 23. PROPERTY PRICE HISTORY
-- -----------------------------------------------------------------------------
INSERT INTO property_price_history (property_id, old_price, new_price, change_reason, changed_at) VALUES
(1,  17200000.00, 18500000.00, 'Annual Mysuru Vijayanagar appreciation (+7.5%)',  NOW() - INTERVAL 60 DAY),
(2,     60000.00,    65000.00, 'Indiranagar metro corridor rental revision',      NOW() - INTERVAL 90 DAY),
(8,    420000.00,   450000.00, 'Added new boardroom & server rack upgrades',      NOW() - INTERVAL 45 DAY),
(12, 15000000.00, 16500000.00, 'Second high-yield borewell commissioned',         NOW() - INTERVAL 50 DAY),
(17, 20500000.00, 22000000.00, 'Added outdoor stone gazebo & solar fencing',      NOW() - INTERVAL 40 DAY),
(23,  8800000.00,  9600000.00, 'Airport Terminal 2 & Metro Blue Line boost',      NOW() - INTERVAL 35 DAY),
(28, 10400000.00, 11200000.00, 'Phase-2 structural completion price milestone',   NOW() - INTERVAL 25 DAY);

-- -----------------------------------------------------------------------------
-- 24. NOTIFICATIONS & AUDIT LOGS
-- -----------------------------------------------------------------------------
INSERT INTO notifications (user_id, title, message, notification_type, is_read) VALUES
(2,  'Owner Verification Approved', 'Congratulations! Your Aadhaar & Sale Deed KYC is verified. Your Verified Owner badge is now live.', 'VERIFICATION', TRUE),
(3,  'Property Sold',               'Priya Sharma completed simulated purchase TXN-PUR-202611-7782 for Site No 102, Vijayanagar.',       'PAYMENT',      TRUE),
(4,  'Visit Approved',              'Rajesh Gowda approved your visit for Luxury 4 BHK Independent Villa in Vijayanagar.',               'VISIT',        FALSE),
(5,  'Rent Receipt Generated',      'November 2026 rent of Rs. 65,000 recorded successfully (Ref: TXN-RENT-202611-1004).',               'PAYMENT',      TRUE),
(12, 'Listing Pending Review',      'Your Mango Orchard listing on Hunsur Road is queued for administrator approval.',                   'APPROVAL',     FALSE),
(1,  'Pending Approvals Alert',     '2 properties and 2 owner KYC requests are awaiting administrator review.',                          'SYSTEM',       FALSE);

INSERT INTO audit_logs (actor_user_id, action_type, entity_table, entity_id, details) VALUES
(1,  'APPROVE_PROPERTY',     'properties',            1,  'Approved Luxury 4 BHK Villa in Vijayanagar after document review.'),
(1,  'VERIFY_OWNER',         'owner_verifications',   1,  'Approved KYC for Rajesh Gowda (user_id=2).'),
(20, 'VERIFY_OWNER',         'owner_verifications',   4,  'Approved Patta & Aadhaar verification for Meenakshi Iyer (user_id=7).'),
(4,  'PURCHASE_SIMULATED',   'purchase_transactions', 1,  'Simulated purchase of MUDA corner plot #22 for Rs. 1,15,00,000.'),
(5,  'RENT_PAYMENT_RECORDED','rent_payments',         4,  'Recorded November 2026 rent via SIMULATED_UPI.');


-- =============================================================================
-- PART 3: ANALYTICAL VIEWS (READY FOR DASHBOARDS & REPORTING)
-- =============================================================================

-- VIEW 1: Complete Public Property Catalog View
CREATE OR REPLACE VIEW vw_approved_properties_catalog AS
SELECT
    p.property_id,
    p.title,
    pc.category_name,
    pc.category_code,
    p.purpose,
    p.price,
    p.property_area,
    p.area_unit,
    p.city,
    p.locality,
    p.state,
    p.listing_status,
    p.is_featured,
    p.views_count,
    p.primary_image_url,
    u.full_name AS owner_name,
    u.user_role AS owner_role,
    u.is_verified AS owner_verified,
    COALESCE(r.bedrooms, f.bedrooms, 0) AS bedrooms,
    COALESCE(r.bathrooms, f.bathrooms, 0) AS bathrooms,
    ROUND(COALESCE((SELECT AVG(pr.rating) FROM property_reviews pr WHERE pr.property_id = p.property_id), 0), 1) AS avg_rating,
    (SELECT COUNT(*) FROM property_reviews pr WHERE pr.property_id = p.property_id) AS review_count,
    p.created_at
FROM properties p
INNER JOIN property_categories pc ON p.category_id = pc.category_id
INNER JOIN users u ON p.owner_id = u.user_id
LEFT JOIN residential_details r ON p.property_id = r.property_id
LEFT JOIN farmhouse_details f ON p.property_id = f.property_id
WHERE p.approval_status = 'APPROVED';

-- VIEW 2: Real-Time Admin Dashboard KPI Summary
CREATE OR REPLACE VIEW vw_admin_dashboard_kpis AS
SELECT
    (SELECT COUNT(*) FROM users WHERE is_active = TRUE) AS total_users,
    (SELECT COUNT(*) FROM properties) AS total_properties,
    (SELECT COUNT(*) FROM properties WHERE purpose = 'BUY' AND approval_status = 'APPROVED') AS properties_for_sale,
    (SELECT COUNT(*) FROM properties WHERE purpose IN ('RENT', 'LEASE') AND approval_status = 'APPROVED') AS properties_for_rent_lease,
    (SELECT COUNT(*) FROM properties WHERE approval_status = 'PENDING') AS pending_property_approvals,
    (SELECT COUNT(*) FROM users WHERE user_role IN ('PROPERTY_OWNER', 'SELLER', 'AGENT') AND is_verified = TRUE) AS verified_owners,
    (SELECT COUNT(*) FROM property_visits) AS total_appointments,
    (SELECT COUNT(*) FROM purchase_transactions) AS total_purchase_transactions,
    (SELECT COALESCE(SUM(purchase_price), 0) FROM purchase_transactions WHERE payment_status = 'SUCCESS') AS total_purchase_volume_inr,
    (SELECT COALESCE(SUM(amount_paid), 0) FROM rent_payments WHERE payment_status = 'SUCCESS') AS total_rent_collected_inr;

-- VIEW 3: Active Rentals & Collection Ledger Summary
CREATE OR REPLACE VIEW vw_active_rentals_summary AS
SELECT
    ra.agreement_id,
    p.title AS property_title,
    p.city,
    owner_u.full_name AS owner_name,
    tenant_u.full_name AS tenant_name,
    ra.monthly_rent,
    ra.security_deposit,
    ra.agreement_start_date,
    ra.agreement_end_date,
    ra.agreement_status,
    COUNT(rp.rent_payment_id) AS months_paid,
    COALESCE(SUM(rp.amount_paid), 0) AS total_rent_paid
FROM rental_agreements ra
INNER JOIN properties p ON ra.property_id = p.property_id
INNER JOIN users owner_u ON ra.owner_id = owner_u.user_id
INNER JOIN users tenant_u ON ra.tenant_id = tenant_u.user_id
LEFT JOIN rent_payments rp ON ra.agreement_id = rp.agreement_id AND rp.payment_status = 'SUCCESS'
GROUP BY ra.agreement_id, p.title, p.city, owner_u.full_name, tenant_u.full_name,
         ra.monthly_rent, ra.security_deposit, ra.agreement_start_date, ra.agreement_end_date, ra.agreement_status;


-- =============================================================================
-- PART 4: AUTOMATION TRIGGERS & STORED PROCEDURES
-- =============================================================================

DELIMITER //

-- TRIGGER 1: Automatically log every property price change into property_price_history
CREATE TRIGGER trg_property_price_change
AFTER UPDATE ON properties
FOR EACH ROW
BEGIN
    IF OLD.price <> NEW.price THEN
        INSERT INTO property_price_history (property_id, old_price, new_price, change_reason)
        VALUES (NEW.property_id, OLD.price, NEW.price, 'Owner/Admin price revision');
    END IF;
END //

-- TRIGGER 2: Automatically mark property SOLD & log audit record on successful purchase
CREATE TRIGGER trg_after_purchase_complete
AFTER INSERT ON purchase_transactions
FOR EACH ROW
BEGIN
    IF NEW.payment_status = 'SUCCESS' THEN
        UPDATE properties
        SET listing_status = 'SOLD'
        WHERE property_id = NEW.property_id;

        INSERT INTO audit_logs (actor_user_id, action_type, entity_table, entity_id, details)
        VALUES (NEW.buyer_id, 'AUTO_MARK_SOLD', 'properties', NEW.property_id,
                CONCAT('Marked SOLD via transaction ', NEW.transaction_reference));
    END IF;
END //

-- TRIGGER 3: Automatically grant Verified Owner badge when Admin approves KYC
CREATE TRIGGER trg_after_verification_approved
AFTER UPDATE ON owner_verifications
FOR EACH ROW
BEGIN
    IF OLD.verification_status <> 'APPROVED' AND NEW.verification_status = 'APPROVED' THEN
        UPDATE users
        SET is_verified = TRUE
        WHERE user_id = NEW.user_id;

        INSERT INTO notifications (user_id, title, message, notification_type)
        VALUES (NEW.user_id, 'Verified Owner Badge Granted',
                'Your identity and ownership documents have been approved by the administrator.', 'VERIFICATION');
    END IF;
END //

-- STORED PROCEDURE 1: Dynamic Multi-Filter Property Search
CREATE PROCEDURE sp_search_properties(
    IN p_city VARCHAR(80),
    IN p_category_code VARCHAR(30),
    IN p_purpose VARCHAR(20),
    IN p_min_price DECIMAL(15,2),
    IN p_max_price DECIMAL(15,2),
    IN p_sort_by VARCHAR(20)
)
BEGIN
    SELECT *
    FROM vw_approved_properties_catalog
    WHERE listing_status = 'AVAILABLE'
      AND (p_city IS NULL OR p_city = '' OR p_city = 'ALL' OR LOWER(city) = LOWER(p_city))
      AND (p_category_code IS NULL OR p_category_code = '' OR p_category_code = 'ALL' OR UPPER(category_code) = UPPER(p_category_code))
      AND (p_purpose IS NULL OR p_purpose = '' OR p_purpose = 'ALL' OR UPPER(purpose) = UPPER(p_purpose))
      AND (p_min_price IS NULL OR p_min_price <= 0 OR price >= p_min_price)
      AND (p_max_price IS NULL OR p_max_price <= 0 OR price <= p_max_price)
    ORDER BY
        CASE WHEN p_sort_by = 'PRICE_ASC'  THEN price END ASC,
        CASE WHEN p_sort_by = 'PRICE_DESC' THEN price END DESC,
        CASE WHEN p_sort_by = 'AREA_DESC'  THEN property_area END DESC,
        created_at DESC;
END //

-- STORED PROCEDURE 2: City & Category Real Estate Market Analytics Report
CREATE PROCEDURE sp_get_city_market_report()
BEGIN
    SELECT
        p.city,
        p.state,
        pc.category_name,
        COUNT(p.property_id) AS total_listings,
        ROUND(MIN(p.price), 2) AS min_price_inr,
        ROUND(AVG(p.price), 2) AS avg_price_inr,
        ROUND(MAX(p.price), 2) AS max_price_inr,
        SUM(p.views_count) AS total_buyer_views
    FROM properties p
    INNER JOIN property_categories pc ON p.category_id = pc.category_id
    WHERE p.approval_status = 'APPROVED'
    GROUP BY p.city, p.state, pc.category_name
    ORDER BY total_listings DESC, avg_price_inr DESC;
END //

DELIMITER ;


-- =============================================================================
-- PART 5: QUICK VERIFICATION QUERIES (RUN TO INSPECT YOUR VAST DATABASE)
-- =============================================================================
SELECT * FROM vw_admin_dashboard_kpis;
CALL sp_get_city_market_report();
CALL sp_search_properties('Mysuru', 'ALL', 'BUY', 0, 50000000, 'PRICE_ASC');
