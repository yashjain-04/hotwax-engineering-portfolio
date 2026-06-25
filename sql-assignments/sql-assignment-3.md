# SQL Assignment 3 Answers

## 1 Completed Sales Orders (Physical Items)

**Business Problem:**
Merchants need to track only physical items (requiring shipping and fulfillment) for logistics and shipping-cost analysis.

**Fields to Retrieve:**

* ORDER_ID
* ORDER_ITEM_SEQ_ID
* PRODUCT_ID
* PRODUCT_TYPE_ID
* SALES_CHANNEL_ENUM_ID
* ORDER_DATE
* ENTRY_DATE
* STATUS_ID
* STATUS_DATETIME
* ORDER_TYPE_ID
* PRODUCT_STORE_ID

```sql
SELECT
    oh.order_id,
    oi.order_item_seq_id,
    p.product_id,
    p.product_type_id,
    oh.sales_channel_enum_id,
    oh.order_date,
    oh.entry_date,
    oh.status_id,
    (
        SELECT MAX(os.status_datetime)
        FROM order_status os
        WHERE os.order_id = oi.order_id
            AND os.order_item_seq_id = oi.order_item_seq_id
            AND os.status_id = oi.status_id
    ) AS STATUS_DATETIME,
    oh.order_type_id,
    oh.product_store_id
FROM order_header oh
JOIN order_item oi ON oh.order_id = oi.order_id
JOIN product p ON oi.product_id = p.product_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND p.product_type_id NOT IN ('DIGITAL_GOOD', 'SERVICE')
    AND oh.status_id = 'ORDER_COMPLETED'
    AND oi.status_id = 'ITEM_COMPLETED'
```

---

## 2 Completed Return Items

**Business Problem:**
Customer service and finance often need insights into returned items to manage refunds, replacements, and inventory restocking.

**Fields to Retrieve:**

* RETURN_ID
* ORDER_ID
* PRODUCT_STORE_ID
* STATUS_DATETIME
* ORDER_NAME
* FROM_PARTY_ID
* RETURN_DATE
* ENTRY_DATE
* RETURN_CHANNEL_ENUM_ID

```sql
SELECT
    rh.return_id,
    oh.order_id,
    oh.product_store_id,
    (
        SELECT MAX(rs.status_datetime)
        FROM return_status rs 
        WHERE ri.return_id = rs.return_id
            AND ri.return_item_seq_id = rs.return_item_seq_id
            AND ri.status_id = rs.status_id
    ) AS STATUS_DATETIME,
    oh.order_name,
    rh.from_party_id,
    rh.return_date,
    oh.entry_date,
    rh.return_channel_enum_id
FROM return_item ri
JOIN return_header rh ON ri.return_id = rh.return_id
LEFT JOIN order_header oh ON ri.order_id = oh.order_id
WHERE rh.status_id = 'RETURN_COMPLETED'
    AND ri.status_id = 'RETURN_COMPLETED'

```

---

## 3 Single-Return Orders (Last Month)

**Business Problem:**
The merchandising team needs a list of orders that only have one return.

**Fields to Retrieve:**

* PARTY_ID
* FIRST_NAME

```sql
SELECT
    rh.from_party_id,
    per.first_name
FROM return_item ri
JOIN return_header rh ON ri.return_id = rh.return_id
JOIN person per ON rh.from_party_id = per.party_id
WHERE rh.entry_date >= CURRENT_DATE - INTERVAL 1 MONTH
GROUP BY ri.order_id, rh.from_party_id, per.first_name
HAVING COUNT(ri.return_item_seq_id) = 1; 
```

---

## 4 Returns and Appeasements

**Business Problem:**
The retailer needs the total amount of items that were returned as well as how many appeasements were issued.

**Fields to Retrieve:**

* TOTAL RETURNS
* RETURN $ TOTAL
* TOTAL APPEASEMENTS
* APPEASEMENTS $ TOTAL

```sql
SELECT 
    returns.total_returns AS "TOTAL_RETURNS",
    returns.return_total_amount AS "RETURN_TOTAL_AMOUNT",
    appeasement.total_appeasements AS "TOTAL_APPEASEMENTS",
    appeasement.appeasement_total_amount AS "APPEASEMENTS_TOTAL_AMOUNT"
FROM 
    (SELECT 
        COALESCE(SUM(ri.return_quantity), 0) AS total_returns,
        COALESCE(SUM(ri.return_price * ri.return_quantity), 0) AS return_total_amount
     FROM return_header rh
     JOIN return_item ri ON rh.return_id = ri.return_id
     WHERE rh.status_id = 'RETURN_COMPLETED' 
       AND ri.status_id = 'RETURN_COMPLETED'
    ) AS returns
CROSS JOIN 
    (SELECT 
        COALESCE(COUNT(oa.order_adjustment_id), 0) AS total_appeasements,
        COALESCE(ABS(SUM(oa.amount)), 0) AS appeasement_total_amount
     FROM order_adjustment oa
     WHERE oa.order_adjustment_type_id = 'APPEASEMENT'
    ) AS appeasement;
```

