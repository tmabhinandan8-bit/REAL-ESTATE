-- =============================================================================
-- MASTER PRODUCTION DATABASE SCRIPT: REAL ESTATE MANAGEMENT SYSTEM
-- Compatible with: Supabase SQL Editor & PostgreSQL 14+
-- 
-- Includes:
-- 1. Clean Reset: Drops conflicting legacy tables safely with CASCADE
-- 2. All 16 Normalized Relational Tables & Constraints (UUID Primary Keys)
-- 3. Full Indian Geographical Database (28 States, 8 UTs, 786 LGD Districts)
-- 4. Automatic Auth User Sync Trigger & Updated_At Timestamps
-- 5. High-Performance B-Tree Composite Indexes
-- 6. Complete Production Row Level Security (RLS) Policies
-- 7. Supabase Storage Bucket ('property-images') & Storage Access Policies
-- =============================================================================

-- =============================================================================
-- CLEAN RESET: DROP EXISTING TABLES TO PREVENT SCHEMA MISMATCHES
-- Fixes error: column "id" referenced in foreign key constraint does not exist
-- (which happens when previous tables had legacy column names like property_id / user_id)
-- =============================================================================

DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.reviews CASCADE;
DROP TABLE IF EXISTS public.transactions CASCADE;
DROP TABLE IF EXISTS public.appointments CASCADE;
DROP TABLE IF EXISTS public.inquiries CASCADE;
DROP TABLE IF EXISTS public.favorites CASCADE;
DROP TABLE IF EXISTS public.property_features CASCADE;
DROP TABLE IF EXISTS public.property_images CASCADE;
DROP TABLE IF EXISTS public.properties CASCADE;
DROP TABLE IF EXISTS public.agents CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;
DROP TABLE IF EXISTS public.localities CASCADE;
DROP TABLE IF EXISTS public.cities CASCADE;
DROP TABLE IF EXISTS public.districts CASCADE;
DROP TABLE IF EXISTS public.states CASCADE;
DROP TABLE IF EXISTS public.countries CASCADE;

-- Also clean up any legacy tables from previous SQL runs if present
DROP TABLE IF EXISTS public.property_favourites CASCADE;
DROP TABLE IF EXISTS public.property_enquiries CASCADE;
DROP TABLE IF EXISTS public.property_visits CASCADE;
DROP TABLE IF EXISTS public.property_reviews CASCADE;
DROP TABLE IF EXISTS public.property_amenities CASCADE;
DROP TABLE IF EXISTS public.amenities_master CASCADE;
DROP TABLE IF EXISTS public.project_units CASCADE;
DROP TABLE IF EXISTS public.projects CASCADE;
DROP TABLE IF EXISTS public.building_details CASCADE;
DROP TABLE IF EXISTS public.plot_details CASCADE;
DROP TABLE IF EXISTS public.farmhouse_details CASCADE;
DROP TABLE IF EXISTS public.land_details CASCADE;
DROP TABLE IF EXISTS public.commercial_details CASCADE;
DROP TABLE IF EXISTS public.residential_details CASCADE;
DROP TABLE IF EXISTS public.property_locations CASCADE;
DROP TABLE IF EXISTS public.property_categories CASCADE;
DROP TABLE IF EXISTS public.user_profiles CASCADE;
DROP TABLE IF EXISTS public.users CASCADE;
DROP TABLE IF EXISTS public.audit_logs CASCADE;
DROP TABLE IF EXISTS public.property_price_history CASCADE;
DROP TABLE IF EXISTS public.payments CASCADE;
DROP TABLE IF EXISTS public.purchase_transactions CASCADE;
DROP TABLE IF EXISTS public.rent_payments CASCADE;
DROP TABLE IF EXISTS public.rental_agreements CASCADE;
DROP TABLE IF EXISTS public.owner_verifications CASCADE;


-- =============================================================================
-- SUPABASE POSTGRESQL SCHEMA FOR REAL ESTATE MANAGEMENT SYSTEM
-- Compatible with Supabase SQL Editor & PostgreSQL 14+
-- =============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 1. GEOGRAPHICAL LOCATION TABLES (INDIA LGD / IGOD COMPLIANT)
-- =============================================================================

-- Table 1: countries
CREATE TABLE public.countries (
    id SERIAL PRIMARY KEY,
    country_name VARCHAR(100) NOT NULL UNIQUE,
    country_code VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 2: states
CREATE TABLE public.states (
    id SERIAL PRIMARY KEY,
    country_id INT NOT NULL REFERENCES public.countries(id) ON DELETE CASCADE,
    state_name VARCHAR(100) NOT NULL,
    state_type VARCHAR(20) NOT NULL CHECK (state_type IN ('STATE', 'UNION_TERRITORY')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_state_country UNIQUE (country_id, state_name)
);

-- Table 3: districts
CREATE TABLE public.districts (
    id SERIAL PRIMARY KEY,
    state_id INT NOT NULL REFERENCES public.states(id) ON DELETE CASCADE,
    district_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_district_state UNIQUE (state_id, district_name)
);

-- Table 4: cities
CREATE TABLE public.cities (
    id SERIAL PRIMARY KEY,
    district_id INT NOT NULL REFERENCES public.districts(id) ON DELETE CASCADE,
    city_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_city_district UNIQUE (district_id, city_name)
);

-- Table 5: localities
CREATE TABLE public.localities (
    id SERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES public.cities(id) ON DELETE CASCADE,
    locality_name VARCHAR(120) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_locality_city_pincode UNIQUE (city_id, locality_name, pincode)
);

-- =============================================================================
-- 2. USER PROFILES TABLE (LINKS TO auth.users)
-- =============================================================================

CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    role VARCHAR(30) NOT NULL DEFAULT 'BUYER' 
        CHECK (role IN ('BUYER', 'SELLER', 'TENANT', 'AGENT', 'ADMIN')),
    avatar_url TEXT,
    bio TEXT,
    agency_name VARCHAR(150),
    rera_number VARCHAR(100),
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 3. PROPERTIES TABLE
-- =============================================================================

CREATE TABLE public.properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    property_title VARCHAR(255) NOT NULL,
    property_description TEXT,
    property_type VARCHAR(50) NOT NULL CHECK (property_type IN (
        'Apartment', 'Independent House', 'Villa', 'Plot / Land',
        'Commercial Property', 'Office Space', 'Shop', 'Farmhouse',
        'Agricultural Land', 'Industrial Property'
    )),
    listing_type VARCHAR(20) NOT NULL CHECK (listing_type IN ('SALE', 'RENT', 'LEASE')),
    price NUMERIC(15, 2) NOT NULL CHECK (price >= 0),
    area NUMERIC(12, 2) NOT NULL CHECK (area > 0),
    area_unit VARCHAR(20) NOT NULL DEFAULT 'SQ_FT' CHECK (area_unit IN ('SQ_FT', 'SQ_M', 'ACRE')),
    bedrooms INT DEFAULT 0 CHECK (bedrooms >= 0),
    bathrooms INT DEFAULT 0 CHECK (bathrooms >= 0),
    balconies INT DEFAULT 0 CHECK (balconies >= 0),
    furnishing_status VARCHAR(30) DEFAULT 'UNFURNISHED' 
        CHECK (furnishing_status IN ('FURNISHED', 'SEMI_FURNISHED', 'UNFURNISHED')),
    parking INT DEFAULT 0 CHECK (parking >= 0),
    address TEXT NOT NULL,
    country_id INT REFERENCES public.countries(id) ON DELETE SET NULL,
    state_id INT REFERENCES public.states(id) ON DELETE SET NULL,
    district_id INT REFERENCES public.districts(id) ON DELETE SET NULL,
    city_id INT REFERENCES public.cities(id) ON DELETE SET NULL,
    locality_id INT REFERENCES public.localities(id) ON DELETE SET NULL,
    pincode VARCHAR(10),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    property_status VARCHAR(30) DEFAULT 'AVAILABLE' 
        CHECK (property_status IN ('AVAILABLE', 'UNDER_OFFER', 'SOLD', 'RENTED', 'INACTIVE')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 4. ADDITIONAL PROPERTY & USER MODULE TABLES
-- =============================================================================

-- Table: property_images
CREATE TABLE public.property_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table: property_features
CREATE TABLE public.property_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    feature_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table: favorites
CREATE TABLE public.favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_property_favorite UNIQUE (user_id, property_id)
);

-- Table: inquiries
CREATE TABLE public.inquiries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    buyer_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    buyer_name VARCHAR(150) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    buyer_phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'CONTACTED', 'RESOLVED', 'CLOSED')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table: appointments
CREATE TABLE public.appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    status VARCHAR(30) DEFAULT 'SCHEDULED' 
        CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table: transactions
CREATE TABLE public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES public.properties(id) ON DELETE RESTRICT,
    buyer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('PURCHASE', 'RENTAL', 'LEASE')),
    amount NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    payment_status VARCHAR(30) DEFAULT 'PENDING' 
        CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    payment_method VARCHAR(50),
    transaction_reference VARCHAR(100) UNIQUE,
    transaction_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Table: reviews
