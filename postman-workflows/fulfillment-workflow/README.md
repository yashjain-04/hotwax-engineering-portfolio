# Order Fulfillment Automated Workflow

This directory contains a Postman collection and environment configured to automate the core order fulfillment lifecycle (Pick, Pack, and Ship) using the nextgen-maarg routing APIs.

## Overview

Instead of manually executing individual API calls and copy-pasting identifiers, this Postman collection uses test scripts to dynamically capture data from responses and pass it into subsequent requests. 

The collection executes the following sequence automatically:
1. **Pick (`createOrderFulfillmentWave`):** Assigns a picker to an order item and generates a Picklist. The script automatically captures the newly generated `shipmentId` and `orderId` as collection variables.
2. **Pack (`/shipments/{shipmentId}/pack`):** Utilizes the dynamically captured `shipmentId` and `orderId` to complete the packing process.
3. **Ship (`/shipments/{shipmentId}/ship`):** Utilizes the same `shipmentId` to mark the package as shipped and conclude the workflow.

## Repository Contents

* `Poorti.postman_collection.json`: The Postman collection containing the three chained API requests and test scripts.
* `Poorti variable.postman_environment.json`: The environment file containing necessary base URLs or authentication variables (if applicable).
