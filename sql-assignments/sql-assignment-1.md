# SQL Assignment 1 Answers

### 1 New Customers Acquired in June 2023
**Business Problem:**
The marketing team ran a campaign in June 2023 and wants to see how many new customers signed up during that period.

**Fields to Retrieve:**

* PARTY_ID
* FIRST_NAME
* LAST_NAME
* EMAIL
* PHONE
* ENTRY_DATE

```sql
SELECT 
	p.party_id, 
	per.first_name, 
	per.last_name,
	cm.info_string,
	tel.contact_number,
	p.created_date
FROM party p
LEFT JOIN person per
	ON p.party_id = per.party_id
INNER JOIN party_role pr
	ON p.party_id = pr.party_id
LEFT JOIN party_contact_mech pcm
	ON p.party_id = pcm.party_id
LEFT JOIN contact_mech cm
	ON pcm.contact_mech_id = cm.contact_mech_id
LEFT JOIN telecom_number tel
	ON cm.contact_mech_id = tel.contact_mech_id
WHERE pr.role_type_id = 'CUSTOMER' AND
	p.created_date >= '2023-06-01' AND p.created_date < '2023-07-01';
```

---

### 2 List All Active Physical Products

**Business Problem:**
Merchandising teams often need a list of all active physical products to manage logistics, warehousing, inventory planning, and shipping operations efficiently.

**Fields to Retrieve:**

* PRODUCT_ID
* PRODUCT_TYPE_ID
* INTERNAL_NAME

```sql
SELECT
  product_id,
  product_type_id,
  internal_name
FROM product
JOIN product_type USING(product_type_id)
WHERE is_physical = 'Y' and is_virtual <> 'Y';
```

---

### 3 Products Missing NetSuite ID

**Business Problem:**
A product cannot be synchronized with NetSuite unless it has a valid NetSuite ID. Operations and integration teams need a list of products that are missing this identifier so they can be created or updated in NetSuite.

**Fields to Retrieve:**

* PRODUCT_ID
* INTERNAL_NAME
* PRODUCT_TYPE_ID
* NETSUITE_ID

```sql
SELECT
  p.product_id,
  p.product_type_id,
  p.internal_name,
  gi.good_identification_type_id AS netsuite_id
FROM product p
LEFT JOIN good_identification gi
ON gi.product_id = p.product_id AND gi.good_identification_type_id ="ERP_ID"
where p.product_type_id="FINISHED_GOOD" AND gi.good_identification_type_id IS NULL;
```

---

### 4 Product IDs Across Systems

**Business Problem:**
To ensure seamless synchronization between multiple systems such as Shopify, HotWax Commerce, and NetSuite, teams need visibility into the unique identifiers assigned to each product in every platform.

**Fields to Retrieve:**

* PRODUCT_ID
* SHOPIFY_ID
* HOTWAX_ID
* ERP_ID / NETSUITE_ID

```sql
SELECT
    product_id,
    CASE WHEN good_identification_type_id = 'SHOPIFY_PROD_ID'
        THEN id_value ELSE NULL END as SHOPIFY_ID,
    CASE WHEN good_identification_type_id = 'ERP_ID'
        THEN id_value ELSE NULL END as ERP_ID
FROM good_identification;
```

---

### 5 Completed Orders in August 2023

**Business Problem:**
Business analysts need a list of all orders completed during August 2023 to evaluate sales performance, fulfillment efficiency, and operational trends.

**Fields to Retrieve:**

* PRODUCT_ID
* PRODUCT_TYPE_ID
* PRODUCT_STORE_ID
* TOTAL_QUANTITY
* INTERNAL_NAME
* FACILITY_ID
* EXTERNAL_ID
* FACILITY_TYPE_ID
* ORDER_HISTORY_ID
* ORDER_ID
* ORDER_ITEM_SEQ_ID
* SHIP_GROUP_SEQ_ID

```sql
SELECT
    oi.product_id,
    p.product_type_id,
    oh.product_store_id,
    oi.quantity AS TOTAL_QUANTITY,
    p.internal_name,
    oisg.facility_id,
    oh.external_id,
    f.facility_type_id,
    ohis.order_history_id,
    oh.order_id,
    oi.order_item_seq_id,
    oisg.ship_group_seq_id
FROM order_header oh
JOIN order_item oi ON oh.order_id = oi.order_id
JOIN product p ON oi.product_id = p.product_id
LEFT JOIN order_item_ship_group oisg ON oi.order_id = oisg.order_id
    AND oi.ship_group_seq_id = oisg.ship_group_seq_id
LEFT JOIN facility f ON oisg.facility_id = f.facility_id
LEFT JOIN order_history ohis ON oh.order_id = ohis.order_id 
    AND ohis.event_type_enum_id = 'ORDER_COMPLETED'
LEFT JOIN order_status os ON oh.order_id = os.order_id
WHERE
    os.status_id = 'ORDER_COMPLETED' 
    AND os.status_datetime >= '2023-08-01' 
    AND os.status_datetime < '2023-09-01';
```

