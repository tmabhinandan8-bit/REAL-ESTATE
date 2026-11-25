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
