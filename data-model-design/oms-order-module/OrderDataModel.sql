-- Order header stores the main order information
CREATE TABLE order_header (
    order_id VARCHAR(20) NOT NULL,
    order_type_id VARCHAR(20),
    order_name VARCHAR(255),
    external_id VARCHAR(255),
    order_date DATETIME,
    status_id VARCHAR(20),
    currency_uom VARCHAR(20),
    product_store_id VARCHAR(20),
    sales_channel_enum_id VARCHAR(20),
    grand_total DECIMAL(18,2),
    PRIMARY KEY (order_id)
);

-- Order items store the products purchased in the order
CREATE TABLE order_item (
    order_id VARCHAR(20) NOT NULL,
    order_item_seq_id VARCHAR(20) NOT NULL,
    order_item_type_id VARCHAR(20),
    product_id VARCHAR(20),
    quantity DECIMAL(18,2),
    unit_price DECIMAL(18,2),
    unit_list_price DECIMAL(18,2),
    item_description VARCHAR(255),
    status_id VARCHAR(20),
    external_id VARCHAR(255),
    PRIMARY KEY (order_id, order_item_seq_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);

-- Order roles store associations like who placed the order
CREATE TABLE order_role (
    order_id VARCHAR(20) NOT NULL,
    party_id VARCHAR(20) NOT NULL,
    role_type_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (order_id, party_id, role_type_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);

-- Order contact mechs store addresses, emails, and phone numbers associated with the order
CREATE TABLE order_contact_mech (
    order_id VARCHAR(20) NOT NULL,
    contact_mech_purpose_type_id VARCHAR(20) NOT NULL,
    contact_mech_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (order_id, contact_mech_purpose_type_id, contact_mech_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);

-- Order item ship group stores shipping details for a group of items (facility, addresses, methods)
CREATE TABLE order_item_ship_group (
    order_id VARCHAR(20) NOT NULL,
    ship_group_seq_id VARCHAR(20) NOT NULL,
    facility_id VARCHAR(20),
    contact_mech_id VARCHAR(20),
    telecom_contact_mech_id VARCHAR(20),
    shipment_method_type_id VARCHAR(20),
    carrier_party_id VARCHAR(20),
    PRIMARY KEY (order_id, ship_group_seq_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);

-- Associates order items with ship groups
CREATE TABLE order_item_ship_group_assoc (
    order_id VARCHAR(20) NOT NULL,
    order_item_seq_id VARCHAR(20) NOT NULL,
    ship_group_seq_id VARCHAR(20) NOT NULL,
    quantity DECIMAL(18,2),
    PRIMARY KEY (order_id, order_item_seq_id, ship_group_seq_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);

-- Order adjustments store taxes, discounts, and shipping charges
CREATE TABLE order_adjustment (
    order_adjustment_id VARCHAR(20) NOT NULL,
    order_adjustment_type_id VARCHAR(20),
    order_id VARCHAR(20),
    order_item_seq_id VARCHAR(20),
    ship_group_seq_id VARCHAR(20),
    amount DECIMAL(18,2),
    description VARCHAR(255),
    PRIMARY KEY (order_adjustment_id),
    FOREIGN KEY (order_id) REFERENCES order_header(order_id)
);
