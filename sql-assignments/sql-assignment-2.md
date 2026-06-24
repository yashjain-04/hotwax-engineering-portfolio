# SQL Assignment 2 Answers

## 5 Mixed Party + Order Queries

### 5.1 Shipping Addresses for October 2023 Orders

**Business Problem:**
Customer Service might need to verify addresses for orders placed or completed in October 2023. This helps ensure shipments are delivered correctly and prevents address-related issues.

**Fields to Retrieve:**

* ORDER_ID
* PARTY_ID (Customer ID)
* CUSTOMER_NAME (or FIRST_NAME / LAST_NAME)
* STREET_ADDRESS
* CITY
* STATE_PROVINCE
* POSTAL_CODE
* COUNTRY_CODE
* ORDER_STATUS
* ORDER_DATE

```sql
SELECT
    oh.order_id,
    per.party_id,
    COALESCE(CONCAT(per.first_name, ' ', per.last_name), pg.group_name) AS CUSTOMER_NAME,
    pa.address1,
    pa.city,
    pa.state_province_geo_id,
    pa.postal_code,
    pa.country_geo_id,
    oh.status_id,
    oh.entry_date
FROM order_header oh
JOIN order_role orole ON oh.order_id = orole.order_id
LEFT JOIN person per ON orole.party_id = per.party_id
LEFT JOIN party_group pg ON orole.party_id = pg.party_id
JOIN order_contact_mech ocm ON oh.order_id = ocm.order_id
JOIN postal_address pa ON ocm.contact_mech_id = pa.contact_mech_id
WHERE orole.role_type_id = 'PLACING_CUSTOMER'
  AND oh.order_type_id = 'SALES_ORDER'
  AND oh.entry_date >= '2023-10-1'
  AND oh.entry_date < '2023-11-1'
```

---

### 5.2 Orders from New York

**Business Problem:**
Companies often want region-specific analysis to plan local marketing, staffing, or promotions in certain areas—here, specifically, New York.

**Fields to Retrieve:**

* ORDER_ID
* CUSTOMER_NAME
* STREET_ADDRESS (or shipping address detail)
* CITY
* STATE_PROVINCE
* POSTAL_CODE
* TOTAL_AMOUNT
* ORDER_DATE
* ORDER_STATUS

```sql
SELECT
    oh.order_id,
    COALESCE(CONCAT(per.first_name, ' ', per.last_name), pg.group_name) AS CUSTOMER_NAME,
    pa.address1,
    pa.city,
    pa.state_province_geo_id,
    pa.postal_code,
    oh.grand_total,
    oh.entry_date,
    oh.status_id
FROM order_header oh
JOIN order_role orole ON oh.order_id = orole.order_id
LEFT JOIN person per ON orole.party_id = per.party_id
LEFT JOIN party_group pg ON orole.party_id = pg.party_id
JOIN order_contact_mech ocm ON oh.order_id = ocm.order_id
JOIN postal_address pa ON ocm.contact_mech_id = pa.contact_mech_id
WHERE pa.state_province_geo_id = 'NY'
  AND oh.order_type_id = 'SALES_ORDER'
  AND orole.role_type_id = 'PLACING_CUSTOMER'
```

---

### 5.3 Top-Selling Product in New York

**Business Problem:**
Merchandising teams need to identify the best-selling product(s) in a specific region (New York) for targeted restocking or promotions.

**Fields to Retrieve:**

* PRODUCT_ID
* INTERNAL_NAME
* TOTAL_QUANTITY_SOLD
* CITY / STATE (within New York region)
* REVENUE (optionally, total sales amount)

```sql
SELECT
    p.product_id,
    p.internal_name,
    SUM(oi.quantity) AS TOTAL_QUANTITY_SOLD,
    CONCAT(pa.city, ' / ', pa.state_province_geo_id) AS 'CITY / STATE',
    SUM(oi.quantity * oi.unit_price) AS REVENUE
FROM product p
JOIN order_item oi ON p.product_id = oi.product_id
  AND oi.status_id <> 'ITEM_CANCELLED'
JOIN order_header oh ON oi.order_id = oh.order_id
  AND oh.order_type_id = 'SALES_ORDER'
  AND oh.status_id <> 'ORDER_CANCELLED'
JOIN order_contact_mech ocm ON oh.order_id = ocm.order_id
  AND ocm.contact_mech_purpose_type_id = 'SHIPPING_LOCATION'
JOIN postal_address pa ON ocm.contact_mech_id = pa.contact_mech_id 
WHERE pa.state_province_geo_id = 'NY'
GROUP BY 
    p.product_id,
    p.internal_name,
    pa.city,
    pa.state_province_geo_id
ORDER BY TOTAL_QUANTITY_SOLD DESC
```

