# Poorti Component — Detailed Service Change Deep Dive

This document provides a thorough, step-by-step explanation of every service that was created, updated, or deleted in the recent Poorti branch merge. For each service, we cover: **What changed**, **Why it changed**, and **How it works internally**.

---

## Part 1: Pick Profile Optimization

### 1.1 `create#PickList` — [DELETED]

**File:** `service/co/hotwax/poorti/picking/PickProfileServices.xml`  
**Author:** Deepak Dixit

#### What It Used To Do (Before)

This service was a middleman. When `run#PickProfile` found eligible orders, it would pass those orders to `create#PickList`. This service would then:

1. Loop through each order and query the `OrderItemAndShipGroup` view-entity to find all `ITEM_APPROVED` items.
2. Group the items by `facilityId` (because each warehouse gets its own picklist).
3. Call `create#OrderFulfillmentWave` once per facility to actually create the Picklist.

#### Why It Was Deleted

The new version of `run#PickProfile` now handles batching, item lookups, and wave creation all by itself in a single optimized loop. Having this separate service added an extra layer of indirection — orders were being passed around between services unnecessarily. Removing it makes the code simpler and faster.

---

### 1.2 `run#PickProfile` — [UPDATED]

**File:** `service/co/hotwax/poorti/picking/PickProfileServices.xml`  
**Author:** Deepak Dixit

This is the most heavily modified service in the entire diff. Almost every line inside its `<actions>` block was rewritten.

#### What Changed — Input Parameters

**Before:**
```xml
<parameter name="profileId" required="true"/>
<parameter name="maxItemCount" type="Integer"/>
```

**After:**
```xml
<parameter name="profileId" required="true"/>
<parameter name="orderId"/>
<parameter name="shipGroupSeqId"/>
```

- `maxItemCount` was removed. It was never fully implemented (there was even a TODO comment saying "Handle the max order count").
- `orderId` and `shipGroupSeqId` were added. This allows a user to run the profile for a **single specific order** instead of the entire batch. This is useful for testing or re-processing a specific order.

#### What Changed — Facility Resolution

**Before:** The facility was not resolved from the profile group. There was a TODO comment saying *"Need to get the facility id first"*.

**After:** The service now reads `facilityId` directly from the parent `PickProfileGroup` entity:
```xml
<set field="facilityId" from="pickProfileGroup.facilityId"/>
```
This was possible because a new `facilityId` field was added to the `PickProfileGroup` entity in this same diff. This means each profile group is now tied to a specific warehouse/store.

#### What Changed — How Orders Are Found (The Big Rewrite)

**Before (View-Entity Approach):**
```groovy
def orders = ec.entity.find("co.hotwax.poorti.picking.ReadyToPickOrder")
    .searchFormMap(inputFields, null, null, null, false)
    .selectFields(["orderId", "shipGroupSeqId"])
    .distinct(true)
    .havingCondition(...)
```
This used Moqui's standard `entity.find()` on a view-entity called `ReadyToPickOrder`. The view-entity joined `OrderHeader`, `OrderItemShipGroup`, `OrderItem`, `Facility`, `FacilityType`, `PostalAddress`, and `Shipment` tables together. The framework would generate SQL automatically, but the generated SQL was not optimized — it used `LEFT JOIN` + `IS NULL` patterns and `GROUP BY` with `HAVING COUNT > 0`, which are slow on large datasets.

**After (Native SQL Template Approach):**
```groovy
Writer writer = new StringWriter()
ec.resourceFacade.template(templateLoc, writer)
String sql = writer.toString()
try (eli = ec.entityFacade.sqlFind(sql, null, "co.hotwax.poorti.picking.ReadyToPickOrder", fieldList)) {
    // iterate results...
}
```
The service now uses a Freemarker SQL template (`ReadyToPickOrders.sql.ftl`) that generates hand-tuned SQL. The key performance improvements in this SQL are:

- **`NOT EXISTS` instead of `LEFT JOIN + IS NULL`:** To exclude orders that already have a non-cancelled Shipment, the query uses `NOT EXISTS (SELECT 1 FROM SHIPMENT ...)`. This is faster because the database can stop scanning the moment it finds one match.
- **`EXISTS` instead of `JOIN + GROUP BY + HAVING`:** To check if at least one `ITEM_APPROVED` order item exists, it uses `EXISTS (SELECT 1 FROM ORDER_ITEM ...)`. This avoids the overhead of counting all items.
- **`NOT EXISTS` for hold tasks:** A new check was added — orders that have an open `WorkEffort` task of type `RESOLVE_ONHOLD_ORDER` (e.g., from address validation) are automatically excluded from picking.

