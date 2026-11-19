# REAL ESTATE MANAGEMENT SYSTEM
> **Tagline:** *"Find. Buy. Sell. Rent. Build."*

A full-stack, responsive, and professional web application designed for property owners, buyers, tenants, landowners, builders, and real estate agents. Engineered with **Core Java, Java OOP Principles, Java Servlets, JDBC, and MySQL**, paired with a modern, simple, and clean **HTML5, CSS3, Bootstrap 5, and JavaScript** frontend.

---

## 1. DRAWBACK ANALYSIS & HOW THEY ARE COMPLETELY RESOLVED

When analyzing the original ChatGPT master development prompt and naive college implementations, eight critical software engineering, security, and architectural drawbacks were identified and systematically resolved:

| # | Problem / Drawback in Prompt & Naive Designs | Real-World Impact | How It Is Completely Resolved in This Solution |
|---|---|---|---|
| **1** | **Database Polymorphism Antipattern (Wide-Table)**<br>Attempting to put 6 distinct property types (Villas, Farmland, Tech Parks, Sites) into a single flat SQL table. | 70% of table columns become `NULL` (e.g. Farmland doesn't have bedrooms, Villas don't have soil type/borewells), violating Normal Forms and breaking data integrity. | **Table-per-Type (TPT) Inheritance Pattern:** A core `properties` table manages shared fields (id, title, price, location, owner) linked 1-to-1 via Foreign Keys to specialized tables (`residential_details`, `commercial_details`, `land_details`, `farmhouse_details`, `plot_details`, `building_details`). This mirrors Java OOP inheritance directly into the relational schema. |
| **2** | **Vulnerable String Concatenation in Dynamic Search**<br>Generating SQL dynamically by concatenating user inputs (`"WHERE city = '" + city + "'"`). | Severe **SQL Injection (SQLi)** vulnerabilities and syntax failures with special characters (e.g. apostrophes in property titles). | **Dynamic Parameterized Query Builder:** Every filter query is generated with safe `PreparedStatement` indexed parameters (`?`) in `PropertyDAO`, supporting multi-attribute range filtering (city, category, purpose, budget, bedrooms) with complete sanitization. |
| **3** | **Race Conditions & Concurrency Vulnerabilities**<br>Simultaneous visit scheduling, double-selling, or rental agreement creation by multiple threads. | Servlets run concurrently in Tomcat's thread pool. Unsynchronized DAO state leads to duplicate bookings, phantom reads, and inconsistent data. | **JDBC Atomic Transactions with Rollback:** Every multi-step operation (e.g. creating property + details + images; executing purchase + updating listing status to `SOLD`) uses `conn.setAutoCommit(false)`, `conn.commit()`, and `conn.rollback()` in `finally` blocks, guaranteeing ACID consistency. |
| **4** | **Insecure Direct Object Reference (IDOR)**<br>Allowing any logged-in user to edit or delete any property ID by manipulating HTTP request parameters. | Malicious users could tamper with or delete other owners' property listings. | **Ownership Verification & RBAC Guards:** `PropertyService` and `PropertyDAO` enforce ownership validation: `if (!isAdmin && property.getOwnerId() != currentUserId) throw new UnauthorizedAccessException()`. |
| **5** | **Artificial / Fake Multithreading**<br>Most college projects add a dummy `Thread.sleep(1000)` inside a method to claim OOP multithreading. | Adds latency to HTTP servlet threads without solving any real architectural requirement. | **Real-world Asynchronous Worker Queue:** Implemented `AsyncNotificationService` featuring a bounded `LinkedBlockingQueue` and a dedicated background daemon worker thread consuming tasks with synchronized dispatching, simulating background email/SMS alerts and audit trails without stalling client HTTP responses. |
| **6** | **Plaintext Passwords or Weak MD5 Hashing**<br>Storing passwords directly or using broken algorithms. | Sensitive user data exposed in database dumps; vulnerable to rainbow tables. | **Cryptographic Salted SHA-256:** Built `PasswordUtil` using Java's `java.security.MessageDigest` and `java.security.SecureRandom` to generate a unique 16-byte cryptographic salt per user, securely hashed before persistence. |
| **7** | **Rigid Tomcat Dependency & Configuration Nightmare**<br>Students often struggle with complex Tomcat server configuration, port clashes, and 404 errors. | The project fails to run during live presentations or evaluations. | **Dual Deployment Architecture:** Provides both traditional **WAR deployment** (`web.xml` + Servlets) for Apache Tomcat, AND an embedded **Lightweight HTTP Server** in `Main.java` (`java com.realestate.Main`) that serves the static frontend and REST APIs with zero external server installation. |
| **8** | **Frontend Clutter & Overcrowded Dashboards**<br>Complex frameworks, 15 sidebars, and crypto widgets detract from usability. | Distracting for users and difficult for college viva examiners to evaluate. | **Clean, Modern, Simple Interface:** Designed following professional minimalist real estate principles: Pure white background, dark navy blue text (`#0f172a`), soft emerald green accents (`#059669`), rounded cards, and responsive layouts. |