---

## 7 Sales & Revenue Analysis

### 7.3 Store-Specific (Facility-Wise) Revenue

**Business Problem:**
Different physical or online stores (facilities) may have varying levels of performance. The business wants to compare revenue across facilities for sales planning and budgeting.

**Fields to Retrieve:**

* FACILITY_ID
* FACILITY_NAME
* TOTAL_ORDERS
* TOTAL_REVENUE
* DATE_RANGE

```sql
SELECT
    f.facility_id AS FACILITY_ID,
    f.facility_name AS FACILITY_NAME,
    COUNT(DISTINCT oh.order_id) AS TOTAL_ORDERS,
    SUM(oi.quantity * oi.unit_price) AS TOTAL_REVENUE,
    CONCAT( MIN(DATE(oh.entry_date)),' to ',MAX(DATE(oh.entry_date)) ) AS DATE_RANGE
FROM facility f
JOIN order_item_ship_group oisg
    ON f.facility_id = oisg.facility_id
JOIN order_item oi
    ON oisg.order_id = oi.order_id
    AND oisg.ship_group_seq_id = oi.ship_group_seq_id
JOIN order_header oh
    ON oi.order_id = oh.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id <> 'ORDER_CANCELLED'
    AND oi.status_id <> 'ITEM_CANCELLED'
GROUP BY f.facility_id, f.facility_name
ORDER BY TOTAL_REVENUE DESC;
```

---

## 8 Inventory Management & Transfers

### 8.1 Lost and Damaged Inventory

**Business Problem:**
Warehouse managers need to track "shrinkage" such as lost or damaged inventory to reconcile physical vs. system counts.

**Fields to Retrieve:**

* INVENTORY_ITEM_ID
* PRODUCT_ID
* FACILITY_ID
* QUANTITY_LOST_OR_DAMAGED
* REASON_CODE (Lost, Damaged, Expired, etc.)
* TRANSACTION_DATE

```sql
SELECT
    ii.inventory_item_id,
    ii.product_id,
    ii.facility_id,
    ABS(iid.quantity_on_hand_diff) AS QUANTITY_LOST_OR_DAMAGED,
    iid.reason_enum_id AS REASON_CODE,
    iid.effective_date AS TRANSACTION_DATE
FROM inventory_item ii
JOIN inventory_item_detail iid ON ii.inventory_item_id = iid.inventory_item_id
WHERE iid.quantity_on_hand_diff < 0
  AND iid.reason_enum_id IS NOT NULL
```

---

### 8.2 Low Stock or Out of Stock Items Report

**Business Problem:**
Avoiding out-of-stock situations is critical. This report flags items that have fallen below a certain reorder threshold or have zero available stock.

**Fields to Retrieve:**

* PRODUCT_ID
* PRODUCT_NAME
* FACILITY_ID
* QOH (Quantity on Hand)
* ATP (Available to Promise)
* REORDER_THRESHOLD
* DATE_CHECKED

```sql
SELECT 
    p.product_id,
    p.product_name,
    ii.facility_id,
    COALESCE(SUM(ii.quantity_on_hand_total), 0) AS QOH,
    COALESCE(SUM(ii.available_to_promise_total), 0) AS ATP,
    COALESCE(pf.minimum_stock, 0) AS REORDER_THRESHOLD,
    CURRENT_DATE AS DATE_CHECKED
FROM product_facility pf 
JOIN product p ON pf.product_id = p.product_id
LEFT JOIN inventory_item ii ON p.product_id = ii.product_id
  AND pf.facility_id= ii.facility_id
GROUP BY p.product_id, 
    p.product_name, 
    ii.facility_id, 
    pf.minimum_stock
HAVING COALESCE(SUM(ii.available_to_promise_total), 0) <= COALESCE(pf.minimum_stock, 0)
```

