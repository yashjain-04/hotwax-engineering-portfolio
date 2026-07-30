# Shopify Product Mapping

## Product Level Mapping

| Shopify Product API Field | UDM Entity | UDM Field | Notes |
| :--- | :--- | :--- | :--- |
| `id` | `good_identification` | `id_value` | Stored with type = 'SHOPIFY_PROD_ID' |
| `title` | `product` | `product_name` | |
| `body_html` | `product` | `description` | |
| `vendor` | `product` | `internal_name` | Can also be stored as a party association if needed |
| `product_type` | `product_category` | `category_name` | Creates or maps to an existing category record |
| `tags` | `product_category_member` | - | Each tag can be mapped as a separate category membership |
| `status` | `product` | `sales_discontinuation_date` | If status is "archived" or "draft", set discontinuation date |

## Variant Level Mapping (Shopify `variants`)

| Shopify Variant Field | UDM Entity | UDM Field | Notes |
| :--- | :--- | :--- | :--- |
| `id` | `good_identification` | `id_value` | Stored with type = 'SHOPIFY_VAR_ID' |
| `sku` | `good_identification` | `id_value` | Stored with type = 'SKU' |
| `barcode` | `good_identification` | `id_value` | Stored with type = 'UPCA' (UPC) or 'GTIN' |
| `price` | `product_price` | `price` | Stored with type = 'LIST_PRICE' |
| `compare_at_price` | `product_price` | `price` | Stored with type = 'ORIGINAL_PRICE' |
| `option1`, `option2`, `option3` | `product_feature` | `description` | Linked via `product_feature_appl` |

## Handling Multi-valued Fields

**Variants:**
Shopify groups all variations under a single parent product. In UDM, the parent product is stored as a `VIRTUAL_PRODUCT` and each Shopify variant becomes a separate `VARIANT_PRODUCT`. They are linked using the `product_assoc` table with type `PRODUCT_VARIANT`.

For example, a Shopify product "T-Shirt" with 3 variants (S/Red, M/Red, L/Red) becomes:
- 1 virtual product record for "T-Shirt"
- 3 variant product records, each linked to the virtual product via `product_assoc`
- Each variant gets its own pricing in `product_price`

**Options:**
Shopify's `options` array defines the variation types (e.g., Size, Color). The `option1`, `option2`, `option3` values on each variant map to `product_feature` records. The feature type comes from `options[].name` (e.g., "Size") and the value comes from `option1` (e.g., "Large"). These are linked to variant products through `product_feature_appl`.

**Images:**
Images can be stored in a `product_content` entity (not created in this schema) where each image URL is associated with a product or variant via `variant_ids`.

**Data Type Handling:**
- Shopify `id` fields are numeric (bigint) → stored as VARCHAR in `good_identification.id_value`
- Shopify `price` is a string (e.g., "19.99") → converted to DECIMAL(18,2) before storing
- Shopify `tags` is a comma-separated string → split into individual values and stored as separate category memberships
- Shopify `body_html` contains HTML → stored as-is in the TEXT `description` column