---

## 2. JAVA OOP CONCEPTS IMPLEMENTATION MATRIX

This project strictly adheres to Object-Oriented Programming principles:

```
                          ┌────────────────────────┐
                          │   Auditable (Interface)│
                          └───────────▲────────────┘
                                      │
                   ┌──────────────────┴──────────────────┐
                   │                                     │
      ┌────────────┴────────────┐          ┌─────────────┴─────────────┐
      │   User (Abstract Class) │          │ Property (Abstract Class) │
      └────────────▲────────────┘          └─────────────▲─────────────┘
                   │                                     │
       ┌───────────┼───────────┐             ┌───────────┼───────────┐
       │           │           │             │           │           │
     Buyer      Seller       Agent     Residential  Commercial Agricultural
     Tenant     Owner        Admin     Farmhouse    Plot       Building
```

| OOP Concept | Project Class / Interface | Real-World Implementation |
|---|---|---|
| **Classes & Objects** | All models in `com.realestate.model` | State and behavior encapsulation for users, properties, visits, agreements, and transactions. |
| **Encapsulation** | `User.java`, `Property.java`, all models | Private fields, defensive copying, sanitized setters, and validation methods. |
| **Inheritance** | `User` hierarchy, `Property` hierarchy | `User` -> `Buyer`, `Seller`, `Tenant`, `PropertyOwner`, `Agent`, `Admin`<br>`Property` -> `ResidentialProperty`, `CommercialProperty`, `AgriculturalLand`, `FarmHouse`, `Plot`, `Building`. |
| **Polymorphism** | `Property[]`, Factory methods in DAOs | Dynamic method dispatch: calling `calculateEstimatedMaintenanceCost()` or `getPropertyTypeDetails()` executes the specific subclass behavior. |
| **Method Overloading** | `calculateEstimatedRegistrationFee()` in `Property.java` | `calculateEstimatedRegistrationFee()` uses default 6.6% stamp duty; overloaded `calculateEstimatedRegistrationFee(double customRate)` accepts concessional rates. |
| **Method Overriding** | Subclasses of `Property` and `User` | Subclasses override `@Override public String getPropertyTypeDetails()` and `@Override public String getRolePermissionsDescription()`. |
| **Abstract Classes** | `Property.java`, `User.java`, `BaseDAO.java` | Provide shared template logic, database connection management, and enforce abstract contracts. |
| **Interfaces** | `Searchable`, `Payable`, `Verifiable`, `Auditable` | Decoupled cross-cutting contracts: `Payable` for financial transactions; `Searchable` for keyword/price matching; `Verifiable` for KYC. |
| **Constructors** | Parameterized & Default in all models | Constructor chaining using `this(...)` and superclass delegation using `super(...)`. |
| **this and super** | Throughout all model classes | Disambiguating field references (`this.price = price`) and invoking parent constructors (`super(id, title...)`). |
| **static and final** | `PasswordUtil`, `DBConnection`, constants | `final class PasswordUtil` prevents inheritance; `static volatile DBConnection` manages the thread-safe singleton instance. |
| **Packages** | `com.realestate.*` | Clean architectural separation: `model`, `dao`, `services`, `controllers`, `database`, `exceptions`, `util`, `interfaces`. |
| **Custom Exceptions** | `com.realestate.exceptions.*` | Structured error hierarchy: `RealEstateException`, `AuthenticationException`, `PropertyNotFoundException`, `UnauthorizedAccessException`, etc. |
| **Multithreading** | `AsyncNotificationService.java` | Dedicated background daemon thread consuming tasks asynchronously from a synchronized queue. |
| **Synchronization** | `AsyncNotificationService.java`, `DBConnection.java` | Thread-safe singleton creation via double-checked locking and synchronized queue dispatching. |