#### What Changed — Batching Logic (Completely New)

**Before:** The old code would load ALL eligible orders into memory at once, count them, and then pass the entire list to `create#PickList`. If there were 10,000 orders, all 10,000 were held in memory simultaneously.

**After:** The new code uses a streaming cursor (`EntityListIterator`) and processes orders in configurable chunks:

1. It reads `PickProfileAction` records to find `PPAT_MIN_SIZE` and `PPAT_MAX_SIZE` configurations.
2. It defaults the batch size to `picklistMaxSize` or `200` if not configured.
3. As it reads orders from the SQL cursor one by one, it adds them to a `batch` list.
4. When the batch reaches `batchSize`, it:
   - Looks up the `ITEM_APPROVED` `OrderItem` records for each order in the batch.
   - Calls `create#OrderFulfillmentWave` with `requireNewTransaction(true)` to create a Picklist.
   - Resets the batch to an empty list and continues.
5. After the cursor is exhausted, if there are leftover orders in the batch:
   - If `picklistMinSize` is not configured, or the leftovers meet the minimum, it creates one more Picklist.
   - If the leftovers are **below** the minimum, it logs a message and skips them.

#### Why It Changed

**Performance:** The old view-entity approach generated slow, unoptimized SQL. On databases with millions of orders, it could take minutes or time out entirely.

**Memory Safety:** Loading all orders at once could crash the JVM with an `OutOfMemoryError`. The streaming + batching approach keeps memory usage constant regardless of volume.

**Hold Order Awareness:** The old query had no concept of "on-hold" orders. The new SQL explicitly excludes orders with open `RESOLVE_ONHOLD_ORDER` tasks, which ties into the new Address Validation feature.

---

### 1.3 `get#PickProfileOrderCount` — [CREATED]

**File:** `service/co/hotwax/poorti/picking/PickProfileServices.xml`  
**Author:** Deepak Dixit

#### What It Does

This service answers the question: *"How many orders are ready to be picked right now for this profile?"* It reuses the exact same SQL template (`ReadyToPickOrders.sql.ftl`) as `run#PickProfile`, but sets a flag `queryCount=true` which changes the SQL from `SELECT orderId, shipGroupSeqId ...` to `SELECT COUNT(*) AS orderCount ...`.

#### How It Works Step-by-Step

1. Validates that the `PickProfile` exists and is in `PICK_PROF_ACTIVE` status.
2. Loads `PickProfileCondition` records and separates them into filters and sort fields.
3. If `groupByFields` is not passed by the caller, it defaults to the sort fields from the profile configuration. If `deliveryDays` is in the group-by list, it automatically adds `shipmentMethodTypeId` as well (because delivery days only make sense in the context of a specific shipping method).
4. Sets `queryCount = true` and renders the SQL template.
5. Executes the SQL using `ec.entityFacade.sqlFind()` and iterates the results, building a list of `[orderCount: N, field1: value1, ...]` maps.

#### Why It Was Created

Before this service, the only way to know how many orders a profile would process was to actually run it. This is risky — what if a misconfigured profile suddenly processes 50,000 orders? This preview service lets warehouse managers see the numbers first and decide whether to proceed.

#### Where It Is Called

Exposed as a REST API endpoint: `POST /poorti/v1/pickProfile/{profileId}/orderCount`

---

## Part 2: Inventory Synchronization

### 2.1 `reset#ProductFacilityInventory` — [CREATED]

**File:** `service/co/hotwax/poorti/FulfillmentServices.xml`  
**Author:** Aditya Patel

#### What It Does

This is a new wrapper service that acts as the single entry point for external inventory resets. It takes in raw stock numbers from an external system and handles all the internal logic: resolving identifiers, looking up the current inventory, calculating the variance, and passing the result to the persistence layer.

#### How It Works Step-by-Step

**Step 1 — Input Validation (3 checks):**
- Exactly one of `externalATP` or `externalQOH` must be provided (not both, not neither).
- Either `facilityId` or `externalFacilityId` must be provided.
- Either `productId` or both `productIdentType` + `productIdentValue` must be provided.

**Step 2 — Resolve Internal Records:**
Calls `findOrCreate#FacilityInventoryItem` which:
- Maps the external facility ID to an internal `facilityId` (if only external ID was given).
- Maps the product identifier (like a UPC or SKU) to an internal `productId`.
- Finds or creates the `InventoryItem` record for that product at that facility.