---

## 5 Detailed Return Information

**Business Problem:**
Certain teams need granular return data (reason, date, refund amount) for analyzing return rates, identifying recurring issues, or updating policies.

**Fields to Retrieve:**

* RETURN_ID
* ENTRY_DATE
* RETURN_ADJUSTMENT_TYPE_ID (refund type, store credit, etc.)
* AMOUNT
* COMMENTS
* ORDER_ID
* ORDER_DATE
* RETURN_DATE
* PRODUCT_STORE_ID

```sql
SELECT
    ra.return_id,
    rh.entry_date,
    ra.return_adjustment_type_id,
    ra.amount,
    ra.comments,
    ri.order_id,
    oh.order_date,
    rh.return_date,
    oh.product_store_id
FROM return_adjustment ra
JOIN return_header rh ON ra.return_id = rh.return_id
LEFT JOIN return_item ri ON ra.return_id = ri.return_id
    AND ra.return_item_seq_id = ri.return_item_seq_id
LEFT JOIN order_header oh ON ri.order_id = oh.order_id
WHERE rh.status_id = 'RETURN_COMPLETED'
```

---

## 6 Orders with Multiple Returns

**Business Problem:**
Analyzing orders with multiple returns can identify potential fraud, chronic issues with certain items, or inconsistent shipping processes.

**Fields to Retrieve:**

* ORDER_ID
* RETURN_ID
* RETURN_DATE
* RETURN_REASON
* RETURN_QUANTITY

```sql
SELECT
    ri.order_id,
    ri.return_id,
    rh.return_date,
    ri.return_reason_id,
    ri.return_quantity
FROM return_item ri
JOIN return_header rh ON ri.return_id = rh.return_id
WHERE ri.order_id IN(
    SELECT order_id
    FROM order_item
    WHERE order_id IS NOT NULL
    GROUP BY order_id
    HAVING COUNT(return_item_seq_id) > 1
)
GROUP BY ri.order_id, rh.return_date
)
```

---

## 7 Store with Most One-Day Shipped Orders (Last Month)

**Business Problem:**
Identify which facility (store) handled the highest volume of one-day shipping orders in the previous month, useful for operational benchmarking.

**Fields to Retrieve:**

* FACILITY_ID
* FACILITY_NAME
* TOTAL_ONE_DAY_SHIP_ORDERS
* REPORTING_PERIOD

```sql
SELECT
    f.facility_id,
    f.facility_name,
    COUNT(DISTINCT oh.order_id) AS TOTAL_ONE_DAY_SHIP_ORDERS,
    CONCAT(CURRENT_DATE - INTERVAL 1 MONTH, ' to ', CURRENT_DATE)
FROM order_header oh
JOIN order_item_ship_group oisg ON oh.order_id = oisg.order_id
JOIN facility f ON oisg.facility_id = f.facility_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id <> 'ORDER_CANCELLED'
    AND oisg.shipment_method_type_id IN ('NEXT_DAY')
    AND oh.entry_date >= CURRENT_DATE - INTERVAL 1 MONTH
GROUP BY f.facility_id, f.facility_name
ORDER BY TOTAL_ONE_DAY_SHIP_ORDERS DESC
```

---

## 8 List of Warehouse Pickers

**Business Problem:**
Warehouse managers need a list of employees responsible for picking and packing orders to manage shifts, productivity, and training needs.

**Fields to Retrieve:**

* PARTY_ID (or Employee ID)
* NAME (First/Last)
* ROLE_TYPE_ID (e.g., "WAREHOUSE_PICKER")
* FACILITY_ID (assigned warehouse)
* STATUS (active or inactive employee)

```sql
SELECT
    per.party_id,
    CONCAT(per.first_name, ' ', per.last_name),
    pr.role_type_id,
    fp.facility_id,
    CASE 
      WHEN fp.thru_date IS NULL OR fp.thru_date > CURRENT_TIMESTAMP THEN 'ACTIVE' 
      ELSE 'INACTIVE' 
    END AS STATUS
FROM facility_party fp
JOIN person per ON fp.party_id = per.party_id
JOIN party_role pr ON per.party_id = pr.party_id
WHERE pr.role_type_id IN ('WAREHOUSE_PICKER', 'PICKER', 'PACKER')
```

---

## 9 Total Facilities That Sell the Product

**Business Problem:**
Retailers want to see how many (and which) facilities (stores, warehouses, virtual sites) currently offer a product for sale.

**Fields to Retrieve:**

