-- Product table stores basic product information
CREATE TABLE product (
    product_id VARCHAR(20) NOT NULL,
    product_type_enum_id VARCHAR(20),       -- e.g., VIRTUAL_PRODUCT, VARIANT_PRODUCT
    internal_name VARCHAR(255),
    product_name VARCHAR(255),
    description TEXT,
    sales_discontinuation_date DATETIME,    -- used for soft deletes
    PRIMARY KEY (product_id)
);

-- Stores identifiers like SKU, UPC, GTIN, Shopify IDs
CREATE TABLE good_identification (
    good_identification_type_enum_id VARCHAR(20) NOT NULL,   -- SKU, UPC, GTIN, SHOPIFY_PROD_ID, etc.
    product_id VARCHAR(20) NOT NULL,
    id_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (good_identification_type_enum_id, product_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Features like Size, Color, Material
CREATE TABLE product_feature (
    product_feature_id VARCHAR(20) NOT NULL,
    product_feature_type_enum_id VARCHAR(20),   -- e.g., SIZE, COLOR
    description VARCHAR(255),                    -- e.g., "Large", "Red"
    PRIMARY KEY (product_feature_id)
);

-- Links features to products with date range
CREATE TABLE product_feature_appl (
    product_id VARCHAR(20) NOT NULL,
    product_feature_id VARCHAR(20) NOT NULL,
    from_date DATETIME NOT NULL,
    thru_date DATETIME,
    PRIMARY KEY (product_id, product_feature_id, from_date),
    FOREIGN KEY (product_id) REFERENCES product(product_id),
    FOREIGN KEY (product_feature_id) REFERENCES product_feature(product_feature_id)
);

-- Product categories for organizing the catalog
CREATE TABLE product_category (
    product_category_id VARCHAR(20) NOT NULL,
    product_category_type_enum_id VARCHAR(20),
    category_name VARCHAR(255),
    PRIMARY KEY (product_category_id)
);

-- Links products to categories with date range
CREATE TABLE product_category_member (
    product_category_id VARCHAR(20) NOT NULL,
    product_id VARCHAR(20) NOT NULL,
    from_date DATETIME NOT NULL,
    thru_date DATETIME,
    PRIMARY KEY (product_category_id, product_id, from_date),
    FOREIGN KEY (product_category_id) REFERENCES product_category(product_category_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Pricing with type, purpose, currency, and date range
CREATE TABLE product_price (
    product_id VARCHAR(20) NOT NULL,
    product_price_type_enum_id VARCHAR(20) NOT NULL,     -- LIST_PRICE, SALE_PRICE, COST
    product_price_purpose_enum_id VARCHAR(20) NOT NULL,   -- PURCHASE, COMPONENT
    currency_uom_id VARCHAR(20) NOT NULL,                 -- USD, INR, etc.
    from_date DATETIME NOT NULL,
    price DECIMAL(18,2),
    PRIMARY KEY (product_id, product_price_type_enum_id, product_price_purpose_enum_id, currency_uom_id, from_date),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Connects virtual products to their variants
CREATE TABLE product_assoc (
    product_id VARCHAR(20) NOT NULL,             -- parent (virtual) product
    product_id_to VARCHAR(20) NOT NULL,          -- child (variant) product
    product_assoc_type_enum_id VARCHAR(20) NOT NULL,   -- e.g., PRODUCT_VARIANT
    from_date DATETIME NOT NULL,
    thru_date DATETIME,
    PRIMARY KEY (product_id, product_id_to, product_assoc_type_enum_id, from_date),
    FOREIGN KEY (product_id) REFERENCES product(product_id),
    FOREIGN KEY (product_id_to) REFERENCES product(product_id)
);
