# Data Access Logic

## Part 2: Data Access Logic

### 1. Create a New Product Record

```text
function createProduct(inputData):
    if inputData.sku is empty:
        throw Error("SKU is required")
    
    existingSku = query good_identification where id_value = inputData.sku and good_identification_type_enum_id = 'SKU'
    if existingSku is not null:
        throw Error("Product with this SKU already exists")

    newProductId = generateUniqueId()
    insert into product (product_id, product_name, description)
    values (newProductId, inputData.product_name, inputData.description)

    insert into good_identification (good_identification_type_enum_id, product_id, id_value)
    values ('SKU', newProductId, inputData.sku)

    if inputData.upc is provided:
        insert into good_identification (good_identification_type_enum_id, product_id, id_value)
        values ('UPC', newProductId, inputData.upc)

    if inputData.price is provided:
        insert into product_price (product_id, product_price_type_enum_id, product_price_purpose_enum_id, currency_uom_id, from_date, price)
        values (newProductId, 'LIST_PRICE', 'PURCHASE', 'USD', currentTime(), inputData.price)

    if inputData.category_id is provided:
        insert into product_category_member (product_category_id, product_id, from_date)
        values (inputData.category_id, newProductId, currentTime())

    return newProductId
```

### 2. Retrieve a Product Record

```text
function getProductBySku(sku):
    skuRecord = query good_identification where id_value = sku and good_identification_type_enum_id = 'SKU'
    if skuRecord is null:
        return null

    productRecord = query product where product_id = skuRecord.product_id
    productPrice = query product_price where product_id = productRecord.product_id order by from_date desc limit 1
    features = query product_feature_appl join product_feature where product_id = productRecord.product_id and thru_date is null
    categories = query product_category_member join product_category where product_id = productRecord.product_id and thru_date is null
    
    return {
        product: productRecord,
        price: productPrice,
        features: features,
        categories: categories
    }
```

### 3. Update an Existing Product Record

```text
function updateProduct(sku, updateData):
    productData = getProductBySku(sku)
    if productData is null:
        throw Error("Product not found")

    productId = productData.product.product_id

    if updateData.product_name is provided:
        update product set product_name = updateData.product_name where product_id = productId

    if updateData.description is provided:
        update product set description = updateData.description where product_id = productId

    if updateData.price is provided:
        insert into product_price (product_id, product_price_type_enum_id, product_price_purpose_enum_id, currency_uom_id, from_date, price)
        values (productId, 'LIST_PRICE', 'PURCHASE', 'USD', currentTime(), updateData.price)

    if updateData.features is provided:
        for each feature in updateData.features:
            existingFeature = query product_feature where description = feature.value and product_feature_type_enum_id = feature.type
            if existingFeature is null:
                featureId = generateUniqueId()
                insert into product_feature (product_feature_id, product_feature_type_enum_id, description)
                values (featureId, feature.type, feature.value)
            else:
                featureId = existingFeature.product_feature_id
            
            insert into product_feature_appl (product_id, product_feature_id, from_date)
            values (productId, featureId, currentTime())

    return getProductBySku(sku)
```

### 4. Delete a Product Record (Soft Delete)

```text
function deleteProduct(sku):
    productData = getProductBySku(sku)
    if productData is null:
        throw Error("Product not found")

    productId = productData.product.product_id
    
    -- soft delete: set discontinuation date instead of deleting the record
    update product set sales_discontinuation_date = currentTime() where product_id = productId

    return true
```

## Part 3: Shopify Integration Pseudo-code

### Fetch and Store Shopify Products

```text
function syncShopifyProducts():
    response = httpRequest("GET", "https://notnaked.myshopify.com/admin/api/2024-01/products.json")
    products = response.json.products

    for shopifyProduct in products:
        productId = null
        
        -- check if this Shopify product already exists in our system
        existingProduct = query good_identification where id_value = shopifyProduct.id and good_identification_type_enum_id = 'SHOPIFY_PROD_ID'
        
        if existingProduct is null:
            productId = generateUniqueId()
            insert into product (product_id, product_type_enum_id, product_name, description)
            values (productId, 'VIRTUAL_PRODUCT', shopifyProduct.title, shopifyProduct.body_html)
            
            insert into good_identification (good_identification_type_enum_id, product_id, id_value)
            values ('SHOPIFY_PROD_ID', productId, shopifyProduct.id)
        else:
            productId = existingProduct.product_id
            update product set product_name = shopifyProduct.title, description = shopifyProduct.body_html where product_id = productId

        -- map product_type to a category
        if shopifyProduct.product_type is not empty:
            existingCategory = query product_category where category_name = shopifyProduct.product_type
            if existingCategory is null:
                categoryId = generateUniqueId()
                insert into product_category (product_category_id, category_name)
                values (categoryId, shopifyProduct.product_type)
            else:
                categoryId = existingCategory.product_category_id
            
            existingMember = query product_category_member where product_category_id = categoryId and product_id = productId
            if existingMember is null:
                insert into product_category_member (product_category_id, product_id, from_date)
                values (categoryId, productId, currentTime())

        -- process each variant
        for variant in shopifyProduct.variants:
            variantProductId = null
            existingVariant = query good_identification where id_value = variant.id and good_identification_type_enum_id = 'SHOPIFY_VAR_ID'
            
            if existingVariant is null:
                variantProductId = generateUniqueId()
                insert into product (product_id, product_type_enum_id, product_name)
                values (variantProductId, 'VARIANT_PRODUCT', variant.title)
                
                insert into good_identification (good_identification_type_enum_id, product_id, id_value)
                values ('SHOPIFY_VAR_ID', variantProductId, variant.id)
                
                if variant.sku is not empty:
                    insert into good_identification (good_identification_type_enum_id, product_id, id_value)
                    values ('SKU', variantProductId, variant.sku)
                
                if variant.barcode is not empty:
                    insert into good_identification (good_identification_type_enum_id, product_id, id_value)
                    values ('UPCA', variantProductId, variant.barcode)
                
                -- link variant to parent product
                insert into product_assoc (product_id, product_id_to, product_assoc_type_enum_id, from_date)
                values (productId, variantProductId, 'PRODUCT_VARIANT', currentTime())
            else:
                variantProductId = existingVariant.product_id
            
            -- store list price
            insert into product_price (product_id, product_price_type_enum_id, product_price_purpose_enum_id, currency_uom_id, from_date, price)
            values (variantProductId, 'LIST_PRICE', 'PURCHASE', 'USD', currentTime(), variant.price)

            -- store compare_at_price as original price if available
            if variant.compare_at_price is not null:
                insert into product_price (product_id, product_price_type_enum_id, product_price_purpose_enum_id, currency_uom_id, from_date, price)
                values (variantProductId, 'ORIGINAL_PRICE', 'PURCHASE', 'USD', currentTime(), variant.compare_at_price)

            -- map variant options to product features
            for i in [1, 2, 3]:
                optionValue = variant["option" + i]
                if optionValue is not null:
                    optionName = shopifyProduct.options[i-1].name    -- e.g., "Size", "Color"
                    
                    existingFeature = query product_feature where description = optionValue and product_feature_type_enum_id = optionName
                    if existingFeature is null:
                        featureId = generateUniqueId()
                        insert into product_feature (product_feature_id, product_feature_type_enum_id, description)
                        values (featureId, optionName, optionValue)
                    else:
                        featureId = existingFeature.product_feature_id
                    
                    insert into product_feature_appl (product_id, product_feature_id, from_date)
                    values (variantProductId, featureId, currentTime())
```