* PRODUCT_ID
* PRODUCT_NAME (or INTERNAL_NAME)
* FACILITY_COUNT (number of facilities selling the product)
* (Optionally) a list of FACILITY_IDs if more detail is needed

```sql
SELECT
    p.product_id,
    p.product_name,
    COUNT(pf.facility_id) AS FACILITY_COUNT
FROM product p
LEFT JOIN product_facility pf ON p.product_id = pf.product_id
WHERE p.sales_discontinuation_date IS NULL 
    OR p.sales_discontinuation_date > CURRENT_TIMESTAMP
GROUP BY p.product_id, p.product_name
ORDER BY FACILITY_COUNT DESC, p.product_id
```

---

## 10 Total Items in Various Facilities

**Business Problem:**
Retailers need to study the relation of inventory levels of products to the type of facility it's stored at. Retrieve all inventory levels for products at locations and include the facility type ID. Do not retrieve facilities that are of type Virtual.

**Fields to Retrieve:**

* PRODUCT_ID
* FACILITY_ID
* FACILITY_TYPE_ID
* QOH (Quantity on Hand)
* ATP (Available to Promise)

```sql
SELECT
    ii.product_id,
    ii.facility_id,
    f.facility_type_id,
    SUM(ii.quantity_on_hand_total),
    SUM(ii.available_to_promise_total)
FROM inventory_item ii
JOIN facility f ON ii.facility_id = f.facility_id
JOIN facility_type ft ON ft.facility_type_id = f.facility_type_id
WHERE (ft.parent_type_id <> 'VIRTUAL_FACILITY'
    OR ft.parent_type_id IS NULL)
    AND f.facility_type_id <> 'VIRTUAL_FACILITY'
GROUP BY ii.product_id,
    ii.facility_id,
    f.facility_type_id
```

---

## 11 Transfer Orders Without Inventory Reservation

**Business Problem:**
When transferring stock between facilities, the system should reserve inventory. If it isn’t reserved, the transfer may fail or oversell.

**Fields to Retrieve:**

* TRANSFER_ORDER_ID
* FROM_FACILITY_ID
* TO_FACILITY_ID
* PRODUCT_ID
* REQUESTED_QUANTITY
* RESERVED_QUANTITY
* TRANSFER_DATE
* STATUS

```sql
SELECT 
    oh.order_id AS TRANSFER_ORDER_ID,
    oh.origin_facility_id AS FROM_FACILITY_ID,
    oisg.facility_id AS TO_FACILITY_ID,
    oi.product_id AS PRODUCT_ID,
    oi.quantity AS REQUESTED_QUANTITY,
    COALESCE(SUM(oisgir.quantity), 0) AS RESERVED_QUANTITY,
    oh.order_date AS TRANSFER_DATE,
    oh.status_id AS STATUS
FROM order_header oh
JOIN order_item oi ON oh.order_id = oi.order_id
JOIN order_item_ship_group oisg ON oh.order_id = oisg.order_id
LEFT JOIN order_item_ship_grp_inv_res oisgir 
    ON oi.order_id = oisgir.order_id 
    AND oi.order_item_seq_id = oisgir.order_item_seq_id 
    AND oisg.ship_group_seq_id = oisgir.ship_group_seq_id
WHERE oh.order_type_id = 'TRANSFER_ORDER'
    AND oh.status_id IN ('ORDER_CREATED', 'ORDER_APPROVED')
    AND oi.status_id NOT IN ('ITEM_CANCELLED', 'ITEM_COMPLETED')
GROUP BY
    oh.order_id,
    oh.origin_facility_id,
    oisg.facility_id,
    oi.product_id,
    oi.quantity,
    oh.order_date,
    oh.status_id
ORDER BY oh.order_date ASC;
```

---

## 12 Orders Without Picklist

**Business Problem:**
A picklist is necessary for warehouse staff to gather items. Orders missing a picklist might be delayed and need attention.

**Fields to Retrieve:**

* ORDER_ID
* ORDER_DATE
* ORDER_STATUS
* FACILITY_ID
* DURATION (How long has the order been assigned at the facility)

```sql
SELECT 
    oh.order_id AS ORDER_ID,
    oh.order_date AS ORDER_DATE,
    oh.status_id AS ORDER_STATUS,
    oisg.facility_id AS FACILITY_ID,
    DATEDIFF(CURRENT_TIMESTAMP, oh.order_date) AS DURATION_IN_DAYS
FROM order_header oh
JOIN order_item_ship_group oisg ON oh.order_id = oisg.order_id
LEFT JOIN picklist_item pli 
    ON oisg.order_id = pli.order_id 
    AND oisg.ship_group_seq_id = pli.ship_group_seq_id
WHERE oh.order_type_id = 'SALES_ORDER'
    AND oh.status_id = 'ORDER_APPROVED'
    AND pli.order_id IS NULL
ORDER BY DURATION_IN_DAYS DESC;
```

