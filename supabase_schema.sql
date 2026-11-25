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
