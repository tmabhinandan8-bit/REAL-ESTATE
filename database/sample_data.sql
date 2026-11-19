-- =============================================================================
-- SAMPLE DATA FOR REAL ESTATE MANAGEMENT SYSTEM
-- Covers all 6 Property Categories across Indian Cities
-- All users share salt: salt1234567890ab
-- Admin password: Admin@123
-- Other users password: Password@123
-- =============================================================================

USE real_estate_management;

-- 1. USERS
INSERT INTO users (user_id, full_name, email, mobile_number, password_hash, password_salt, user_role, is_verified) VALUES
(1, 'System Administrator', 'admin@realestate.com', '+91 9880011223', '21406a47caa8fa774455ce9f311d8cedd00d176ef0333cde739bac5486e78e59', 'salt1234567890ab', 'ADMIN', TRUE),
(2, 'Rajesh Gowda', 'rajesh.owner@example.com', '+91 9845012345', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'PROPERTY_OWNER', TRUE),
(3, 'Anand Kulkarni', 'anand.seller@example.com', '+91 9845198765', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'SELLER', TRUE),
(4, 'Priya Sharma', 'priya.buyer@example.com', '+91 9980123456', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'BUYER', FALSE),
(5, 'Kiran Kumar', 'kiran.tenant@example.com', '+91 9740112233', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'TENANT', FALSE),
(6, 'Suresh Reddy Properties', 'suresh.agent@example.com', '+91 9900887766', '451de766c9b36b6c1a6d35661e4733fab6f80ef9e1dc59a45f1ef8873c364b71', 'salt1234567890ab', 'AGENT', TRUE);

-- 2. PROPERTY CATEGORIES
INSERT INTO property_categories (category_id, category_name, category_code, description, icon_class) VALUES
(1, 'Residential Properties', 'RESIDENTIAL', 'Apartments, Villas, Independent Houses, Flats and PG', 'bi-house-heart'),
(2, 'Commercial Properties', 'COMMERCIAL', 'Shops, Office Spaces, Showrooms, Warehouses and Commercial Buildings', 'bi-building'),
(3, 'Agricultural Land', 'AGRICULTURAL', 'Farmlands, Plantations, Agricultural plots and Farm Lease', 'bi-tree'),
(4, 'Farmhouses', 'FARMHOUSE', 'Luxury farmhouses, Country estates and Weekend getaway villas', 'bi-flower1'),
(5, 'Plots and Sites', 'PLOT', 'Residential sites, Corner plots, Commercial layouts and Gated communities', 'bi-geo-alt'),
(6, 'Buildings and Projects', 'BUILDING', 'Apartment complexes, Gated townships and Commercial builders', 'bi-bricks');

-- 3. LOCATIONS
INSERT INTO property_locations (location_id, city, state, locality, pincode) VALUES
(1, 'Mysuru', 'Karnataka', 'Vijayanagar 2nd Stage', '570017'),
(2, 'Mysuru', 'Karnataka', 'Jayalakshmipuram', '570012'),
(3, 'Bengaluru', 'Karnataka', 'Indiranagar 100ft Road', '560038'),
(4, 'Bengaluru', 'Karnataka', 'Whitefield IT Corridor', '560066'),
(5, 'Mandya', 'Karnataka', 'Maddur Highway', '571428'),
(6, 'Hassan', 'Karnataka', 'Sakleshpur Estate Belt', '573134'),
(7, 'Mangaluru', 'Karnataka', 'Kadri Hills', '575002'),
(8, 'Hubballi', 'Karnataka', 'Vidyanagar', '580021'),
(9, 'Chennai', 'Tamil Nadu', 'OMR Road', '600096'),
(10, 'Hyderabad', 'Telangana', 'Gachibowli Financial Dist', '500032'),
(11, 'Mumbai', 'Maharashtra', 'Bandra West', '400050'),
(12, 'Pune', 'Maharashtra', 'Hinjewadi Phase 1', '411057');