CREATE TABLE public.reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID REFERENCES public.properties(id) ON DELETE CASCADE,
    agent_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_target CHECK (property_id IS NOT NULL OR agent_id IS NOT NULL)
);

-- Table: agents
CREATE TABLE public.agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE,
    license_number VARCHAR(100),
    agency_name VARCHAR(150),
    specialization VARCHAR(100),
    experience_years INT DEFAULT 0 CHECK (experience_years >= 0),
    rating NUMERIC(3, 2) DEFAULT 5.0,
    total_reviews INT DEFAULT 0 CHECK (total_reviews >= 0),
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table: notifications
CREATE TABLE public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    notification_type VARCHAR(50) DEFAULT 'GENERAL',
    related_entity_id UUID,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 5. PERFORMANCE INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_states_country ON public.states(country_id);
CREATE INDEX IF NOT EXISTS idx_districts_state ON public.districts(state_id);
CREATE INDEX IF NOT EXISTS idx_cities_district ON public.cities(district_id);
CREATE INDEX IF NOT EXISTS idx_localities_city ON public.localities(city_id);
CREATE INDEX IF NOT EXISTS idx_localities_pincode ON public.localities(pincode);

CREATE INDEX IF NOT EXISTS idx_properties_owner ON public.properties(owner_id);
CREATE INDEX IF NOT EXISTS idx_properties_location ON public.properties(state_id, district_id, city_id);
CREATE INDEX IF NOT EXISTS idx_properties_filter ON public.properties(property_status, listing_type, property_type);
CREATE INDEX IF NOT EXISTS idx_properties_price ON public.properties(price);
CREATE INDEX IF NOT EXISTS idx_properties_created ON public.properties(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_prop_images_property ON public.property_images(property_id, is_primary);
CREATE INDEX IF NOT EXISTS idx_prop_features_property ON public.property_features(property_id);
CREATE INDEX IF NOT EXISTS idx_favorites_user ON public.favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_property ON public.inquiries(property_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_buyer ON public.inquiries(buyer_id);
CREATE INDEX IF NOT EXISTS idx_appointments_user ON public.appointments(user_id);
CREATE INDEX IF NOT EXISTS idx_appointments_property ON public.appointments(property_id);
CREATE INDEX IF NOT EXISTS idx_transactions_parties ON public.transactions(buyer_id, seller_id, property_id);
CREATE INDEX IF NOT EXISTS idx_reviews_target ON public.reviews(property_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications(user_id, is_read);

-- =============================================================================
-- 6. AUTOMATION TRIGGERS & FUNCTIONS
-- =============================================================================

-- Auto update timestamp function
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON public.profiles;
CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_properties_updated_at ON public.properties;
CREATE TRIGGER trg_properties_updated_at
    BEFORE UPDATE ON public.properties
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_appointments_updated_at ON public.appointments;
CREATE TRIGGER trg_appointments_updated_at
    BEFORE UPDATE ON public.appointments
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- Automatically create profile on user signup via Supabase Auth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (
        id,
        full_name,
        email,
        phone_number,
        role,
        avatar_url,
        is_verified
    )
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', split_part(NEW.email, '@', 1)),
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'phone_number', NEW.phone, ''),
        COALESCE(NEW.raw_user_meta_data->>'role', 'BUYER'),
        COALESCE(NEW.raw_user_meta_data->>'avatar_url', ''),
        COALESCE((NEW.raw_user_meta_data->>'is_verified')::BOOLEAN, false)
    )
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        phone_number = CASE WHEN EXCLUDED.phone_number <> '' THEN EXCLUDED.phone_number ELSE public.profiles.phone_number END,
        updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Helper function to check if current user is admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'ADMIN'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- Sync any existing users from auth.users into public.profiles
INSERT INTO public.profiles (id, full_name, email, role, is_verified)
SELECT 
    id,
    COALESCE(raw_user_meta_data->>'full_name', raw_user_meta_data->>'name', split_part(email, '@', 1)),
    email,
    COALESCE(raw_user_meta_data->>'role', 'BUYER'),
    COALESCE((raw_user_meta_data->>'is_verified')::BOOLEAN, false)
FROM auth.users
ON CONFLICT (id) DO NOTHING;


-- =============================================================================
-- INDIA OFFICIAL GEOGRAPHICAL SEED DATA (LGD / IGOD COMPLIANT)
-- Data Source: Local Government Directory (LGD), Ministry of Panchayati Raj, GoI (https://lgdirectory.gov.in)
-- Integrated Government Online Directory (IGOD) (https://igod.gov.in)
-- Verified Date: November 2026
-- Total: 28 States, 8 Union Territories, 786 Districts, No Duplicates
-- =============================================================================

-- 1. Country: India
INSERT INTO public.countries (id, country_name, country_code)
VALUES (1, 'India', 'IN')
ON CONFLICT (id) DO UPDATE SET country_name = EXCLUDED.country_name;

-- 2. States and Union Territories (All 28 States + 8 UTs)

INSERT INTO public.states (id, country_id, state_name, state_type) VALUES
(1, 1, 'Andhra Pradesh', 'STATE'),
(2, 1, 'Arunachal Pradesh', 'STATE'),
(3, 1, 'Assam', 'STATE'),
(4, 1, 'Bihar', 'STATE'),
(5, 1, 'Chhattisgarh', 'STATE'),
(6, 1, 'Goa', 'STATE'),
(7, 1, 'Gujarat', 'STATE'),
(8, 1, 'Haryana', 'STATE'),
(9, 1, 'Himachal Pradesh', 'STATE'),
(10, 1, 'Jharkhand', 'STATE'),
(11, 1, 'Karnataka', 'STATE'),
(12, 1, 'Kerala', 'STATE'),
(13, 1, 'Madhya Pradesh', 'STATE'),
(14, 1, 'Maharashtra', 'STATE'),
(15, 1, 'Manipur', 'STATE'),
(16, 1, 'Meghalaya', 'STATE'),
(17, 1, 'Mizoram', 'STATE'),
(18, 1, 'Nagaland', 'STATE'),
(19, 1, 'Odisha', 'STATE'),
(20, 1, 'Punjab', 'STATE'),
(21, 1, 'Rajasthan', 'STATE'),
(22, 1, 'Sikkim', 'STATE'),
(23, 1, 'Tamil Nadu', 'STATE'),
(24, 1, 'Telangana', 'STATE'),
(25, 1, 'Tripura', 'STATE'),
(26, 1, 'Uttar Pradesh', 'STATE'),
(27, 1, 'Uttarakhand', 'STATE'),
(28, 1, 'West Bengal', 'STATE'),
(29, 1, 'Andaman and Nicobar Islands', 'UNION_TERRITORY'),
(30, 1, 'Chandigarh', 'UNION_TERRITORY'),
(31, 1, 'Dadra and Nagar Haveli and Daman and Diu', 'UNION_TERRITORY'),
(32, 1, 'Delhi', 'UNION_TERRITORY'),
(33, 1, 'Jammu and Kashmir', 'UNION_TERRITORY'),
(34, 1, 'Ladakh', 'UNION_TERRITORY'),
(35, 1, 'Lakshadweep', 'UNION_TERRITORY'),
(36, 1, 'Puducherry', 'UNION_TERRITORY')
ON CONFLICT (id) DO NOTHING;

-- 3. Districts (786 Officially Recognized Districts)

INSERT INTO public.districts (id, state_id, district_name) VALUES
(1, 1, 'Alluri Sitharama Raju'),
(2, 1, 'Anakapalli'),
(3, 1, 'Ananthapuramu'),
(4, 1, 'Annamayya'),
(5, 1, 'Bapatla'),
(6, 1, 'Chittoor'),
(7, 1, 'Dr. B.R. Ambedkar Konaseema'),
(8, 1, 'East Godavari'),
(9, 1, 'Eluru'),
(10, 1, 'Guntur'),
(11, 1, 'Kakinada'),
(12, 1, 'Krishna'),
(13, 1, 'Kurnool'),
(14, 1, 'Nandyal'),
(15, 1, 'NTR'),
(16, 1, 'Palnadu'),
(17, 1, 'Parvathipuram Manyam'),
(18, 1, 'Prakasam'),
(19, 1, 'Sri Potti Sriramulu Nellore'),
(20, 1, 'Sri Sathya Sai'),
(21, 1, 'Srikakulam'),
(22, 1, 'Tirupati'),
(23, 1, 'Visakhapatnam'),
(24, 1, 'Vizianagaram'),
(25, 1, 'West Godavari'),
(26, 1, 'YSR Kadapa'),
(27, 2, 'Anjaw'),
(28, 2, 'Changlang'),
(29, 2, 'Dibang Valley'),
(30, 2, 'East Kameng'),
(31, 2, 'East Siang'),
(32, 2, 'Itanagar Capital Complex'),
(33, 2, 'Kamle'),
(34, 2, 'Kra Daadi'),
(35, 2, 'Kurung Kumey'),
(36, 2, 'Leparada'),
(37, 2, 'Lohit'),
(38, 2, 'Longding'),
(39, 2, 'Lower Dibang Valley'),
(40, 2, 'Lower Siang'),
(41, 2, 'Lower Subansiri'),
(42, 2, 'Namsai'),
(43, 2, 'Pakke Kessang'),
(44, 2, 'Papum Pare'),
(45, 2, 'Shi Yomi'),
(46, 2, 'Siang'),
(47, 2, 'Tawang'),
(48, 2, 'Tirap'),
(49, 2, 'Upper Siang'),
(50, 2, 'Upper Subansiri'),
(51, 2, 'West Kameng'),
(52, 2, 'West Siang'),
(53, 3, 'Bajali'),
(54, 3, 'Baksa'),
(55, 3, 'Barpeta'),
(56, 3, 'Biswanath'),
(57, 3, 'Bongaigaon'),
(58, 3, 'Cachar'),
(59, 3, 'Charaideo'),
(60, 3, 'Chirang'),
(61, 3, 'Darrang'),
(62, 3, 'Dhemaji'),
(63, 3, 'Dhubri'),
(64, 3, 'Dibrugarh'),
(65, 3, 'Dima Hasao'),
(66, 3, 'Goalpara'),
(67, 3, 'Golaghat'),
(68, 3, 'Hailakandi'),
(69, 3, 'Hojai'),
(70, 3, 'Jorhat'),
(71, 3, 'Kamrup'),
(72, 3, 'Kamrup Metropolitan'),
(73, 3, 'Karbi Anglong'),
(74, 3, 'Karimganj'),
(75, 3, 'Kokrajhar'),
(76, 3, 'Lakhimpur'),
(77, 3, 'Majuli'),
(78, 3, 'Morigaon'),
(79, 3, 'Nagaon'),
(80, 3, 'Nalbari'),
(81, 3, 'Sivasagar'),
(82, 3, 'Sonitpur'),
(83, 3, 'South Salmara-Mankachar'),
(84, 3, 'Tamulpur'),
(85, 3, 'Tinsukia'),
(86, 3, 'Udalguri'),
(87, 3, 'West Karbi Anglong'),
(88, 4, 'Araria'),
(89, 4, 'Arwal'),
(90, 4, 'Aurangabad'),
(91, 4, 'Banka'),
(92, 4, 'Begusarai'),
(93, 4, 'Bhagalpur'),
(94, 4, 'Bhojpur'),
(95, 4, 'Buxar'),
(96, 4, 'Darbhanga'),
(97, 4, 'East Champaran'),
(98, 4, 'Gaya'),
(99, 4, 'Gopalganj'),
(100, 4, 'Jamui')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(101, 4, 'Jehanabad'),
(102, 4, 'Kaimur'),
(103, 4, 'Katihar'),
(104, 4, 'Khagaria'),
(105, 4, 'Kishanganj'),
(106, 4, 'Lakhisarai'),
(107, 4, 'Madhepura'),
(108, 4, 'Madhubani'),
(109, 4, 'Munger'),
(110, 4, 'Muzaffarpur'),
(111, 4, 'Nalanda'),
(112, 4, 'Nawada'),
(113, 4, 'Patna'),
(114, 4, 'Purnia'),
(115, 4, 'Rohtas'),
(116, 4, 'Saharsa'),
(117, 4, 'Samastipur'),
(118, 4, 'Saran'),
(119, 4, 'Sheikhpura'),
(120, 4, 'Sheohar'),
(121, 4, 'Sitamarhi'),
(122, 4, 'Siwan'),
(123, 4, 'Supaul'),
(124, 4, 'Vaishali'),
(125, 4, 'West Champaran'),
(126, 5, 'Balod'),
(127, 5, 'Baloda Bazar'),
(128, 5, 'Balrampur'),
(129, 5, 'Bastar'),
(130, 5, 'Bemetara'),
(131, 5, 'Bijapur'),
(132, 5, 'Bilaspur'),
(133, 5, 'Dantewada'),
(134, 5, 'Dhamtari'),
(135, 5, 'Durg'),
(136, 5, 'Gariaband'),
(137, 5, 'Gaurela-Pendra-Marwahi'),
(138, 5, 'Janjgir-Champa'),
(139, 5, 'Jashpur'),
(140, 5, 'Kabirdham'),
(141, 5, 'Kanker'),
(142, 5, 'Khairagarh-Chhuikhadan-Gandai'),
(143, 5, 'Kondagaon'),
(144, 5, 'Korba'),
(145, 5, 'Koriya'),
(146, 5, 'Mahasamund'),
(147, 5, 'Manendragarh-Chirmiri-Bharatpur'),
(148, 5, 'Mohla-Manpur-Ambagarh Chowki'),
(149, 5, 'Mungeli'),
(150, 5, 'Narayanpur'),
(151, 5, 'Raigarh'),
(152, 5, 'Raipur'),
(153, 5, 'Rajnandgaon'),
(154, 5, 'Sakti'),
(155, 5, 'Sarangarh-Bilaigarh'),
(156, 5, 'Sukma'),
(157, 5, 'Surajpur'),
(158, 5, 'Surguja'),
(159, 6, 'North Goa'),
(160, 6, 'South Goa'),
(161, 7, 'Ahmedabad'),
(162, 7, 'Amreli'),
(163, 7, 'Anand'),
(164, 7, 'Aravalli'),
(165, 7, 'Banaskantha'),
(166, 7, 'Bharuch'),
(167, 7, 'Bhavnagar'),
(168, 7, 'Botad'),
(169, 7, 'Chhota Udaipur'),
(170, 7, 'Dahod'),
(171, 7, 'Dang'),
(172, 7, 'Devbhumi Dwarka'),
(173, 7, 'Gandhinagar'),
(174, 7, 'Gir Somnath'),
(175, 7, 'Jamnagar'),
(176, 7, 'Junagadh'),
(177, 7, 'Kheda'),
(178, 7, 'Kutch'),
(179, 7, 'Mahisagar'),
(180, 7, 'Mehsana'),
(181, 7, 'Morbi'),
(182, 7, 'Narmada'),
(183, 7, 'Navsari'),
(184, 7, 'Panchmahal'),
(185, 7, 'Patan'),
(186, 7, 'Porbandar'),
(187, 7, 'Rajkot'),
(188, 7, 'Sabarkantha'),
(189, 7, 'Surat'),
(190, 7, 'Surendranagar'),
(191, 7, 'Tapi'),
(192, 7, 'Vadodara'),
(193, 7, 'Valsad'),
(194, 8, 'Ambala'),
(195, 8, 'Bhiwani'),
(196, 8, 'Charkhi Dadri'),
(197, 8, 'Faridabad'),
(198, 8, 'Fatehabad'),
(199, 8, 'Gurugram'),
(200, 8, 'Hisar')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(201, 8, 'Jhajjar'),
(202, 8, 'Jind'),
(203, 8, 'Kaithal'),
(204, 8, 'Karnal'),
(205, 8, 'Kurukshetra'),
(206, 8, 'Mahendragarh'),
(207, 8, 'Nuh'),
(208, 8, 'Palwal'),
(209, 8, 'Panchkula'),
(210, 8, 'Panipat'),
(211, 8, 'Rewari'),
(212, 8, 'Rohtak'),
(213, 8, 'Sirsa'),
(214, 8, 'Sonipat'),
(215, 8, 'Yamunanagar'),
(216, 9, 'Bilaspur'),
(217, 9, 'Chamba'),
(218, 9, 'Hamirpur'),
(219, 9, 'Kangra'),
(220, 9, 'Kinnaur'),
(221, 9, 'Kullu'),
(222, 9, 'Lahaul and Spiti'),
(223, 9, 'Mandi'),
(224, 9, 'Shimla'),
(225, 9, 'Sirmaur'),
(226, 9, 'Solan'),
(227, 9, 'Una'),
(228, 10, 'Bokaro'),
(229, 10, 'Chatra'),
(230, 10, 'Deoghar'),
(231, 10, 'Dhanbad'),
(232, 10, 'Dumka'),
(233, 10, 'East Singhbhum'),
(234, 10, 'Garhwa'),
(235, 10, 'Giridih'),
(236, 10, 'Godda'),
(237, 10, 'Gumla'),
(238, 10, 'Hazaribagh'),
(239, 10, 'Jamtara'),
(240, 10, 'Khunti'),
(241, 10, 'Koderma'),
(242, 10, 'Latehar'),
(243, 10, 'Lohardaga'),
(244, 10, 'Pakur'),
(245, 10, 'Palamu'),
(246, 10, 'Ramgarh'),
(247, 10, 'Ranchi'),
(248, 10, 'Sahebganj'),
(249, 10, 'Seraikela Kharsawan'),
(250, 10, 'Simdega'),
(251, 10, 'West Singhbhum'),
(252, 11, 'Bagalkot'),
(253, 11, 'Ballari'),
(254, 11, 'Belagavi'),
(255, 11, 'Bengaluru Rural'),
(256, 11, 'Bengaluru Urban'),
(257, 11, 'Bidar'),
(258, 11, 'Chamarajanagar'),
(259, 11, 'Chikkaballapur'),
(260, 11, 'Chikkamagaluru'),
(261, 11, 'Chitradurga'),
(262, 11, 'Dakshina Kannada'),
(263, 11, 'Davanagere'),
(264, 11, 'Dharwad'),
(265, 11, 'Gadag'),
(266, 11, 'Hassan'),
(267, 11, 'Haveri'),
(268, 11, 'Kalaburagi'),
(269, 11, 'Kodagu'),
(270, 11, 'Kolar'),
(271, 11, 'Koppal'),
(272, 11, 'Mandya'),
(273, 11, 'Mysuru'),
(274, 11, 'Raichur'),
(275, 11, 'Ramanagara'),
(276, 11, 'Shivamogga'),
(277, 11, 'Tumakuru'),
(278, 11, 'Udupi'),
(279, 11, 'Uttara Kannada'),
(280, 11, 'Vijayapura'),
(281, 11, 'Yadgir'),
(282, 11, 'Vijayanagara'),
(283, 12, 'Alappuzha'),
(284, 12, 'Ernakulam'),
(285, 12, 'Idukki'),
(286, 12, 'Kannur'),
(287, 12, 'Kasaragod'),
(288, 12, 'Kollam'),
(289, 12, 'Kottayam'),
(290, 12, 'Kozhikode'),
(291, 12, 'Malappuram'),
(292, 12, 'Palakkad'),
(293, 12, 'Pathanamthitta'),
(294, 12, 'Thiruvananthapuram'),
(295, 12, 'Thrissur'),
(296, 12, 'Wayanad'),
(297, 13, 'Agar Malwa'),
(298, 13, 'Alirajpur'),
(299, 13, 'Anuppur'),
(300, 13, 'Ashoknagar')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(301, 13, 'Balaghat'),
(302, 13, 'Barwani'),
(303, 13, 'Betul'),
(304, 13, 'Bhind'),
(305, 13, 'Bhopal'),
(306, 13, 'Burhanpur'),
(307, 13, 'Chhatarpur'),
(308, 13, 'Chhindwara'),
(309, 13, 'Damoh'),
(310, 13, 'Datia'),
(311, 13, 'Dewas'),
(312, 13, 'Dhar'),
(313, 13, 'Dindori'),
(314, 13, 'Guna'),
(315, 13, 'Gwalior'),
(316, 13, 'Harda'),
(317, 13, 'Indore'),
(318, 13, 'Jabalpur'),
(319, 13, 'Jhabua'),
(320, 13, 'Katni'),
(321, 13, 'Khandwa'),
(322, 13, 'Khargone'),
(323, 13, 'Maihar'),
(324, 13, 'Mandla'),
(325, 13, 'Mandsaur'),
(326, 13, 'Mauganj'),
(327, 13, 'Morena'),
(328, 13, 'Narmadapuram'),
(329, 13, 'Narsinghpur'),
(330, 13, 'Neemuch'),
(331, 13, 'Niwari'),
(332, 13, 'Pandhurna'),
(333, 13, 'Panna'),
(334, 13, 'Raisen'),
(335, 13, 'Rajgarh'),
(336, 13, 'Ratlam'),
(337, 13, 'Rewa'),
(338, 13, 'Sagar'),
(339, 13, 'Satna'),
(340, 13, 'Sehore'),
(341, 13, 'Seoni'),
(342, 13, 'Shahdol'),
(343, 13, 'Shajapur'),
(344, 13, 'Sheopur'),
(345, 13, 'Shivpuri'),
(346, 13, 'Sidhi'),
(347, 13, 'Singrauli'),
(348, 13, 'Tikamgarh'),
(349, 13, 'Ujjain'),
(350, 13, 'Umaria'),
(351, 13, 'Vidisha'),
(352, 14, 'Ahmednagar'),
(353, 14, 'Akola'),
(354, 14, 'Amravati'),
(355, 14, 'Beed'),
(356, 14, 'Bhandara'),
(357, 14, 'Buldhana'),
(358, 14, 'Chandrapur'),
(359, 14, 'Chhatrapati Sambhajinagar'),
(360, 14, 'Dharashiv'),
(361, 14, 'Dhule'),
(362, 14, 'Gadchiroli'),
(363, 14, 'Gondia'),
(364, 14, 'Hingoli'),
(365, 14, 'Jalgaon'),
(366, 14, 'Jalna'),
(367, 14, 'Kolhapur'),
(368, 14, 'Latur'),
(369, 14, 'Mumbai City'),
(370, 14, 'Mumbai Suburban'),
(371, 14, 'Nagpur'),
(372, 14, 'Nanded'),
(373, 14, 'Nandurbar'),
(374, 14, 'Nashik'),
(375, 14, 'Palghar'),
(376, 14, 'Parbhani'),
(377, 14, 'Pune'),
(378, 14, 'Raigad'),
(379, 14, 'Ratnagiri'),
(380, 14, 'Sangli'),
(381, 14, 'Satara'),
(382, 14, 'Sindhudurg'),
(383, 14, 'Solapur'),
(384, 14, 'Thane'),
(385, 14, 'Wardha'),
(386, 14, 'Washim'),
(387, 14, 'Yavatmal'),
(388, 15, 'Bishnupur'),
(389, 15, 'Chandel'),
(390, 15, 'Churachandpur'),
(391, 15, 'Imphal East'),
(392, 15, 'Imphal West'),
(393, 15, 'Jiribam'),
(394, 15, 'Kakching'),
(395, 15, 'Kamjong'),
(396, 15, 'Kangpokpi'),
(397, 15, 'Noney'),
(398, 15, 'Pherzawl'),
(399, 15, 'Senapati'),
(400, 15, 'Tamenglong')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(401, 15, 'Tengnoupal'),
(402, 15, 'Thoubal'),
(403, 15, 'Ukhrul'),
(404, 16, 'East Garo Hills'),
(405, 16, 'East Jaintia Hills'),
(406, 16, 'East Khasi Hills'),
(407, 16, 'Eastern West Khasi Hills'),
(408, 16, 'North Garo Hills'),
(409, 16, 'Ri Bhoi'),
(410, 16, 'South Garo Hills'),
(411, 16, 'South West Garo Hills'),
(412, 16, 'South West Khasi Hills'),
(413, 16, 'West Garo Hills'),
(414, 16, 'West Jaintia Hills'),
(415, 16, 'West Khasi Hills'),
(416, 17, 'Aizawl'),
(417, 17, 'Champhai'),
(418, 17, 'Hnahthial'),
(419, 17, 'Khawzawl'),
(420, 17, 'Kolasib'),
(421, 17, 'Lawngtlai'),
(422, 17, 'Lunglei'),
(423, 17, 'Mamit'),
(424, 17, 'Saitual'),
(425, 17, 'Serchhip'),
(426, 17, 'Siaha'),
(427, 18, 'Chumoukedima'),
(428, 18, 'Dimapur'),
(429, 18, 'Kiphire'),
(430, 18, 'Kohima'),
(431, 18, 'Longleng'),
(432, 18, 'Mokokchung'),
(433, 18, 'Mon'),
(434, 18, 'Niuland'),
(435, 18, 'Noklak'),
(436, 18, 'Peren'),
(437, 18, 'Phek'),
(438, 18, 'Shamator'),
(439, 18, 'Tseminyu'),
(440, 18, 'Tuensang'),
(441, 18, 'Wokha'),
(442, 18, 'Zunheboto'),
(443, 19, 'Angul'),
(444, 19, 'Balangir'),
(445, 19, 'Balasore'),
(446, 19, 'Bargarh'),
(447, 19, 'Bhadrak'),
(448, 19, 'Boudh'),
(449, 19, 'Cuttack'),
(450, 19, 'Deogarh'),
(451, 19, 'Dhenkanal'),
(452, 19, 'Gajapati'),
(453, 19, 'Ganjam'),
(454, 19, 'Jagatsinghpur'),
(455, 19, 'Jajpur'),
(456, 19, 'Jharsuguda'),
(457, 19, 'Kalahandi'),
(458, 19, 'Kandhamal'),
(459, 19, 'Kendrapara'),
(460, 19, 'Kendujhar'),
(461, 19, 'Khordha'),
(462, 19, 'Koraput'),
(463, 19, 'Malkangiri'),
(464, 19, 'Mayurbhanj'),
(465, 19, 'Nabarangpur'),
(466, 19, 'Nayagarh'),
(467, 19, 'Nuapada'),
(468, 19, 'Puri'),
(469, 19, 'Rayagada'),
(470, 19, 'Sambalpur'),
(471, 19, 'Subarnapur'),
(472, 19, 'Sundargarh'),
(473, 20, 'Amritsar'),
(474, 20, 'Barnala'),
(475, 20, 'Bathinda'),
(476, 20, 'Faridkot'),
(477, 20, 'Fatehgarh Sahib'),
(478, 20, 'Fazilka'),
(479, 20, 'Ferozepur'),
(480, 20, 'Gurdaspur'),
(481, 20, 'Hoshiarpur'),
(482, 20, 'Jalandhar'),
(483, 20, 'Kapurthala'),
(484, 20, 'Ludhiana'),
(485, 20, 'Malerkotla'),
(486, 20, 'Mansa'),
(487, 20, 'Moga'),
(488, 20, 'Muktsar'),
(489, 20, 'Pathankot'),
(490, 20, 'Patiala'),
(491, 20, 'Rupnagar'),
(492, 20, 'Sahibzada Ajit Singh Nagar'),
(493, 20, 'Sangrur'),
(494, 20, 'Shahid Bhagat Singh Nagar'),
(495, 20, 'Tarn Taran'),
(496, 21, 'Ajmer'),
(497, 21, 'Alwar'),
(498, 21, 'Anupgarh'),
(499, 21, 'Balotra'),
(500, 21, 'Banswara')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(501, 21, 'Baran'),
(502, 21, 'Barmer'),
(503, 21, 'Beawar'),
(504, 21, 'Bharatpur'),
(505, 21, 'Bhilwara'),
(506, 21, 'Bikaner'),
(507, 21, 'Bundi'),
(508, 21, 'Chittorgarh'),
(509, 21, 'Churu'),
(510, 21, 'Dausa'),
(511, 21, 'Deeg'),
(512, 21, 'Dholpur'),
(513, 21, 'Didwana-Kuchaman'),
(514, 21, 'Dudu'),
(515, 21, 'Dungarpur'),
(516, 21, 'Gangapur City'),
(517, 21, 'Hanumangarh'),
(518, 21, 'Jaipur'),
(519, 21, 'Jaipur Rural'),
(520, 21, 'Jaisalmer'),
(521, 21, 'Jalore'),
(522, 21, 'Jhalawar'),
(523, 21, 'Jhunjhunu'),
(524, 21, 'Jodhpur'),
(525, 21, 'Jodhpur Rural'),
(526, 21, 'Karauli'),
(527, 21, 'Kekri'),
(528, 21, 'Khairthal-Tijara'),
(529, 21, 'Kota'),
(530, 21, 'Kotputli-Behror'),
(531, 21, 'Nagaur'),
(532, 21, 'Neem Ka Thana'),
(533, 21, 'Pali'),
(534, 21, 'Phalodi'),
(535, 21, 'Pratapgarh'),
(536, 21, 'Rajsamand'),
(537, 21, 'Salumbar'),
(538, 21, 'Sanchore'),
(539, 21, 'Sawai Madhopur'),
(540, 21, 'Shahpura'),
(541, 21, 'Sikar'),
(542, 21, 'Sirohi'),
(543, 21, 'Sri Ganganagar'),
(544, 21, 'Tonk'),
(545, 21, 'Udaipur'),
(546, 22, 'Gangtok'),
(547, 22, 'Gyalshing'),
(548, 22, 'Mangan'),
(549, 22, 'Namchi'),
(550, 22, 'Pakyong'),
(551, 22, 'Soreng'),
(552, 23, 'Ariyalur'),
(553, 23, 'Chengalpattu'),
(554, 23, 'Chennai'),
(555, 23, 'Coimbatore'),
(556, 23, 'Cuddalore'),
(557, 23, 'Dharmapuri'),
(558, 23, 'Dindigul'),
(559, 23, 'Erode'),
(560, 23, 'Kallakurichi'),
(561, 23, 'Kanchipuram'),
(562, 23, 'Kanyakumari'),
(563, 23, 'Karur'),
(564, 23, 'Krishnagiri'),
(565, 23, 'Madurai'),
(566, 23, 'Mayiladuthurai'),
(567, 23, 'Nagapattinam'),
(568, 23, 'Namakkal'),
(569, 23, 'Nilgiris'),
(570, 23, 'Perambalur'),
(571, 23, 'Pudukkottai'),
(572, 23, 'Ramanathapuram'),
(573, 23, 'Ranipet'),
(574, 23, 'Salem'),
(575, 23, 'Sivaganga'),
(576, 23, 'Tenkasi'),
(577, 23, 'Thanjavur'),
(578, 23, 'Theni'),
(579, 23, 'Thoothukudi'),
(580, 23, 'Tiruchirappalli'),
(581, 23, 'Tirunelveli'),
(582, 23, 'Tirupathur'),
(583, 23, 'Tiruppur'),
(584, 23, 'Tiruvallur'),
(585, 23, 'Tiruvannamalai'),
(586, 23, 'Tiruvarur'),
(587, 23, 'Vellore'),
(588, 23, 'Viluppuram'),
(589, 23, 'Virudhunagar'),
(590, 24, 'Adilabad'),
(591, 24, 'Bhadradri Kothagudem'),
(592, 24, 'Hanamkonda'),
(593, 24, 'Hyderabad'),
(594, 24, 'Jagtial'),
(595, 24, 'Jangaon'),
(596, 24, 'Jayashankar Bhupalpally'),
(597, 24, 'Jogulamba Gadwal'),
(598, 24, 'Kamareddy'),
(599, 24, 'Karimnagar'),
(600, 24, 'Khammam')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(601, 24, 'Kumuram Bheem Asifabad'),
(602, 24, 'Mahabubabad'),
(603, 24, 'Mahabubnagar'),
(604, 24, 'Mancherial'),
(605, 24, 'Medak'),
(606, 24, 'Medchal-Malkajgiri'),
(607, 24, 'Mulugu'),
(608, 24, 'Nagarkurnool'),
(609, 24, 'Nalgonda'),
(610, 24, 'Narayanpet'),
(611, 24, 'Nirmal'),
(612, 24, 'Nizamabad'),
(613, 24, 'Peddapalli'),
(614, 24, 'Rajanna Sircilla'),
(615, 24, 'Ranga Reddy'),
(616, 24, 'Sangareddy'),
(617, 24, 'Siddipet'),
(618, 24, 'Suryapet'),
(619, 24, 'Vikarabad'),
(620, 24, 'Wanaparthy'),
(621, 24, 'Warangal'),
(622, 24, 'Yadadri Bhuvanagiri'),
(623, 25, 'Dhalai'),
(624, 25, 'Gomati'),
(625, 25, 'Khowai'),
(626, 25, 'North Tripura'),
(627, 25, 'Sepahijala'),
(628, 25, 'South Tripura'),
(629, 25, 'Unakoti'),
(630, 25, 'West Tripura'),
(631, 26, 'Agra'),
(632, 26, 'Aligarh'),
(633, 26, 'Ambedkar Nagar'),
(634, 26, 'Amethi'),
(635, 26, 'Amroha'),
(636, 26, 'Auraiya'),
(637, 26, 'Ayodhya'),
(638, 26, 'Azamgarh'),
(639, 26, 'Baghpat'),
(640, 26, 'Bahraich'),
(641, 26, 'Ballia'),
(642, 26, 'Balrampur'),
(643, 26, 'Banda'),
(644, 26, 'Barabanki'),
(645, 26, 'Bareilly'),
(646, 26, 'Basti'),
(647, 26, 'Bhadohi'),
(648, 26, 'Bijnor'),
(649, 26, 'Budaun'),
(650, 26, 'Bulandshahr'),
(651, 26, 'Chandauli'),
(652, 26, 'Chitrakoot'),
(653, 26, 'Deoria'),
(654, 26, 'Etah'),
(655, 26, 'Etawah'),
(656, 26, 'Farrukhabad'),
(657, 26, 'Fatehpur'),
(658, 26, 'Firozabad'),
(659, 26, 'Gautam Buddha Nagar'),
(660, 26, 'Ghaziabad'),
(661, 26, 'Ghazipur'),
(662, 26, 'Gonda'),
(663, 26, 'Gorakhpur'),
(664, 26, 'Hamirpur'),
(665, 26, 'Hapur'),
(666, 26, 'Hardoi'),
(667, 26, 'Hathras'),
(668, 26, 'Jalaun'),
(669, 26, 'Jaunpur'),
(670, 26, 'Jhansi'),
(671, 26, 'Kannauj'),
(672, 26, 'Kanpur Dehat'),
(673, 26, 'Kanpur Nagar'),
(674, 26, 'Kasganj'),
(675, 26, 'Kaushambi'),
(676, 26, 'Kheri'),
(677, 26, 'Kushinagar'),
(678, 26, 'Lalitpur'),
(679, 26, 'Lucknow'),
(680, 26, 'Maharajganj'),
(681, 26, 'Mahoba'),
(682, 26, 'Mainpuri'),
(683, 26, 'Mathura'),
(684, 26, 'Mau'),
(685, 26, 'Meerut'),
(686, 26, 'Mirzapur'),
(687, 26, 'Moradabad'),
(688, 26, 'Muzaffarnagar'),
(689, 26, 'Pilibhit'),
(690, 26, 'Pratapgarh'),
(691, 26, 'Prayagraj'),
(692, 26, 'Raebareli'),
(693, 26, 'Rampur'),
(694, 26, 'Saharanpur'),
(695, 26, 'Sambhal'),
(696, 26, 'Sant Kabir Nagar'),
(697, 26, 'Shahjahanpur'),
(698, 26, 'Shamli'),
(699, 26, 'Shravasti'),
(700, 26, 'Siddharthnagar')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, state_id, district_name) VALUES
(701, 26, 'Sitapur'),
(702, 26, 'Sonbhadra'),
(703, 26, 'Sultanpur'),
(704, 26, 'Unnao'),
(705, 26, 'Varanasi'),
(706, 27, 'Almora'),
(707, 27, 'Bageshwar'),
(708, 27, 'Chamoli'),
(709, 27, 'Champawat'),
(710, 27, 'Dehradun'),
(711, 27, 'Haridwar'),
(712, 27, 'Nainital'),
(713, 27, 'Pauri Garhwal'),
(714, 27, 'Pithoragarh'),
(715, 27, 'Rudraprayag'),
(716, 27, 'Tehri Garhwal'),
(717, 27, 'Udham Singh Nagar'),
(718, 27, 'Uttarkashi'),
(719, 28, 'Alipurduar'),
(720, 28, 'Bankura'),
(721, 28, 'Birbhum'),
(722, 28, 'Cooch Behar'),
(723, 28, 'Dakshin Dinajpur'),
(724, 28, 'Darjeeling'),
(725, 28, 'Hooghly'),
(726, 28, 'Howrah'),
(727, 28, 'Jalpaiguri'),
(728, 28, 'Jhargram'),
(729, 28, 'Kalimpong'),
(730, 28, 'Kolkata'),
(731, 28, 'Malda'),
(732, 28, 'Murshidabad'),
(733, 28, 'Nadia'),
(734, 28, 'North 24 Parganas'),
(735, 28, 'Paschim Bardhaman'),
(736, 28, 'Paschim Medinipur'),
(737, 28, 'Purba Bardhaman'),
(738, 28, 'Purba Medinipur'),
(739, 28, 'Purulia'),
(740, 28, 'South 24 Parganas'),
(741, 28, 'Uttar Dinajpur'),
(742, 29, 'Nicobar'),
(743, 29, 'North and Middle Andaman'),
(744, 29, 'South Andaman'),
(745, 30, 'Chandigarh'),
(746, 31, 'Dadra and Nagar Haveli'),
(747, 31, 'Daman'),
(748, 31, 'Diu'),
(749, 32, 'Central Delhi'),
(750, 32, 'East Delhi'),
(751, 32, 'New Delhi'),
(752, 32, 'North Delhi'),
(753, 32, 'North East Delhi'),
(754, 32, 'North West Delhi'),
(755, 32, 'Shahdara'),
(756, 32, 'South Delhi'),
(757, 32, 'South East Delhi'),
(758, 32, 'South West Delhi'),
(759, 32, 'West Delhi'),
(760, 33, 'Anantnag'),
(761, 33, 'Bandipora'),
(762, 33, 'Baramulla'),
(763, 33, 'Budgam'),
(764, 33, 'Doda'),
(765, 33, 'Ganderbal'),
(766, 33, 'Jammu'),
(767, 33, 'Kathua'),
(768, 33, 'Kishtwar'),
(769, 33, 'Kulgam'),
(770, 33, 'Kupwara'),
(771, 33, 'Poonch'),
(772, 33, 'Pulwama'),
(773, 33, 'Rajouri'),
(774, 33, 'Ramban'),
(775, 33, 'Reasi'),
(776, 33, 'Samba'),
(777, 33, 'Shopian'),
(778, 33, 'Srinagar'),
(779, 33, 'Udhampur'),
(780, 34, 'Kargil'),
(781, 34, 'Leh'),
(782, 35, 'Lakshadweep'),
(783, 36, 'Karaikal'),
(784, 36, 'Mahe'),
(785, 36, 'Puducherry'),
(786, 36, 'Yanam')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- 4. CITIES & LOCALITIES (Key Metropolitan Hubs, Tier-1/2/3 Cities, and Karnataka Hubs)
-- =============================================================================

INSERT INTO public.cities (id, district_id, city_name)
SELECT 1, d.id, 'Mysuru' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Mysuru'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 2, d.id, 'Bengaluru' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Bengaluru Urban'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 3, d.id, 'Mandya' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Mandya'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 4, d.id, 'Hassan' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Hassan'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 5, d.id, 'Mangaluru' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Dakshina Kannada'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 6, d.id, 'Belagavi' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Belagavi'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 7, d.id, 'Hubballi-Dharwad' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Dharwad'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 8, d.id, 'Hosapete' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Vijayanagara'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 9, d.id, 'Ramanagara' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Karnataka' AND d.district_name = 'Ramanagara'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 10, d.id, 'Mumbai' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Maharashtra' AND d.district_name = 'Mumbai City'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 11, d.id, 'Pune' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Maharashtra' AND d.district_name = 'Pune'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 12, d.id, 'Hyderabad' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Telangana' AND d.district_name = 'Hyderabad'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 13, d.id, 'Chennai' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Tamil Nadu' AND d.district_name = 'Chennai'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 14, d.id, 'New Delhi' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Delhi' AND d.district_name = 'New Delhi'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 15, d.id, 'Kolkata' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'West Bengal' AND d.district_name = 'Kolkata'
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, district_id, city_name)
SELECT 16, d.id, 'Ahmedabad' FROM public.districts d JOIN public.states s ON d.state_id = s.id WHERE s.state_name = 'Gujarat' AND d.district_name = 'Ahmedabad'
ON CONFLICT (id) DO NOTHING;

-- Localities
INSERT INTO public.localities (id, city_id, locality_name, pincode) VALUES
-- Mysuru Localities
(1, 1, 'Vijayanagar 2nd Stage', '570017'),
(2, 1, 'Kuvempunagar', '570023'),
(3, 1, 'Jayalakshmipuram', '570012'),
(4, 1, 'Gokulam 3rd Stage', '570002'),
(5, 1, 'Saraswathipuram', '570009'),
-- Bengaluru Localities
(6, 2, 'Indiranagar 100ft Road', '560038'),
(7, 2, 'Whitefield IT Corridor', '560066'),
(8, 2, 'Koramangala 4th Block', '560034'),
(9, 2, 'HSR Layout Sector 1', '560102'),
(10, 2, 'Jayanagar 4th Block', '560011'),
-- Mandya Localities
(11, 3, 'Maddur Highway', '571428'),
(12, 3, 'Sugar Town', '571401'),
-- Hassan Localities
(13, 4, 'Sakleshpur Estate Belt', '573134'),
(14, 4, 'Shanthi Nagar', '573201'),
-- Mangaluru Localities
(15, 5, 'Kadri Hills', '575002'),
(16, 5, 'Bejai Main Road', '575004'),
-- Mumbai Localities
(17, 10, 'Bandra West (Pali Hill)', '400050'),
(18, 10, 'Nariman Point', '400021'),
-- Pune Localities
(19, 11, 'Koregaon Park', '411001'),
(20, 11, 'Hinjawadi IT Park Phase 1', '411057'),
-- Hyderabad Localities
(21, 12, 'HITEC City', '500081'),
(22, 12, 'Jubilee Hills Check Post', '500033'),
-- Chennai Localities
(23, 13, 'OMR IT Expressway', '600096'),
(24, 13, 'Anna Nagar West', '600040'),
-- New Delhi Localities
(25, 14, 'Connaught Place Outer Circle', '110001'),
(26, 14, 'Vasant Kunj Sector B', '110070')
ON CONFLICT (id) DO NOTHING;

-- Reset Sequences for primary keys
SELECT setval('public.countries_id_seq', (SELECT MAX(id) FROM public.countries));
SELECT setval('public.states_id_seq', (SELECT MAX(id) FROM public.states));
SELECT setval('public.districts_id_seq', (SELECT MAX(id) FROM public.districts));
SELECT setval('public.cities_id_seq', (SELECT MAX(id) FROM public.cities));
SELECT setval('public.localities_id_seq', (SELECT MAX(id) FROM public.localities));


-- =============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES FOR REAL ESTATE MANAGEMENT SYSTEM
-- =============================================================================

-- Enable Row Level Security on all tables
ALTER TABLE public.countries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.states ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.districts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.localities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.properties ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_features ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inquiries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- -----------------------------------------------------------------------------
-- 1. GEOGRAPHY TABLES (Public Read, Admin Write)
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public can view countries" ON public.countries;
CREATE POLICY "Public can view countries" ON public.countries FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can modify countries" ON public.countries;
CREATE POLICY "Admin can modify countries" ON public.countries FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Public can view states" ON public.states;
CREATE POLICY "Public can view states" ON public.states FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can modify states" ON public.states;
CREATE POLICY "Admin can modify states" ON public.states FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Public can view districts" ON public.districts;
CREATE POLICY "Public can view districts" ON public.districts FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can modify districts" ON public.districts;
CREATE POLICY "Admin can modify districts" ON public.districts FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Public can view cities" ON public.cities;
CREATE POLICY "Public can view cities" ON public.cities FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can modify cities" ON public.cities;
CREATE POLICY "Admin can modify cities" ON public.cities FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Public can view localities" ON public.localities;
CREATE POLICY "Public can view localities" ON public.localities FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can modify localities" ON public.localities;
CREATE POLICY "Admin can modify localities" ON public.localities FOR ALL USING (public.is_admin());

-- -----------------------------------------------------------------------------
-- 2. PROFILES TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public can view user profiles" ON public.profiles;
CREATE POLICY "Public can view user profiles" 
ON public.profiles FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile" 
ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id OR public.is_admin());

DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile" 
ON public.profiles FOR UPDATE USING (auth.uid() = id OR public.is_admin());

DROP POLICY IF EXISTS "Users can delete their own profile" ON public.profiles;
CREATE POLICY "Users can delete their own profile" 
ON public.profiles FOR DELETE USING (auth.uid() = id OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 3. PROPERTIES TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Anyone can view active property listings" ON public.properties;
CREATE POLICY "Anyone can view active property listings" 
ON public.properties FOR SELECT 
USING (
    property_status IN ('AVAILABLE', 'UNDER_OFFER') 
    OR auth.uid() = owner_id 
    OR public.is_admin()
);

DROP POLICY IF EXISTS "Authenticated users can create properties" ON public.properties;
CREATE POLICY "Authenticated users can create properties" 
ON public.properties FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = owner_id OR public.is_admin());

DROP POLICY IF EXISTS "Owners can update their own properties" ON public.properties;
CREATE POLICY "Owners can update their own properties" 
ON public.properties FOR UPDATE 
TO authenticated 
USING (auth.uid() = owner_id OR public.is_admin());

DROP POLICY IF EXISTS "Owners can delete their own properties" ON public.properties;
CREATE POLICY "Owners can delete their own properties" 
ON public.properties FOR DELETE 
TO authenticated 
USING (auth.uid() = owner_id OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 4. PROPERTY IMAGES & FEATURES
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public can view property images" ON public.property_images;
CREATE POLICY "Public can view property images" 
ON public.property_images FOR SELECT USING (true);

DROP POLICY IF EXISTS "Property owners can add images" ON public.property_images;
CREATE POLICY "Property owners can add images" 
ON public.property_images FOR INSERT 
TO authenticated 
WITH CHECK (
    EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND (owner_id = auth.uid() OR public.is_admin()))
);