---

### 8.3 Retrieve the Current Facility (Physical or Virtual) of Open Orders

**Business Problem:**
The business wants to know where open orders are currently assigned, whether in a physical store or a virtual facility (e.g., a distribution center or online fulfillment location).

**Fields to Retrieve:**

* ORDER_ID
* ORDER_STATUS
* FACILITY_ID
* FACILITY_NAME
* FACILITY_TYPE_ID

```sql
SELECT DISTINCT
    oh.order_id,
    oh.status_id,
    f.facility_id,
    f.facility_name,
    f.facility_type_id
FROM order_header oh
JOIN order_item_ship_group oisg ON oh.order_id = oisg.order_id
LEFT JOIN facility f ON oisg.facility_id = f.facility_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id NOT IN('ORDER_COMPLETED', 'ORDER_CANCELLED', 'ORDER_REJECTED')
```

---

### 8.4 Items Where QOH and ATP Differ

**Business Problem:**
Sometimes the Quantity on Hand (QOH) doesn’t match the Available to Promise (ATP) due to pending orders, reservations, or data discrepancies. This needs review for accurate fulfillment planning.

**Fields to Retrieve:**

* PRODUCT_ID
* FACILITY_ID
* QOH (Quantity on Hand)
* ATP (Available to Promise)
* DIFFERENCE (QOH - ATP)

```sql
SELECT
    ii.product_id,
    ii.facility_id,
    ii.quantity_on_hand_total,
    ii.available_to_promise_total,
    (ii.quantity_on_hand_total - ii.available_to_promise_total) AS DIFFERENCE
FROM inventory_item ii
GROUP BY ii.product_id, ii.facility_id
HAVING ii.quantity_on_hand_total <> ii.available_to_promise_total
ORDER BY product_id
```

---

### 8.5 Order Item Current Status Changed Date-Time

**Business Problem:**
Operations teams need to audit when an order item’s status (e.g., from "Pending" to "Shipped") was last changed, for shipment tracking or dispute resolution.

**Fields to Retrieve:**

* ORDER_ID
* ORDER_ITEM_SEQ_ID
* CURRENT_STATUS_ID
* STATUS_CHANGE_DATETIME
* CHANGED_BY

```sql
SELECT
    oi.order_id,
    oi.order_item_seq_id,
    oi.status_id,
    os.status_datetime,
    os.status_user_login
FROM order_item oi
JOIN order_status os ON oi.order_id = os.order_id
    AND oi.order_item_seq_id = os.order_item_seq_id
    AND oi.status_id = os.status_id
WHERE os.status_datetime = (
    SELECT
        MAX(os2.status_datetime)
        FROM order_status os2
        WHERE os2.order_id = os.order_id
            AND os2.order_item_seq_id = os.order_item_seq_id
            AND os2.status_id = os.status_id
);
```

---

### 8.6 Total Orders by Sales Channel

**Business Problem:**
Marketing and sales teams want to see how many orders come from each channel (e.g., web, mobile app, in-store POS, marketplace) to allocate resources effectively.

**Fields to Retrieve:**

* SALES_CHANNEL
* TOTAL_ORDERS
* TOTAL_REVENUE
* REPORTING_PERIOD

```sql
SELECT
    COALESCE(oh.sales_channel_enum_id, 'UNKNOWN_SALES_CHANNEL') AS SALES_CHANNEL,
    COUNT(DISTINCT oh.order_id) AS Total_Orders,
    SUM(oi.quantity * oi.unit_price) AS Total_Revenue,
    CONCAT(MIN(DATE(oh.entry_date)), ' - ', MAX(DATE(oh.entry_date))) AS Reporting_Period
FROM order_header oh
JOIN order_item oi ON oh.order_id = oi.order_id
WHERE oh.order_type_id = 'SALES_ORDER'
  AND oh.status_id <> 'ORDER_CANCELLED'
  AND oi.status_id <> 'ITEM_CANCELLED'
GROUP BY oh.sales_channel_enum_id;
```