-- 4. PROPERTIES (Core Table)
INSERT INTO properties (property_id, owner_id, category_id, title, description, purpose, price, property_area, area_unit, state, city, locality, address, pincode, approval_status, listing_status, is_featured) VALUES
-- 1. Residential: Villa in Mysuru
(1, 2, 1, 'Luxury 4 BHK Independent Villa in Vijayanagar', 'Exquisite brand new 4 BHK duplex villa with teakwood finish, private garden, Italian marble flooring and solar water heater.', 'BUY', 18500000.00, 3200.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Vijayanagar 2nd Stage', 'No 45, 12th Cross, Vijayanagar 2nd Stage, Mysuru', '570017', 'APPROVED', 'AVAILABLE', TRUE),

-- 2. Residential: 3 BHK Flat in Bengaluru
(2, 3, 1, 'Premium 3 BHK High-Rise Apartment in Indiranagar', 'Spacious semi-furnished apartment with panoramic skyline views, modular kitchen, 2 covered car parks, and club house access.', 'RENT', 65000.00, 1950.00, 'Sq. Ft.', 'Karnataka', 'Bengaluru', 'Indiranagar 100ft Road', 'Flat 802, Prestige Ozone Towers, Indiranagar, Bengaluru', '560038', 'APPROVED', 'AVAILABLE', TRUE),

-- 3. Commercial: Prime Showroom in Mysuru
(3, 2, 2, 'Main Road Corner Commercial Showroom Space', 'Prime corner showroom with 60ft frontage, ideal for branded retail, banks or clinics. 100% power backup and dedicated customer parking.', 'RENT', 120000.00, 2400.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Jayalakshmipuram', 'Shop 3, Heritage Commercial Hub, Temple Road, Jayalakshmipuram', '570012', 'APPROVED', 'AVAILABLE', FALSE),

-- 4. Commercial: IT Office Space in Bengaluru
(4, 6, 2, 'Grade-A Plug and Play IT Office in Whitefield', 'Fully furnished 80-seater IT office space with conference rooms, server room, cafeteria and 24/7 security.', 'LEASE', 450000.00, 7500.00, 'Sq. Ft.', 'Karnataka', 'Bengaluru', 'Whitefield IT Corridor', 'Level 4, Sigma Tech Park, Varthur Main Road, Whitefield', '560066', 'APPROVED', 'AVAILABLE', TRUE),

-- 5. Agricultural Land: Fertile Land near Mandya
(5, 3, 3, '5.5 Acres Fertile Sugarcane & Paddy Farmland', 'Highly fertile agricultural land with perennial canal water supply, 2 running borewells, 3-phase electricity, and tar road approach.', 'BUY', 16500000.00, 5.50, 'Acres', 'Karnataka', 'Mandya', 'Maddur Highway', 'Survey No 142/3, Maddur Taluk, Mandya District', '571428', 'APPROVED', 'AVAILABLE', TRUE),

-- 6. Farmhouse: Coffee Estate Villa in Sakleshpur / Hassan
(6, 2, 4, 'Boutique Coffee Estate Farmhouse with Stream', 'Scenic weekend farmhouse nestled inside a 3-acre silver oak and coffee plantation with private natural stream, outdoor gazebo and organic orchard.', 'BUY', 22000000.00, 3.00, 'Acres', 'Karnataka', 'Hassan', 'Sakleshpur Estate Belt', 'Valley View Estate, NH 75 Highway, Sakleshpur, Hassan', '573134', 'APPROVED', 'AVAILABLE', TRUE),

-- 7. Plots: MUDA Approved Corner Site in Mysuru
(7, 3, 5, 'Prime 50x80 North-East Facing Corner Plot', 'MUDA-approved residential site in a fully developed gated layout. 60ft asphalt road, underground drainage, water and electric lines ready.', 'BUY', 11500000.00, 4000.00, 'Sq. Ft.', 'Karnataka', 'Mysuru', 'Vijayanagar 2nd Stage', 'Site No 102, H-Block, Vijayanagar, Mysuru', '570017', 'APPROVED', 'AVAILABLE', FALSE),

-- 8. Buildings: New Apartment Project in Mangaluru
(8, 6, 6, 'Coastal Palms 24-Unit Residential Apartment Project', 'Brand new G+4 residential building complex featuring modern 2 & 3 BHK flats with ocean breeze, automatic elevators, and RERA approval.', 'BUY', 145000000.00, 32000.00, 'Sq. Ft.', 'Karnataka', 'Mangaluru', 'Kadri Hills', 'Coastal Palms, Airport Road, Kadri Hills, Mangaluru', '575002', 'APPROVED', 'AVAILABLE', TRUE),

-- 9. Pending Listing for Approval Demo
(9, 3, 1, '2 BHK Budget Flat in Vidyanagar, Hubballi', 'Newly constructed 2 BHK apartment near BVB Engineering College, ready for immediate occupancy.', 'BUY', 4200000.00, 1100.00, 'Sq. Ft.', 'Karnataka', 'Hubballi', 'Vidyanagar', 'Flat 204, Shanti Nilaya, Vidyanagar, Hubballi', '580021', 'PENDING', 'AVAILABLE', FALSE);

-- 5. RESIDENTIAL DETAILS
INSERT INTO residential_details (property_id, residential_type, bedrooms, bathrooms, built_up_area, carpet_area, parking_slots, furnishing_status, floor_number, total_floors, property_age_years) VALUES
(1, 'VILLA', 4, 4, 3200.00, 2750.00, 2, 'SEMI_FURNISHED', 0, 2, 1),
(2, 'APARTMENT', 3, 3, 1950.00, 1680.00, 2, 'SEMI_FURNISHED', 8, 14, 3),
(9, 'FLAT', 2, 2, 1100.00, 950.00, 1, 'UNFURNISHED', 2, 4, 0);

-- 6. COMMERCIAL DETAILS
INSERT INTO commercial_details (property_id, commercial_type, carpet_area, number_of_floors, parking_spaces, building_type, commercial_usage, power_backup) VALUES
(3, 'SHOWROOM', 2100.00, 1, 6, 'Stand-alone Commercial Building', 'Retail Brand / Bank / Clinic', TRUE),
(4, 'OFFICE_SPACE', 6800.00, 1, 15, 'Grade-A Tech Park', 'IT / Software / Corporate Office', TRUE);

-- 7. LAND DETAILS
INSERT INTO land_details (property_id, land_type, total_land_area, land_unit, soil_type, water_availability, has_borewell, road_connectivity, has_electricity, land_document_type, survey_number) VALUES
(5, 'FARMLAND', 5.50, 'ACRES', 'Red Loamy & Black Clay Soil', 'Canal water + 2 Borewells', TRUE, 'Tar Highway 30ft', TRUE, 'Clear Title Deed, RTC & Encumbrance Free', 'Sy. No 142/3');

-- 8. FARMHOUSE DETAILS
INSERT INTO farmhouse_details (property_id, land_area, built_up_area, bedrooms, bathrooms, has_garden, has_swimming_pool, parking_capacity, water_facilities) VALUES
(6, 3.00, 2800.00, 3, 3, TRUE, TRUE, 5, 'Perennial mountain spring + Deep borewell');

-- 9. PLOT DETAILS
INSERT INTO plot_details (property_id, plot_type, plot_dimensions, total_area, facing_direction, road_width_feet, is_approved_layout, approval_authority) VALUES
(7, 'CORNER_SITE', '50 x 80', 4000.00, 'NORTH_EAST', 60, TRUE, 'MUDA (Mysuru Urban Development Authority)');

-- 10. BUILDING DETAILS
INSERT INTO building_details (property_id, building_type, number_of_floors, total_units, construction_status, builder_name, project_completion_date, rera_number) VALUES
(8, 'APARTMENT_BUILDING', 5, 24, 'READY_TO_MOVE', 'Coastal Horizon Developers Ltd.', '2026-03-31', 'PRM/KA/RERA/1257/334/PR/240118/006501');

-- 11. PROPERTY IMAGES (Professional architectural images)
INSERT INTO property_images (property_id, image_url, is_primary, caption) VALUES
(1, 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80', TRUE, 'Front elevation villa'),
(1, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=80', FALSE, 'Spacious living room'),
(2, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', TRUE, 'Apartment exterior view'),
(2, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=80', FALSE, 'Balcony view of city'),
(3, 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1000&q=80', TRUE, 'Main street commercial front'),
(4, 'https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1000&q=80', TRUE, 'Modern tech corporate office'),
(5, 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=80', TRUE, 'Panoramic fertile farmland'),
(6, 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1000&q=80', TRUE, 'Farmhouse scenic estate'),
(7, 'https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1000&q=80', TRUE, 'Clean plotted site development'),
(8, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1000&q=80', TRUE, 'Building towers complex');

-- 12. AMENITIES
INSERT INTO property_amenities (property_id, amenity_name) VALUES
(1, 'Private Garden'), (1, 'Modular Kitchen'), (1, 'Covered Parking'), (1, 'Solar Water Heater'), (1, 'CCTV Security'),
(2, 'Swimming Pool'), (2, 'Gymnasium'), (2, 'Clubhouse'), (2, 'Power Backup'), (2, '24/7 Security'), (2, 'Lift'),
(3, '100% Power Backup'), (3, 'High Footfall Area'), (3, 'Customer Parking'), (3, 'Fire Safety'),
(4, 'High Speed Internet'), (4, 'Central Air Conditioning'), (4, 'Cafeteria'), (4, 'Access Card Entry'),
(5, 'Perennial Canal Water'), (5, '3-Phase Electricity'), (5, 'Tar Road Access'), (5, 'Fencing'),
(6, 'Private Natural Stream'), (6, 'Swimming Pool'), (6, 'Gazebo'), (6, 'Organic Orchard'),
(7, 'Underground Drainage'), (7, 'Street Lights'), (7, 'Gated Layout'), (7, 'Park Area'),
(8, 'Automatic Elevators'), (8, 'Rooftop Lounge'), (8, 'Rainwater Harvesting'), (8, 'Covered Car Park');

-- 13. OWNER VERIFICATIONS
INSERT INTO owner_verifications (verification_id, user_id, identity_proof_type, identity_proof_number, document_path, property_address, verification_status, admin_notes, submitted_at, verified_at) VALUES
(1, 2, 'Aadhaar Card', 'XXXX-XXXX-8921', '/uploads/docs/aadhaar_rajesh.pdf', 'Vijayanagar 2nd Stage, Mysuru', 'APPROVED', 'Verified with original property sale deed and electricity bill.', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY),
(2, 3, 'PAN Card', 'ABCDE1234F', '/uploads/docs/pan_anand.pdf', 'Indiranagar 100ft Road, Bengaluru', 'APPROVED', 'Document clear, title records verified on Bhoomi portal.', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 3 DAY);

-- 14. PROPERTY VISITS / APPOINTMENTS
INSERT INTO property_visits (visit_id, property_id, user_id, visit_date, visit_time, contact_number, notes, appointment_status) VALUES
(1, 1, 4, CURDATE() + INTERVAL 2 DAY, '10:30:00', '+91 9980123456', 'Interested in buying villa, visiting with family.', 'APPROVED'),
(2, 2, 5, CURDATE() + INTERVAL 3 DAY, '15:00:00', '+91 9740112233', 'Looking for immediate rental move-in.', 'PENDING'),
(3, 7, 4, CURDATE() - INTERVAL 1 DAY, '11:00:00', '+91 9980123456', 'Site inspection completed, checking layout plan.', 'COMPLETED');

-- 15. RENTAL AGREEMENTS & PAYMENTS
INSERT INTO rental_agreements (agreement_id, property_id, owner_id, tenant_id, monthly_rent, security_deposit, agreement_start_date, agreement_end_date, agreement_status, terms_and_conditions) VALUES
(1, 2, 3, 5, 65000.00, 300000.00, '2026-04-01', '2027-03-31', 'ACTIVE', 'Rent payable before 5th of each month. 11 months lock-in period.');

INSERT INTO rent_payments (rent_payment_id, agreement_id, payment_month, amount_paid, payment_date, payment_method, transaction_ref, payment_status) VALUES
(1, 1, 'October 2026', 65000.00, '2026-10-04', 'SIMULATED_UPI', 'TXN-RENT-202610-0981', 'SUCCESS'),
(2, 1, 'November 2026', 65000.00, '2026-11-03', 'SIMULATED_UPI', 'TXN-RENT-202611-0412', 'SUCCESS');

-- 16. PURCHASE TRANSACTIONS
INSERT INTO purchase_transactions (transaction_id, property_id, buyer_id, seller_id, purchase_price, transaction_date, payment_status, transaction_reference, remarks) VALUES
(1, 7, 4, 3, 11500000.00, NOW() - INTERVAL 15 DAY, 'SUCCESS', 'TXN-PURCHASE-202611-7782', 'College Project Demo Purchase simulated transaction.');
