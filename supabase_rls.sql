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