---

## 3. PROJECT DIRECTORY STRUCTURE

```
RealEstateManagement/
|-- database/
|   |-- schema.sql                 # 22 Normalized tables with TPT inheritance, constraints & indexes
|   |-- sample_data.sql            # Rich sample data across Mysuru, Bengaluru, Mandya, Hassan, etc.
|
|-- pom.xml                        # Maven configuration (Servlet 4.0 API, MySQL Connector/J)
|-- main.java                      # Root entrypoint redirecting to com.realestate.Main
|
|-- src/
|   |-- main/
|       |-- java/
|       |   |-- com/realestate/
|       |       |-- Main.java      # Application Runner with built-in embedded HTTP web server
|       |       |
|       |       |-- interfaces/    # OOP Interfaces
|       |       |   |-- Searchable.java
|       |       |   |-- Payable.java
|       |       |   |-- Verifiable.java
|       |       |   |-- Auditable.java
|       |       |
|       |       |-- model/         # OOP Domain Models
|       |       |   |-- user/
|       |       |   |   |-- User.java (abstract)
|       |       |   |   |-- Buyer.java, Seller.java, Tenant.java, PropertyOwner.java, Agent.java, Admin.java
|       |       |   |
|       |       |   |-- property/
|       |       |   |   |-- Property.java (abstract)
|       |       |   |   |-- ResidentialProperty.java, CommercialProperty.java, AgriculturalLand.java
|       |       |   |   |-- FarmHouse.java, Plot.java, Building.java
|       |       |   |
|       |       |   |-- rental/
|       |       |   |   |-- RentalAgreement.java, RentPayment.java
|       |       |   |-- purchase/
|       |       |   |   |-- PurchaseTransaction.java
|       |       |   |-- appointment/
|       |       |   |   |-- PropertyVisit.java
|       |       |   |-- verification/
|       |       |       |-- OwnerVerification.java
|       |       |
|       |       |-- exceptions/    # Custom Exception Hierarchy
|       |       |   |-- RealEstateException.java
|       |       |   |-- AuthenticationException.java
|       |       |   |-- PropertyNotFoundException.java
|       |       |   |-- InvalidPropertyDataException.java
|       |       |   |-- PaymentFailedException.java
|       |       |   |-- UnauthorizedAccessException.java
|       |       |
|       |       |-- database/      # Thread-Safe Database Singleton
|       |       |   |-- DBConnection.java
|       |       |
|       |       |-- util/          # Utilities
|       |       |   |-- PasswordUtil.java (Salted SHA-256)
|       |       |   |-- JsonUtil.java (Standard REST JSON)
|       |       |
|       |       |-- dao/           # Data Access Layer (JDBC + Transactions)
|       |       |   |-- BaseDAO.java
|       |       |   |-- UserDAO.java
|       |       |   |-- PropertyDAO.java
|       |       |   |-- AppointmentDAO.java
|       |       |   |-- RentalDAO.java
|       |       |   |-- PurchaseDAO.java
|       |       |   |-- VerificationDAO.java
|       |       |   |-- AnalyticsDAO.java
|       |       |
|       |       |-- services/      # Business Logic Layer
|       |       |   |-- UserService.java
|       |       |   |-- PropertyService.java
|       |       |   |-- AppointmentService.java
|       |       |   |-- RentalService.java
|       |       |   |-- PurchaseService.java
|       |       |   |-- VerificationService.java
|       |       |   |-- AsyncNotificationService.java (Multithreading & Synchronization)
|       |       |
|       |       |-- controllers/   # Java Servlets (REST Endpoints)
|       |           |-- AuthServlet.java
|       |           |-- PropertyServlet.java
|       |           |-- CompareServlet.java
|       |           |-- AppointmentServlet.java
|       |           |-- RentalServlet.java
|       |           |-- PurchaseServlet.java
|       |           |-- VerificationServlet.java
|       |           |-- AdminServlet.java
|       |
|       |-- webapp/                # Clean, Simple & Modern Frontend
|           |-- WEB-INF/
|           |   |-- web.xml        # Deployment descriptor for Apache Tomcat
|           |-- css/
|           |   |-- style.css      # Dark Navy Blue + Soft Emerald Accents
|           |-- js/
|           |   |-- app.js         # Modular client handling auth, search & toasts
|           |-- index.html         # Homepage: Hero, Search, 6 Categories, Featured
|           |-- properties.html    # Full Search, Filtering & Sorting
|           |-- property-details.html # Gallery, Specs, Booking Modal, Simulated Buy/Rent
|           |-- post-property.html # 4 Options Wizard with Dynamic Category Fields
|           |-- compare.html       # Side-by-Side Comparison Table (up to 3 properties)
|           |-- dashboard.html     # User Dashboard: Listings, Visits, Agreements, KYC
|           |-- admin.html         # Clean Admin Portal: 8 KPIs, Approvals, Users
|           |-- login.html         # Sign In with 1-click Demo Fill
|           |-- register.html      # User Registration with Role Selection
|
|-- README.md                      # Comprehensive Architecture & Execution Guide
```

