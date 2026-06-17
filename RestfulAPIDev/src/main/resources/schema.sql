SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS order_header;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS contact_mech;
DROP TABLE IF EXISTS customer;
SET FOREIGN_KEY_CHECKS = 1;

-- Customer Table
CREATE TABLE IF NOT EXISTS customer (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL
);

-- Contact_Mech Table
CREATE TABLE IF NOT EXISTS contact_mech (
    contact_mech_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    street_address VARCHAR(100) NOT NULL,
    city VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    phone_number VARCHAR(20),
    email VARCHAR(100),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

-- Product Table
CREATE TABLE IF NOT EXISTS product (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    color VARCHAR(30),
    size VARCHAR(10)
);

-- Order_Header Table
CREATE TABLE IF NOT EXISTS order_header (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    order_date DATE NOT NULL,
    customer_id INT NOT NULL,
    shipping_contact_mech_id INT NOT NULL,
    billing_contact_mech_id INT NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (shipping_contact_mech_id) REFERENCES contact_mech(contact_mech_id),
    FOREIGN KEY (billing_contact_mech_id) REFERENCES contact_mech(contact_mech_id)
);

-- Order_Item Table (Composite Primary Key)
CREATE TABLE IF NOT EXISTS order_item (
    order_item_seq_id INT AUTO_INCREMENT,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (order_item_seq_id, order_id), -- Seq_id MUST be first
    FOREIGN KEY (order_id) REFERENCES order_header(order_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);