DROP POLICY IF EXISTS "Property owners can update images" ON public.property_images;
CREATE POLICY "Property owners can update images" 
ON public.property_images FOR UPDATE 
TO authenticated 
USING (
    EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND (owner_id = auth.uid() OR public.is_admin()))
);

DROP POLICY IF EXISTS "Property owners can delete images" ON public.property_images;
CREATE POLICY "Property owners can delete images" 
ON public.property_images FOR DELETE 
TO authenticated 
USING (
    EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND (owner_id = auth.uid() OR public.is_admin()))
);

DROP POLICY IF EXISTS "Public can view property features" ON public.property_features;
CREATE POLICY "Public can view property features" 
ON public.property_features FOR SELECT USING (true);

DROP POLICY IF EXISTS "Property owners can manage features" ON public.property_features;
CREATE POLICY "Property owners can manage features" 
ON public.property_features FOR ALL 
TO authenticated 
USING (
    EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND (owner_id = auth.uid() OR public.is_admin()))
);

-- -----------------------------------------------------------------------------
-- 5. FAVORITES TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Users can view their own favorites" ON public.favorites;
CREATE POLICY "Users can view their own favorites" 
ON public.favorites FOR SELECT 
TO authenticated 
USING (auth.uid() = user_id OR public.is_admin());