---

### 6 Newly Created Sales Orders and Payment Methods

**Business Problem:**
Finance and fraud prevention teams require visibility into newly created sales orders along with their payment methods to support payment reconciliation, auditing, and fraud monitoring activities.

**Fields to Retrieve:**

* ORDER_ID
* TOTAL_AMOUNT
* PAYMENT_METHOD
* SHOPIFY_ORDER_ID

```sql
SELECT
    oh.order_id,
    oh.grand_total as TOTAL_AMOUNT,
    opp.payment_method_type_id as PAYMENT_METHOD,
    oh.external_id as SHOPIFY_ORDER_ID
FROM order_header oh
LEFT JOIN order_payment_preference opp ON oh.order_id = opp.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id = 'ORDER_CREATED';
```

---

### 7 Payment Captured but Not Shipped

**Business Problem:**
Finance teams need to identify orders where payment has already been captured but shipment has not yet occurred. These orders may require investigation to prevent fulfillment delays and ensure proper revenue recognition.

**Fields to Retrieve:**

* ORDER_ID
* ORDER_STATUS
* PAYMENT_STATUS
* SHIPMENT_STATUS

```sql
SELECT
    oh.order_id AS ORDER_ID,
    oh.status_id AS ORDER_STATUS,
    opp.status_id AS PAYMENT_STATUS,
    s.status_id AS SHIPMENT_STATUS
FROM order_header oh
JOIN order_payment_preference opp ON oh.order_id = opp.order_id
LEFT JOIN shipment s ON oh.order_id = s.primary_order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id NOT IN ('ORDER_COMPLETED', 'ORDER_CANCELLED')
    AND opp.status_id = 'PAYMENT_SETTLED'
    AND (s.status_id IS NULL OR s.status_id <> 'SHIPMENT_SHIPPED');
```

---

### 8 Orders Completed Hourly

**Business Problem:**
Operations teams want to analyze order completion patterns throughout the day to optimize staffing, warehouse workloads, and fulfillment processes.

**Fields to Retrieve:**

* TOTAL_ORDERS
* HOUR

```sql
SELECT 
    COUNT(DISTINCT os.order_id) AS TOTAL_ORDERS,
    HOUR(os.status_datetime) AS HOUR
FROM order_status os
JOIN order_header oh ON os.order_id = oh.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND os.status_id = 'ORDER_COMPLETED'
GROUP BY HOUR(os.status_datetime)
ORDER BY HOUR;
```

---

### 9 BOPIS Orders Revenue (Last Year)
oh
**Business Problem:**
BOPIS (Buy Online, Pickup In Store) is an important omnichannel retail strategy. Finance and business teams need to understand the volume and revenue generated from BOPIS orders during the previous year.

**Fields to Retrieve:**

* TOTAL_ORDERS
* TOTAL_REVENUE

```sql
SELECT
    COUNT(DISTINCT oh.order_id) AS TOTAL_ORDERS,
    SUM(oi.quantity * oi.unit_price) AS TOTAL_REVENUE
FROM order_header oh
JOIN order_item oi ON oh.order_id = oi.order_id
JOIN order_item_ship_group oisg ON oh.order_id = oisg.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oisg.shipment_method_type_id = 'STOREPICKUP'
    AND oh.status_id <> 'ORDER_CANCELLED'
    AND oi.status_id <> 'ITEM_CANCELLED'
    AND oh.entry_date >= '2025-06-22'
    AND oh.entry_date < '2026-06-22'
```

---

### 10 Canceled Orders (Last Month)

**Business Problem:**
Merchandising and customer experience teams need insight into canceled orders from the previous month to identify trends, operational issues, and common cancellation reasons.

**Fields to Retrieve:**

* TOTAL_ORDERS
* CANCELLATION_REASON

```sql
SELECT
    COUNT(oh.order_id) AS TOTAL_ORDERS,
    os.change_reason AS CANCELLATION_REASON
FROM order_header oh
JOIN order_status os ON oh.order_id = os.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND os.status_id = 'ITEM_CANCELLED'
    AND os.status_datetime >= '2026-05-01'
    AND os.status_datetime < '2026-06-01'
GROUP BY os.change_reason
```

---

### 11 Product Threshold Value

**Business Problem:**
Retailers often define threshold values for products sold online to prevent overselling and maintain inventory accuracy. Inventory management teams need visibility into these configured thresholds.

**Fields to Retrieve:**

* PRODUCT_ID
* THRESHOLD

```sql
SELECT
    pf.product_id AS PRODUCT_ID,
    pf.facility_id AS FACILITY_ID,
    pf.minimum_stock AS THRESHOLD
FROM product_facility pf;
```