**Step 3 — Calculate the Variance:**
```xml
<!-- If external QOH was provided -->
<if condition="externalQOH != null">
    <set field="quantityOnHandDiff" from="externalQOH - inventoryItemQOH"/>
    <set field="availableToPromiseDiff" from="quantityOnHandDiff"/>
</if>
<!-- If external ATP was provided -->
<if condition="externalATP != null">
    <set field="availableToPromiseDiff" from="externalATP - inventoryItemATP"/>
    <set field="quantityOnHandDiff" from="availableToPromiseDiff"/>
</if>
```
Notice the logic: if external QOH is provided, the QOH diff drives both values. If external ATP is provided, the ATP diff drives both. This means only one dimension of inventory is adjusted per call.

**Step 4 — Persist:**
Calls `create#ExternalInventoryReset` with all the calculated values.

#### Why It Was Created

**Before:** The old `create#ExternalInventoryReset` service did everything — it resolved identifiers, calculated variances, AND saved to the database. But it required ALL parameters as `required="true"`, meaning the caller always had to provide `externalFacilityId`, `productIdentType`, `productIdentValue`, `externalATP`, `externalQOH`, AND `unitCost`. This was rigid and inflexible.

**After:** The new wrapper is flexible. You can pass either `facilityId` OR `externalFacilityId`. You can pass either `productId` OR `productIdentType`+`productIdentValue`. You can pass either `externalATP` OR `externalQOH`. The service figures out the rest.

---

### 2.2 `create#ExternalInventoryReset` — [UPDATED]

**File:** `service/co/hotwax/poorti/FulfillmentServices.xml`  
**Author:** Aditya Patel

#### What Changed — Parameters

**Before (old in-parameters):**
```
resetDateResourceId (required), externalFacilityId (required),
productIdentType (required), productIdentValue (required),
externalATP (required), externalQOH (required), unitCost (required)
```

**After (new in-parameters):**
```
resetDateResourceId, facilityId (required), productId (required),
inventoryItemId (required), externalFacilityId, productIdentType,
productIdentValue, reason (default: VAR_EXT_RESET), description,
externalATP, externalQOH, inventoryItemATP, inventoryItemQOH,
availableToPromiseDiff, quantityOnHandDiff
```

Key differences:
- `unitCost` was **removed** entirely. The entity field `availableToPromiseDiff` and `quantityOnHandDiff` were added instead.
- `facilityId`, `productId`, and `inventoryItemId` are now **required** — because the caller (the new wrapper) has already resolved them.
- `reason` and `description` were added so each reset log can explain *why* the inventory was adjusted.

#### What Changed — Actions

**Before:**
```xml
<if condition="quantityOnHandDiff != 0 || availableToPromiseDiff != 0">
    <service-call name="create#InventoryItemDetail" .../>
</if>
```
The old code conditionally created an `InventoryItemDetail` record only if the diff was non-zero. It also did NOT pass any reason or description.

**After:**
```xml
<check-errors/>
<set field="resetItemId" from="externalInventoryResetResult.resetItemId"/>
<service-call name="create#InventoryItemDetail" in-map="[
    inventoryItemId: inventoryItemId,
    quantityOnHandDiff: quantityOnHandDiff,
    availableToPromiseDiff: availableToPromiseDiff,
    reasonEnumId: reason,
    description: description,
    resetItemId: resetItemId
]"/>
<check-errors/>
```
Now it **always** creates the detail record (even for zero diff), includes the `reasonEnumId` and `description`, and links it back to the reset via `resetItemId`. It also has proper `<check-errors/>` calls after each service invocation to halt execution if something fails.

#### Why It Changed

This service was refactored to become a **pure persistence layer**. All the "smart" logic (resolving identifiers, calculating diffs) was moved up to the new `reset#ProductFacilityInventory` wrapper. This makes the code cleaner and easier to test — each service has one clear job.

---

## Part 3: Automated Address Validation

### 3.1 `validate#Address` — [CREATED]

**File:** `service/co/hotwax/poorti/shipping/ShippingServices.xml`  
**Author:** Arun Patidar

#### What It Does

This is the core integration service that talks to an external shipping gateway (Unigate) to verify whether a physical address is real and deliverable.

#### How It Works Step-by-Step

**Step 1 — Load Gateway Configuration:**
- Finds the `SystemMessageRemote` record for `UNIGATE_CONFIG` (the API URL and credentials).
- Finds the `ShippingCarrierConfig` for the given `productStoreId` + `carrierPartyId` + `facilityId`. If no facility-specific config exists, it falls back to the store-level default.

**Step 2 — Resolve Addresses:**
The service is flexible — it accepts addresses in three different ways:
- `contactMechIds` (a List) — for batch validation of multiple addresses at once.
- `contactMechId` (a single ID) — for validating one address by its database ID.
- `addressMap` (a raw Map) — for validating an address that isn't stored in the database yet.