DROP POLICY IF EXISTS "Users can add favorites" ON public.favorites;
CREATE POLICY "Users can add favorites" 
ON public.favorites FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can remove favorites" ON public.favorites;
CREATE POLICY "Users can remove favorites" 
ON public.favorites FOR DELETE 
TO authenticated 
USING (auth.uid() = user_id OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 6. INQUIRIES TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Property owners and buyers can view inquiries" ON public.inquiries;
CREATE POLICY "Property owners and buyers can view inquiries" 
ON public.inquiries FOR SELECT 
USING (
    buyer_id = auth.uid() 
    OR EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND owner_id = auth.uid()) 
    OR public.is_admin()
);

DROP POLICY IF EXISTS "Anyone can create inquiries" ON public.inquiries;
CREATE POLICY "Anyone can create inquiries" 
ON public.inquiries FOR INSERT 
WITH CHECK (true);

DROP POLICY IF EXISTS "Property owners can update inquiry status" ON public.inquiries;
CREATE POLICY "Property owners can update inquiry status" 
ON public.inquiries FOR UPDATE 
TO authenticated 
USING (
    EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND owner_id = auth.uid()) 
    OR public.is_admin()
);

-- -----------------------------------------------------------------------------
-- 7. APPOINTMENTS TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Users and property owners can view appointments" ON public.appointments;
CREATE POLICY "Users and property owners can view appointments" 
ON public.appointments FOR SELECT 
TO authenticated 
USING (
    user_id = auth.uid() 
    OR EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND owner_id = auth.uid()) 
    OR public.is_admin()
);

