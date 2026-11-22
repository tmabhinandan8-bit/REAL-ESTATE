-- =============================================================================
-- PREMIUM 3D REAL ESTATE MANAGEMENT PLATFORM - SUPABASE SCHEMA MIGRATION
-- Database: PostgreSQL 14+ / Supabase
-- Integrates with existing database tables while adding full 3D, Virtual Tour,
-- Video Tours, Floor Plans, Plot Layouts, Inquiries, Favorites, and Agents.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 1. EXTEND EXISTING PROPERTIES TABLE (IF NOT ALREADY PRESENT)
-- =============================================================================
ALTER TABLE IF EXISTS public.properties 
    ADD COLUMN IF NOT EXISTS has_3d_model BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_virtual_tour BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_video_tour BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS primary_model_url TEXT,
    ADD COLUMN IF NOT EXISTS primary_video_url TEXT,
    ADD COLUMN IF NOT EXISTS virtual_tour_url TEXT,
    ADD COLUMN IF NOT EXISTS floor_plan_url TEXT,
    ADD COLUMN IF NOT EXISTS facing_direction VARCHAR(30) DEFAULT 'NORTH_EAST',
    ADD COLUMN IF NOT EXISTS road_width_ft INT DEFAULT 40,
    ADD COLUMN IF NOT EXISTS rera_approved BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS rera_number VARCHAR(100) DEFAULT 'RERA-KAR-2026-9842';

