-- UDM-compliant database schema for storing customer data.

-- 1. Type entities

CREATE TABLE PartyType(
    party_type_id VARCHAR(20) NOT NULL, -- Unique type identifier (e.g., 'PERSON', 'PARTY_GROUP')
    parent_type_id VARCHAR(20), -- Self-referencing FK for type hierarchy
    description VARCHAR(255), -- Human-readable description of this type
    PRIMARY KEY (party_type_id),
    FOREIGN KEY (parent_type_id) REFERENCES PartyType(party_type_id) ON DELETE RESTRICT
) CHARACTER SET utf8mb4;

CREATE TABLE StatusItem(
    status_id VARCHAR(20) NOT NULL, -- Unique status identifier (e.g., 'PARTY_ENABLED')
    status_type_id VARCHAR(20), -- Groups statuses into categories
    description VARCHAR(255), -- Human-readable status label
    PRIMARY KEY (status_id)
) CHARACTER SET utf8mb4;

CREATE TABLE RoleType(
    role_type_id VARCHAR(20) NOT NULL, -- Unique role identifier (e.g., 'CUSTOMER', 'SUPPLIER')
    parent_type_id VARCHAR(20), -- Self-referencing FK for role hierarchy
    description VARCHAR(255), -- Human-readable role description
    PRIMARY KEY (role_type_id),
    FOREIGN KEY (parent_type_id) REFERENCES RoleType(role_type_id) ON DELETE RESTRICT
) CHARACTER SET utf8mb4;

CREATE TABLE PartyIdentificationType(
    party_identification_type_id VARCHAR(20) NOT NULL, -- Unique ID type key (e.g., 'SHOPIFY_CUST_ID')
    description VARCHAR(255), -- Human-readable description
    PRIMARY KEY (party_identification_type_id)
) CHARACTER SET utf8mb4;

CREATE TABLE ContactMechType(
    contact_mech_type_id VARCHAR(20) NOT NULL, -- Unique contact type key (e.g., 'EMAIL_ADDRESS', 'POSTAL_ADDRESS', 'TELECOM_NUMBER')
    description VARCHAR(255), -- Human-readable description
    PRIMARY KEY (contact_mech_type_id)
) CHARACTER SET utf8mb4;

CREATE TABLE ContactMechPurposeType (
    contact_mech_purpose_id VARCHAR(20) NOT NULL, -- Unique purpose key (e.g., 'PRIMARY_EMAIL', 'SHIPPING_LOCATION')
    description VARCHAR(255), -- Human-readable description
    PRIMARY KEY (contact_mech_purpose_id)
) CHARACTER SET utf8mb4;


-- Core Entities

-- Party: The master entity of the party data model.
CREATE TABLE Party(
    party_id VARCHAR(20) NOT NULL, -- Unique party identifier
    party_type_id VARCHAR(20) NOT NULL, -- FK to PartyType (PERSON or PARTY_GROUP)
    status_id VARCHAR(20), -- Current party status
    disabled CHAR(1) DEFAULT 'N', -- Soft-delete flag ('Y'/'N')
    created_date DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3), -- Record creation timestamp
    last_modified_date DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), -- Auto-updated modification timestamp
    PRIMARY KEY (party_id),
    FOREIGN KEY (party_type_id) REFERENCES PartyType(party_type_id) ON DELETE RESTRICT,
    FOREIGN KEY (status_id) REFERENCES StatusItem(status_id) ON DELETE SET NULL
) CHARACTER SET utf8mb4;