DROP POLICY IF EXISTS "Users can book appointments" ON public.appointments;
CREATE POLICY "Users can book appointments" 
ON public.appointments FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users and owners can update appointments" ON public.appointments;
CREATE POLICY "Users and owners can update appointments" 
ON public.appointments FOR UPDATE 
TO authenticated 
USING (
    user_id = auth.uid() 
    OR EXISTS (SELECT 1 FROM public.properties WHERE id = property_id AND owner_id = auth.uid()) 
    OR public.is_admin()
);

-- -----------------------------------------------------------------------------
-- 8. TRANSACTIONS TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Transaction participants can view transactions" ON public.transactions;
CREATE POLICY "Transaction participants can view transactions" 
ON public.transactions FOR SELECT 
TO authenticated 
USING (buyer_id = auth.uid() OR seller_id = auth.uid() OR public.is_admin());

DROP POLICY IF EXISTS "Only admins can record or alter transactions" ON public.transactions;
CREATE POLICY "Only admins can record or alter transactions" 
ON public.transactions FOR ALL 
TO authenticated 
USING (public.is_admin());

-- -----------------------------------------------------------------------------
-- 9. REVIEWS TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public can view reviews" ON public.reviews;
CREATE POLICY "Public can view reviews" 
ON public.reviews FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can submit reviews" ON public.reviews;
CREATE POLICY "Users can submit reviews" 
ON public.reviews FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = reviewer_id);