---

## 4. DATABASE SETUP (MySQL)

1. Open MySQL Command Line Client or MySQL Workbench:
   ```bash
   mysql -u root -p
   ```

2. Execute the schema script:
   ```sql
   SOURCE database/schema.sql;
   ```

3. Populate with realistic Indian sample data:
   ```sql
   SOURCE database/sample_data.sql;
   ```

### Database Credentials Configuration
By default, the application connects to:
- **Host:** `localhost:3306`
- **Database:** `real_estate_management`
- **Username:** `root`
- **Password:** `""` (empty string)

If your MySQL has a password (e.g. `root`), set environment variables or update `DBConnection.java`:
```powershell
$env:DB_USER="root"
$env:DB_PASS="your_password"
```

---

## 5. HOW TO RUN THE APPLICATION

### Method A: Standalone Runner with Embedded HTTP Server (Recommended & Easiest)
You do not need to configure Apache Tomcat. The project includes an integrated Java HTTP server that serves both the frontend web files and the backend REST endpoints:

1. Compile the project:
   ```bash
   javac -d bin -cp ".;lib/*" (Get-ChildItem -Recurse -Filter *.java src/main/java).FullName
   ```
2. Run `com.realestate.Main`:
   ```bash
   java -cp "bin;lib/*" com.realestate.Main
   ```
3. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

### Method B: Deploying on Apache Tomcat (WAR File)
1. Build the `.war` package using Maven:
   ```bash
   mvn clean package
   ```
2. Copy `target/realestate.war` into Tomcat's `webapps/` folder.
3. Start Tomcat:
   ```bash
   catalina.bat start
   ```
4. Access the application at:
   ```
   http://localhost:8080/realestate/
   ```

---

## 6. DEMO LOGIN CREDENTIALS

All demo accounts are pre-populated with realistic activity:

| Role | Email Address | Password | Key Permissions & Features to Test |
|---|---|---|---|
| **System Administrator** | `admin@realestate.com` | `Admin@123` | View 8 KPIs, approve/reject pending listings, verify owner KYC documents, view all users. |
| **Property Owner** | `rajesh.owner@example.com` | `Password@123` | Post listings, view visit requests, manage rental agreements, view verification badge. |
| **Seller** | `anand.seller@example.com` | `Password@123` | Post properties for sale, receive purchase offers, view appointment requests. |
| **Buyer** | `priya.buyer@example.com` | `Password@123` | Schedule property visits, simulate purchases, compare up to 3 properties side-by-side. |
| **Tenant** | `kiran.tenant@example.com` | `Password@123` | Browse rentals, view active rental agreements, simulate monthly rent payments. |
| **Agent / Broker** | `suresh.agent@example.com` | `Password@123` | Multi-property management, commercial tech park listings, RERA registration. |

*(Note: On the `login.html` page, click any of the "Demo 1-Click Credentials" buttons to automatically fill email and password).*

---

## 7. REST-STYLE API ENDPOINTS REFERENCE

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user with role and salted password hashing. |
| `POST` | `/api/auth/login` | Authenticate user and initiate session. |
| `GET` | `/api/auth/me` | Fetch active logged-in user profile. |
| `POST` | `/api/auth/logout` | Invalidate session. |
| `GET` | `/api/properties` | Dynamic search & filter (city, category, purpose, budget, keyword, sort). |
| `GET` | `/api/properties?featured=true` | Retrieve featured properties for homepage. |
| `GET` | `/api/properties/details?id={id}` | Retrieve complete property details, specs, images, and amenities. |
| `GET` | `/api/properties/compare?ids={1,2,3}` | Compare up to 3 properties side-by-side. |
| `GET` | `/api/properties/my` | Fetch listings posted by current logged-in owner. |
| `POST` | `/api/properties` | Submit new property (saved with `PENDING` status). |
| `POST` | `/api/properties/delete` | Delete property with IDOR ownership validation. |
| `GET` | `/api/appointments` | View scheduled visit appointments. |
| `POST` | `/api/appointments` | Schedule a property visit appointment. |
| `POST` | `/api/appointments/status` | Update visit appointment status (`APPROVED`, `REJECTED`, `COMPLETED`). |
| `GET` | `/api/rentals` | Retrieve active rental agreements. |
| `POST` | `/api/rentals` | Create a new rental agreement. |
| `POST` | `/api/rentals/pay` | Simulate a monthly rent payment. |
| `GET` | `/api/purchases` | Retrieve purchase transaction records. |
| `POST` | `/api/purchases` | Execute simulated property purchase (marks property as `SOLD`). |
| `GET` | `/api/verifications` | Retrieve owner KYC verification status. |
| `POST` | `/api/verifications` | Submit owner identity and property title documents. |
| `GET` | `/api/admin/stats` | Retrieve 8 dashboard KPI counts. |
| `GET` | `/api/admin/properties/pending` | List all properties awaiting admin review. |
| `POST` | `/api/admin/properties/approve` | Approve property listing for public display. |
| `POST` | `/api/admin/properties/reject` | Reject property listing with reason. |
| `GET` | `/api/admin/verifications` | List pending owner KYC verification requests. |
| `POST` | `/api/admin/verifications/action` | Approve or reject owner KYC documents. |
| `GET` | `/api/admin/users` | List all registered users in system. |

---

## 8. CODEBASE QUALITY & COLLEGE PROJECT COMPLIANCE

- **Zero Hardcoded Frontend Data:** All property listings, appointments, user profiles, and transactions are served dynamically from the backend and MySQL database.
- **Full Exception Handling:** Custom exception hierarchy with meaningful error codes returned as clean JSON responses.
- **Multithreading & Synchronization:** Real-world background task consumer queue demonstrating concurrency control and thread safety.
- **UI/UX Simplicity:** Minimalist, uncluttered layout with responsive Bootstrap 5 styling, clean cards, and intuitive navigation.