-- =============================================================================
-- 2. 3D MODELS TABLE (GLB / GLTF / OBJ / FBX)
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_models (
    model_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    model_title VARCHAR(200) NOT NULL DEFAULT 'Architectural 3D Model',
    model_url TEXT NOT NULL,
    model_format VARCHAR(20) NOT NULL DEFAULT 'GLB' CHECK (model_format IN ('GLB', 'GLTF', 'OBJ', 'FBX', 'PROCEDURAL')),
    file_size_bytes BIGINT DEFAULT 0,
    poly_count INT DEFAULT 45000,
    camera_fov NUMERIC(5,2) DEFAULT 45.0,
    camera_pos_x NUMERIC(8,2) DEFAULT 18.0,
    camera_pos_y NUMERIC(8,2) DEFAULT 14.0,
    camera_pos_z NUMERIC(8,2) DEFAULT 22.0,
    is_primary BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_models_pid ON public.property_models(property_id);

-- =============================================================================
-- 3. 360° VIRTUAL TOURS & ROOM PANORAMAS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_virtual_tours (
    tour_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    room_code VARCHAR(50) NOT NULL DEFAULT 'LIVING_ROOM',
    room_label VARCHAR(100) NOT NULL,
    panorama_image_url TEXT NOT NULL,
    initial_yaw NUMERIC(6,2) DEFAULT 0.0,
    initial_pitch NUMERIC(6,2) DEFAULT 0.0,
    hotspots_json JSONB DEFAULT '[]'::jsonb,
    sort_order INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_tours_pid ON public.property_virtual_tours(property_id);

-- =============================================================================
-- 4. PROPERTY VIDEOS TABLE (WALKTHROUGHS, DRONE FOOTAGE, ETC.)
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_videos (
    video_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    video_type VARCHAR(50) NOT NULL DEFAULT 'WALKTHROUGH' 
        CHECK (video_type IN ('WALKTHROUGH', 'DRONE_AERIAL', 'INTERIOR', 'EXTERIOR', 'CONSTRUCTION_PROGRESS', 'PLOT_LAYOUT')),
    video_title VARCHAR(200) NOT NULL,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    duration_seconds INT DEFAULT 90,
    sort_order INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_videos_pid ON public.property_videos(property_id);

-- =============================================================================
-- 5. PROPERTY FLOOR PLANS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_floor_plans (
    plan_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    floor_name VARCHAR(100) NOT NULL DEFAULT 'Ground Floor Plan',
    floor_level INT DEFAULT 0,
    plan_image_url TEXT NOT NULL,
    built_up_area_sqft NUMERIC(10,2) DEFAULT 0.0,
    carpet_area_sqft NUMERIC(10,2) DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_floor_plans_pid ON public.property_floor_plans(property_id);

-- =============================================================================
-- 6. BUILDING PLOTS INTERACTIVE MASTER PLAN TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_plots (
    plot_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    layout_name VARCHAR(150) NOT NULL DEFAULT 'Gated Layout Phase 1',
    plot_number VARCHAR(50) NOT NULL,
    dimensions VARCHAR(50) NOT NULL DEFAULT '30x50',
    area_sqft NUMERIC(10,2) NOT NULL DEFAULT 1500.0,
    facing_direction VARCHAR(30) NOT NULL DEFAULT 'EAST',
    road_width_ft INT DEFAULT 40,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD')),
    price NUMERIC(14,2) NOT NULL DEFAULT 4500000.0,
    has_water_supply BOOLEAN DEFAULT TRUE,
    has_underground_electricity BOOLEAN DEFAULT TRUE,
    approval_authority VARCHAR(100) DEFAULT 'BDA / MUDA Approved',
    corner_plot BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_plots_pid ON public.property_plots(property_id);
CREATE INDEX IF NOT EXISTS idx_property_plots_status ON public.property_plots(status);

-- =============================================================================
-- 7. PROPERTY CUSTOMER INQUIRIES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.property_inquiries (
    inquiry_id SERIAL PRIMARY KEY,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    user_id INT,
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(200) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    inquiry_type VARCHAR(50) NOT NULL DEFAULT 'BUY' 
        CHECK (inquiry_type IN ('BUY', 'RENT', 'VISIT', '3D_TOUR', 'PRICE_QUOTE', 'GENERAL')),
    status VARCHAR(30) NOT NULL DEFAULT 'NEW' 
        CHECK (status IN ('NEW', 'CONTACTED', 'APPOINTMENT_SCHEDULED', 'RESOLVED')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_property_inquiries_pid ON public.property_inquiries(property_id);

-- =============================================================================
-- 8. CUSTOMER FAVORITES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.favorites (
    favorite_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    property_id INT REFERENCES public.properties(property_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_favorite UNIQUE(user_id, property_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_user_id ON public.favorites(user_id);

-- =============================================================================
-- 9. TRUSTED REAL ESTATE AGENTS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.agents (
    agent_id SERIAL PRIMARY KEY,
    user_id INT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    agency_name VARCHAR(150) NOT NULL DEFAULT 'Royal Heritage Realty Group',
    rera_registration_no VARCHAR(100) NOT NULL,
    experience_years INT DEFAULT 8,
    specialization VARCHAR(100) DEFAULT 'Luxury Villas & Farmhouses',
    avatar_url TEXT NOT NULL,
    rating NUMERIC(3,2) DEFAULT 4.9,
    active_listings_count INT DEFAULT 12,
    is_verified BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 10. SEED SAMPLE TRUSTED AGENTS
-- =============================================================================
INSERT INTO public.agents (full_name, email, phone, agency_name, rera_registration_no, experience_years, specialization, avatar_url, rating, active_listings_count, is_verified)
VALUES
('Vikramaditya Singhania', 'vikram.singhania@heritage.com', '+91 98450 11223', 'Royal Crest Luxury Realty', 'PRM/KA/RERA/1251/310/AG/2026', 15, 'Luxury Villas & High-Rise Penthouses', 'https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=400&q=80', 4.95, 24, TRUE),
('Pooja Hegde Deshmukh', 'pooja.realtor@heritage.com', '+91 99002 44556', 'Apex Commercial & Tech Realty', 'PRM/KA/RERA/1251/310/AG/2027', 11, 'Grade-A Commercial & IT Tech Parks', 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80', 4.90, 18, TRUE),
('Col. Rajeshwar Rao (Retd.)', 'rajeshwar.rao@heritage.com', '+91 98455 77889', 'Sovereign Land & Estate Advisors', 'PRM/KA/RERA/1251/310/AG/2028', 19, 'Coffee Estates, Farmhouses & Land Parcels', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80', 4.98, 31, TRUE),
('Ananya Sharma', 'ananya.sharma@heritage.com', '+91 98451 99001', 'Signature Prime Residences', 'PRM/KA/RERA/1251/310/AG/2029', 9, 'Gated Villa Plots & Designer Duplexes', 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80', 4.88, 15, TRUE)
ON CONFLICT (email) DO NOTHING;

-- =============================================================================
-- 11. SUPABASE ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================
ALTER TABLE public.property_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_virtual_tours ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_floor_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_plots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.property_inquiries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agents ENABLE ROW LEVEL SECURITY;

-- Read policies: Public read for published media & agents
CREATE POLICY "Public can view property models" ON public.property_models FOR SELECT USING (true);
CREATE POLICY "Public can view virtual tours" ON public.property_virtual_tours FOR SELECT USING (true);
CREATE POLICY "Public can view property videos" ON public.property_videos FOR SELECT USING (true);
CREATE POLICY "Public can view floor plans" ON public.property_floor_plans FOR SELECT USING (true);
CREATE POLICY "Public can view plot layouts" ON public.property_plots FOR SELECT USING (true);
CREATE POLICY "Public can view agents" ON public.agents FOR SELECT USING (true);
CREATE POLICY "Public can insert inquiries" ON public.property_inquiries FOR INSERT WITH CHECK (true);
CREATE POLICY "Users can manage favorites" ON public.favorites FOR ALL USING (true);