For each address, it looks up the `PostalAddressAndGeo` view to get `address1`, `city`, `stateGeoCodeAlpha2`, `postalCode`, `countryGeoCodeAlpha2`, and assigns a `clientReferenceId` (so the response can be matched back to the request).

**Step 3 — Call the External API:**
Uses Moqui's `RestClient` to POST to `{unigateUrl}/shipment/address/validate` with the JSON payload rendered from `ValidateAddressRequest.ftl`.

**Step 4 — Parse Response:**
Iterates through `responseMap.results` and builds a `resultsMap` keyed by `clientReferenceId`. For single-address validations, it also sets top-level `isValid` and `validationSummary` output parameters for backward compatibility.

#### Why It Was Created

Without this, the system had no way to check if a customer's address was real before trying to ship to it. Bad addresses cause label generation failures, carrier surcharges, and returned packages — all of which cost money and delay fulfillment.

---

### 3.2 `validate#SalesOrderAddress` — [CREATED]

**File:** `service/co/hotwax/poorti/shipping/ShippingServices.xml`  
**Author:** Arun Patidar

#### What It Does

This is an orchestration service that automatically validates all shipping addresses on a newly created Sales Order and takes action if any address is invalid.

#### How It Works Step-by-Step

**Step 1 — Guard Checks:**
- Finds the `OrderHeader` and checks that `statusId` is either `ORDER_CREATED` or `ORDER_APPROVED`. If the order is already cancelled or completed, the service simply returns without doing anything.

**Step 2 — Find Active Ship Groups:**
- Queries `OrderItem` to get distinct `shipGroupSeqId` values (only ship groups that actually have items).
- Loads the corresponding `OrderItemShipGroup` records.

**Step 3 — Batch Address Validation:**
- Extracts unique `contactMechId` values from all ship groups.
- Calls `validate#Address` with the full list of `contactMechIds` for a single batch API call (instead of calling the API once per address).

**Step 4 — Log the API Response:**
- Creates a `CommunicationEvent` record with `communicationEventTypeId = 'API_COMMUNICATION'` and `subject = 'Address Validation'`, storing the entire JSON response as the `content`.
- Links this `CommunicationEvent` to the Order via `CommunicationEventOrder`.

**Step 5 — Handle Invalid Addresses:**
For each ship group whose address failed validation:
- Extracts the `issue` and `suggestion` from the validation response.
- Creates a `WorkEffort` record with:
  - `workEffortTypeId = 'RESOLVE_ONHOLD_ORDER'`
  - `workEffortPurposeTypeId = 'INVALID_ADDRESS'`
  - `statusId = 'TASK_CREATED'`
  - `description` containing the issue details (truncated to 255 chars for the database column).
  - `locationDesc` containing the standardized address suggestion as JSON.
- Links the `WorkEffort` to the Order via `OrderHeaderWorkEffort` (including `shipGroupSeqId`).

#### Why It Was Created

This automates the entire exception management flow. Before, bad addresses were only discovered when a warehouse worker tried to print a shipping label and it failed. Now, the system catches them immediately at order creation time, creates a visible task for Customer Service, and the `run#PickProfile` SQL query automatically excludes these orders from picking (via the `NOT EXISTS` check on `RESOLVE_ONHOLD_ORDER` work efforts).

#### Where It Is Called

Triggered automatically by the SECA rule `ValidateOrderAddressOnSalesOrderCreate`:
```xml
<seca service="co.hotwax.oms.order.OrderServices.create#SalesOrder"
      when="tx-commit" priority="7">
    <condition><expression>orderId</expression></condition>
    <actions>
        <service-call name="validate#SalesOrderAddress"
                      in-map="[orderId: orderId]" async="true" disable-authz="true"/>
    </actions>
</seca>
```
Note: It runs `async="true"` so it doesn't slow down order creation, and `disable-authz="true"` because it's a background system process.

---

## Part 4: Return Shipping Labels

### 4.1 `get#ReturnShippingLabel` — [CREATED]

**File:** `service/co/hotwax/poorti/shipping/ShippingServices.xml`  
**Author:** Arun Patidar

#### What It Does

This is the largest new service in the diff (~600 lines). It generates a real shipping label for a customer return by calling the Unigate shipping gateway API.

#### How It Works Step-by-Step

**Step 1 — Validate the Return:**
- Looks up `ReturnHeader` and checks that `statusId = 'RETURN_ACCEPTED'`.
- Gets the `destinationFacilityId` (the warehouse receiving the return).
- Finds `ReturnItem` records to get the associated `orderId`.