DROP POLICY IF EXISTS "Users can edit or delete their own reviews" ON public.reviews;
CREATE POLICY "Users can edit or delete their own reviews" 
ON public.reviews FOR UPDATE 
TO authenticated 
USING (auth.uid() = reviewer_id OR public.is_admin());

DROP POLICY IF EXISTS "Users can delete their own reviews" ON public.reviews;
CREATE POLICY "Users can delete their own reviews" 
ON public.reviews FOR DELETE 
TO authenticated 
USING (auth.uid() = reviewer_id OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 10. AGENTS TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public can view agents" ON public.agents;
CREATE POLICY "Public can view agents" 
ON public.agents FOR SELECT USING (true);

DROP POLICY IF EXISTS "Agents can manage their own agent profile" ON public.agents;
CREATE POLICY "Agents can manage their own agent profile" 
ON public.agents FOR ALL 
TO authenticated 
USING (profile_id = auth.uid() OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 11. NOTIFICATIONS TABLE
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "Users can view their own notifications" ON public.notifications;
CREATE POLICY "Users can view their own notifications" 
ON public.notifications FOR SELECT 
TO authenticated 
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can mark notifications as read" ON public.notifications;
CREATE POLICY "Users can mark notifications as read" 
ON public.notifications FOR UPDATE 
TO authenticated 
USING (auth.uid() = user_id);


-- =============================================================================
-- SUPABASE STORAGE BUCKET & SECURITY POLICIES FOR PROPERTY IMAGES
-- =============================================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'property-images',
    'property-images',
    true,
    10485760, -- 10MB per file
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/avif', 'image/jpg']
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 10485760,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/avif', 'image/jpg'];

-- Enable Storage RLS Policies
DROP POLICY IF EXISTS "Public can view property images" ON storage.objects;
CREATE POLICY "Public can view property images"
ON storage.objects FOR SELECT
USING (bucket_id = 'property-images');

DROP POLICY IF EXISTS "Authenticated users can upload property images" ON storage.objects;
CREATE POLICY "Authenticated users can upload property images"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'property-images');

DROP POLICY IF EXISTS "Users can update their own property images" ON storage.objects;
CREATE POLICY "Users can update their own property images"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'property-images' 
    AND (auth.uid() = owner OR auth.uid()::text = (storage.foldername(name))[1])
);

DROP POLICY IF EXISTS "Users can delete their own property images" ON storage.objects;
CREATE POLICY "Users can delete their own property images"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'property-images' 
    AND (auth.uid() = owner OR auth.uid()::text = (storage.foldername(name))[1])
);

