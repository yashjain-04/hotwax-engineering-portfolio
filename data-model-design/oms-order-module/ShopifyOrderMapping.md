# Shopify to OMS Order Mapping (Based on ShopifyOrderServices.java)

## OrderHeader
| Shopify Order Field | OMS OrderHeader Field | Notes |
| :--- | :--- | :--- |
| `id` | `external_id` | Mapped as `SHOPIFY_ORD_ID` in `OrderIdentification` and directly to `external_id` |
| `name` | `order_name` | E.g. "#1001" |
| `created_at` | `order_date` | Converted to OFBiz timestamp |
| `currency` | `currency_uom` | Direct mapping of currency code |
| `total_price` | `grand_total` | Converted to BigDecimal |
| `source_name` | `sales_channel_enum_id` | Mapped via `ShopifyShopTypeMapping` |
| `financial_status`/`closed_at`/`cancelled_at` | `status_id` | Resolved to `ORDER_CREATED`, `ORDER_COMPLETED`, or `ORDER_CANCELLED` |

## OrderItem
| Shopify Line Item Field | OMS OrderItem Field | Notes |
| :--- | :--- | :--- |
| `id` | `external_id` | Store Shopify line item ID as `itemExternalId` |
| `variant_id` / `product_id` | `product_id` | Looks up the HotWax `productId` associated with the Shopify variant |
| `quantity` (adjusted) | `quantity` | Shopify quantity minus any refunded/cancelled quantities |
| `price` | `unit_price` / `unit_list_price` | |
| `title` / `variant_title` | `item_description` | Used if the product needs to be created on the fly |
| `fulfillment_status` | `status_id` | Resolved to `ITEM_CREATED`, `ITEM_COMPLETED`, or `ITEM_CANCELLED` |

## OrderRole
| Shopify Field | OMS OrderRole Field | Notes |
| :--- | :--- | :--- |
| `customer` | `party_id` | Looks up or creates customer via `customerDataSetup` |
| N/A | `role_type_id` | Uses `CUSTOMER` role type |

## OrderContactMech
| Shopify Field | OMS OrderContactMech Field | Notes |
| :--- | :--- | :--- |
| `billing_address` | `contact_mech_id` | Created via `createPostalAddress` and linked with `BILLING_LOCATION` |
| `phone` | `contact_mech_id` | Created via `createTelecomNumber` and linked as phone |
| `email` | `contact_mech_id` | Created via `createContactMech` and linked as email |

## OrderItemShipGroup
| Shopify Field | OMS OrderItemShipGroup Field | Notes |
| :--- | :--- | :--- |
| `location_id` / `line_items.properties` | `facility_id` | Mapped to fulfillment facility ID or `_NA_` / default facility |
| `shipping_address` | `contact_mech_id` | Linked with `SHIPPING_LOCATION` |
| `shipping_address.phone` | `telecom_contact_mech_id` | Associated telecom number |
| `shipping_lines[0].title` | `shipment_method_type_id` | Mapped to `shipmentMethodTypeId` and `carrierPartyId` |

## OrderItemShipGroupAssoc
| Shopify Field | OMS OrderItemShipGroupAssoc Field | Notes |
| :--- | :--- | :--- |
| `line_items` | `order_item_seq_id` | Line items are grouped and added to their respective ship groups |
| `line_items.quantity` (adjusted) | `quantity` | |

## OrderAdjustment
| Shopify Field | OMS OrderAdjustment Field | Notes |
| :--- | :--- | :--- |
| `total_tax` | `amount` | Creates a `SALES_TAX` adjustment |
| `shipping_lines` | `amount` | Creates `SHIPPING_CHARGES` adjustments |
| `discount_allocations` | `amount` | Creates `EXT_PROMO_ADJUSTMENT` adjustments for item discounts |