-- Person: Stores basic personal information
CREATE TABLE Person(
    party_id VARCHAR(20) NOT NULL, -- FK to Party (1:1 sub-type relationship)
    first_name VARCHAR(100), -- Customer's first name
    last_name VARCHAR(100), -- Customer's last name
    birth_date DATE, -- Date of birth
    gender CHAR(1), -- Single-character gender code
    PRIMARY KEY (party_id),
    FOREIGN KEY (party_id) REFERENCES Party(party_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;

-- PartyRole: Defines the role of the party
CREATE TABLE PartyRole(
    party_id VARCHAR(20) NOT NULL, -- FK to Party
    role_type_id VARCHAR(20) NOT NULL, -- FK to RoleType defining the party's role
    PRIMARY KEY (party_id, role_type_id),
    FOREIGN KEY (party_id) REFERENCES Party(party_id) ON DELETE CASCADE,
    FOREIGN KEY (role_type_id) REFERENCES RoleType(role_type_id) ON DELETE RESTRICT
) CHARACTER SET utf8mb4;

-- PartyIdentification: Stores external system IDs (e.g., Shopify Customer ID)
CREATE TABLE PartyIdentification (
    party_id VARCHAR(20) NOT NULL, -- FK to Party
    party_identification_type_id VARCHAR(20) NOT NULL, -- FK identifying the external system
    id_value VARCHAR(255) NOT NULL, -- The external system's ID value (e.g., Shopify customer ID)
    PRIMARY KEY (party_id, party_identification_type_id),
    FOREIGN KEY (party_id) REFERENCES Party(party_id) ON DELETE CASCADE,
    FOREIGN KEY (party_identification_type_id) REFERENCES PartyIdentificationType(party_identification_type_id) ON DELETE RESTRICT,
    UNIQUE (party_identification_type_id, id_value)
) CHARACTER SET utf8mb4;

-- ContactMech: Stores all contact mechanisms for a party (Email, Phone, Social Media Handles, etc.)
-- Social media handles are stored in 'info_string' with contact_mech_type_id = 'SOCIAL_MEDIA'.
CREATE TABLE ContactMech(
    contact_mech_id VARCHAR(20) NOT NULL, -- Unique identifier for this contact mechanism
    contact_mech_type_id VARCHAR(20) NOT NULL, -- FK to ContactMechType
    info_string VARCHAR(255), -- Stores inline contact data (e.g., email address, social media handle)
    PRIMARY KEY (contact_mech_id),
    FOREIGN KEY (contact_mech_type_id) REFERENCES ContactMechType(contact_mech_type_id) ON DELETE RESTRICT
) CHARACTER SET utf8mb4;

-- PostalAddress: Stores physical addresses (Billing/Shipping)
CREATE TABLE PostalAddress(
    contact_mech_id VARCHAR(20) NOT NULL, -- FK to ContactMech (1:1 sub-type)
    to_name VARCHAR(100), -- Recipient name at this address
    address1 VARCHAR(255), -- Primary street address line
    address2 VARCHAR(255), -- Secondary address line (apt, suite, etc.)
    city VARCHAR(100), -- City name
    postal_code VARCHAR(20), -- ZIP or postal code
    state_province_geo_id VARCHAR(20), -- State/province geographic ID
    country_geo_id VARCHAR(20), -- Country geographic ID
    PRIMARY KEY (contact_mech_id),
    FOREIGN KEY (contact_mech_id) REFERENCES ContactMech(contact_mech_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;

-- TelecomNumber: Stores phone numbers
CREATE TABLE TelecomNumber (
    contact_mech_id VARCHAR(20) NOT NULL, -- FK to ContactMech (1:1 sub-type)
    country_code VARCHAR(10), -- International dialing code
    area_code VARCHAR(10), -- Regional area code
    contact_number VARCHAR(20), -- Local phone number
    PRIMARY KEY (contact_mech_id),
    FOREIGN KEY (contact_mech_id) REFERENCES ContactMech(contact_mech_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;

-- PartyContactMech: Join table for the party and contact mechanism.
CREATE TABLE PartyContactMech(
    party_id VARCHAR(20) NOT NULL, -- FK to Party
    contact_mech_id VARCHAR(20) NOT NULL, -- FK to ContactMech
    contact_mech_purpose_id VARCHAR(20) NOT NULL, -- FK defining the purpose of this link
    from_date DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), -- Date this association became active
    thru_date DATETIME(3) NULL, -- Date this association expired (NULL = currently active)
    PRIMARY KEY (party_id, contact_mech_id, contact_mech_purpose_id, from_date),
    FOREIGN KEY (party_id) REFERENCES Party(party_id) ON DELETE CASCADE,
    FOREIGN KEY (contact_mech_id) REFERENCES ContactMech(contact_mech_id) ON DELETE CASCADE,
    FOREIGN KEY (contact_mech_purpose_id) REFERENCES ContactMechPurposeType(contact_mech_purpose_id) ON DELETE RESTRICT
) CHARACTER SET utf8mb4;

-- CustomerPreference: Stores specific customer preferences (Marketing Opt-ins, Preferred Channels, etc.)
CREATE TABLE CustomerPreference(
    party_id VARCHAR(20) NOT NULL, -- FK to Party
    preference_type_id VARCHAR(20) NOT NULL, -- Type of preference (e.g., 'MARKETING_OPT_IN', 'PREFERRED_CHANNEL', 'EMAIL_VERIFIED')
    preference_value VARCHAR(255), -- Value for this preference
    PRIMARY KEY (party_id, preference_type_id),
    FOREIGN KEY (party_id) REFERENCES Party(party_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;