**Step 2 — Resolve Carrier & Configuration:**
- Gets the `OrderHeader` for `productStoreId`.
- Resolves `carrierPartyId` from the input or falls back to the order's carrier.
- Looks up `ShippingCarrierConfig` for the product store, carrier, and facility.
- Looks up billing account number via `get#BillingAccountNumber`.

**Step 3 — Build Origin Address (Customer):**
- The origin of a return label is the customer's address. It resolves this from `returnHeader.originContactMechId` or falls back to the order's `OrderItemShipGroup.contactMechId`.
- Looks up the `PostalAddressAndGeo` and phone number for this address.

**Step 4 — Build Destination Address (Warehouse):**
- Looks up the warehouse's `PRIMARY_LOCATION` contact mech and `PRIMARY_PHONE`.
- Also checks for regional data like municipality, department, district, and canton (for Latin American shipping).

**Step 5 — Calculate Package Weight & Dimensions:**
- Iterates through all non-cancelled `ReturnItem` records.
- For each item, looks up the `Product` entity to get `shippingWeight`, `shippingDepth`, `shippingWidth`, `shippingHeight`.
- Converts weight to the carrier's preferred UOM if needed (via `convert#Uom`).
- Calculates total package dimensions using a stacking model (max length, max width, cumulative height).
- Defaults dimensions to 10x10x10 if product data is missing.

**Step 6 — Call the Shipping Gateway API:**
- Renders the `GetLabelRequest.ftl` template with all the collected data.
- POSTs to `{unigateUrl}/shipment/label`.
- Parses the response for `referenceNumber` (tracking code), `labelImageUrl`, and `imageBytes` (Base64 label image).

**Step 7 — Save the Label:**
- Checks if a `ReturnLabel` entity already exists for this `returnId`.
- If it exists, updates it with the new tracking code and label image.
- If it doesn't exist, creates a new `ReturnLabel` record.
- If the API call failed, returns an error message.

#### Why It Was Created

This enables the complete RMA (Return Merchandise Authorization) flow. Without it, Customer Service had to manually log into FedEx/UPS portals to generate return labels. Now they can do it with a single API call from the OMS.

#### Where It Is Called

Exposed as a REST API endpoint: `POST /poorti/v1/returns/{returnId}/shippingLabel`

---

### 4.2 `print#ReturnLabel` — [CREATED]

**File:** `service/co/hotwax/poorti/FulfillmentServices.xml`  
**Author:** Arun Patidar

#### What It Does

A lightweight wrapper that takes one or more `returnId` values and redirects the browser to an Apache FOP PDF rendering endpoint.

#### How It Works

1. Accepts `returnId` as a `List` type (supports both single and bulk printing).
2. Builds a URL query string: `returnId=12345&returnId=67890`.
3. Sets the filename to either `ReturnLabel-{id}.pdf` (single) or `bulk-ReturnLabel.pdf` (multiple).
4. Calls `ec.web.sendResourceResponse()` to redirect to the FOP screen at `fop/apps/pdf/PrintReturnLabel`.

The FOP screen (`PrintReturnLabel.xml`) then renders the `ReturnLabel.xsl-fo.ftl` template, which loops through each `returnId` and includes `ReturnLabelContent.xsl-fo.ftl` for each one.

#### Where It Is Called

Exposed as a REST API endpoint: `GET /poorti/v1/ReturnLabel.pdf`

---

### 4.3 `encode#ReturnLabelBase64` — [CREATED]

**File:** `service/co/hotwax/poorti/FulfillmentServices.xml`  
**Author:** Arun Patidar

#### What It Does

A utility service that reads the binary label image from the `ReturnLabel` entity and converts it to a Base64 string that can be embedded in a PDF.

#### How It Works

1. Looks up `ReturnLabel` by `returnId`.
2. If `labelImage` (binary blob) exists, converts it: `Base64.encoder.encodeToString((returnLabel.labelImage).getBinaryStream().bytes)`.
3. If `labelImageUrl` exists, passes it through as-is.
4. Returns a `labels` list containing one map with either `labelImage` (Base64 string) or `labelImageUrl`.

Note: `authenticate="false"` because this service is called from within a Freemarker template during PDF rendering, where there is no authenticated user session.

#### Where It Is Called

Called dynamically inside the `ReturnLabelContent.xsl-fo.ftl` Freemarker template:
```ftl
<#assign labels = ec.service.sync().name("co.hotwax.poorti.FulfillmentServices.encode#ReturnLabelBase64")
    .parameter("returnId", returnId).call().labels!/>
```
