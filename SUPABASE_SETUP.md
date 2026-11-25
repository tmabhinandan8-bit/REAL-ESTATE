# Supabase PostgreSQL Integration Guide

## Real Estate Management System
**Tagline:** *"Find. Buy. Sell. Rent. Build."*

---

## 1. Overview

This production-ready database integration connects your Real Estate Management website with **Supabase PostgreSQL**, providing:
- Complete **India Geographical Hierarchy** (All 28 States, 8 Union Territories, 786 Districts verified via Government of India Local Government Directory - LGD / IGOD).
- **UUID-based Property Schema** supporting all 10 property types and 3 listing types (Sale, Rent, Lease).
- **Full Relationship Model:** Profiles, Properties, Images, Features, Favorites, Inquiries, Appointments, Transactions, Reviews, Agents, and Notifications.
- **Row Level Security (RLS):** Strict role-based policies protecting owner listings, private user inquiries, appointments, and favorites.
- **Supabase Storage:** Configured public `property-images` bucket with 10MB limit and MIME validation.
- **Automated Triggers:** Automatic profile creation on Auth signup, and automatic `updated_at` timestamps.

---

## 2. Quick Setup in Supabase (1-Click)

### Step 1: Open Supabase Project
1. Log in to [supabase.com](https://supabase.com).
2. Go to your project or create a new project named `RealEstateDB`.
3. Open the **SQL Editor** tab from the left sidebar.

### Step 2: Execute Master SQL Script
- Open `supabase_all_in_one.sql` (or copy the full SQL script provided).
- Paste it into the Supabase SQL Editor and click **RUN**.
- The script is idempotent and handles table creation, foreign keys, constraints, indexes, triggers, seed data, RLS policies, and storage setup in a single transaction.

---

## 3. Geographical Hierarchy (India LGD Verification)

- **Total States:** 28
- **Total Union Territories:** 8
- **Total Districts:** 786 (Zero missing, zero duplicates)
- **Karnataka Reference (All 31 Districts Included):**
  1. Bagalkot, 2. Ballari, 3. Belagavi, 4. Bengaluru Rural, 5. Bengaluru Urban, 6. Bidar, 7. Chamarajanagar, 8. Chikkaballapur, 9. Chikkamagaluru, 10. Chitradurga, 11. Dakshina Kannada, 12. Davanagere, 13. Dharwad, 14. Gadag, 15. Hassan, 16. Haveri, 17. Kalaburagi, 18. Kodagu, 19. Kolar, 20. Koppal, 21. Mandya, 22. Mysuru, 23. Raichur, 24. Ramanagara, 25. Shivamogga, 26. Tumakuru, 27. Udupi, 28. Uttara Kannada, 29. Vijayapura, 30. Yadgir, 31. Vijayanagara.

---

## 4. Environment Variables (`.env`)

Add your Supabase project keys to `.env`:
```env
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```
*(Never expose the `SUPABASE_SERVICE_ROLE_KEY` on the client side).*

---

## 5. File Manifest

| File | Description |
|---|---|
| `supabase_all_in_one.sql` | Complete consolidated script for 1-click execution in Supabase SQL Editor |
| `supabase_schema.sql` | 16 normalized tables, constraints, indexes, and triggers |
| `supabase_seed.sql` | Full geographical seed data for 28 States, 8 UTs, 786 districts, cities & localities |
| `supabase_rls.sql` | Production Row Level Security policies across all tables |
| `supabase_storage.sql` | Storage bucket creation & storage access policies |
| `SUPABASE_SETUP.md` | Complete architecture, API reference, and testing instructions |
