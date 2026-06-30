/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery

/**
 * findProduct - Searches for products using filters on productId, productName,
 * price range, and product features. Supports case-insensitive and partial matching.
 */
def findProduct() {
    String productId = parameters.productId
    String productName = parameters.productName
    BigDecimal minPrice = parameters.minPrice ? parameters.minPrice as BigDecimal : null
    BigDecimal maxPrice = parameters.maxPrice ? parameters.maxPrice as BigDecimal : null
    String productFeatureId = parameters.productFeatureId
    String productFeatureTypeId = parameters.productFeatureTypeId
    String productCategoryId = parameters.productCategoryId

    List productConditions = []

    // Build dynamic conditions on the Product entity
    if (UtilValidate.isNotEmpty(productId)) {
        productConditions.add(EntityCondition.makeCondition("productId",
                EntityOperator.LIKE, "%" + productId + "%"))
    }

    if (UtilValidate.isNotEmpty(productName)) {
        productConditions.add(EntityCondition.makeCondition(
                EntityCondition.makeCondition("productName",
                        EntityOperator.LIKE, "%" + productName + "%"),
                EntityOperator.OR,
                EntityCondition.makeCondition("internalName",
                        EntityOperator.LIKE, "%" + productName + "%")
        ))
    }

    // Query products
    List products
    if (!productConditions.isEmpty()) {
        def productCond = EntityCondition.makeCondition(productConditions, EntityOperator.AND)
        products = EntityQuery.use(delegator)
                .from("Product")
                .where(productCond)
                .queryList()
    } else {
        products = EntityQuery.use(delegator)
                .from("Product")
                .queryList()
    }

    // Filter and enrich results
    List productList = []
    for (GenericValue product : products) {
        String prodId = product.getString("productId")
        Map productMap = [:]
        productMap.productId = prodId
        productMap.productName = product.getString("productName")
        productMap.internalName = product.getString("internalName")
        productMap.description = product.getString("description")
        productMap.isVirtual = product.getString("isVirtual")
        productMap.isVariant = product.getString("isVariant")

        // Get price (LIST_PRICE)
        GenericValue price = EntityQuery.use(delegator)
                .from("ProductPrice")
                .where("productId", prodId,
                        "productPriceTypeId", "LIST_PRICE",
                        "productPricePurposeId", "PURCHASE")
                .filterByDate()
                .queryFirst()

        if (price != null) {
            BigDecimal priceValue = price.getBigDecimal("price")
            productMap.price = priceValue
            productMap.currencyUomId = price.getString("currencyUomId")

            // Apply price range filters
            if (minPrice != null && priceValue.compareTo(minPrice) < 0) {
                continue
            }
            if (maxPrice != null && priceValue.compareTo(maxPrice) > 0) {
                continue
            }
        } else {
            // If price filtering is requested but product has no price, skip it
            if (minPrice != null || maxPrice != null) {
                continue
            }
            productMap.price = null
            productMap.currencyUomId = null
        }

        // Get category membership
        List categoryMembers = EntityQuery.use(delegator)
                .from("ProductCategoryMember")
                .where("productId", prodId)
                .filterByDate()
                .queryList()

        // Apply category filter
        if (UtilValidate.isNotEmpty(productCategoryId)) {
            boolean foundCategory = false
            for (GenericValue member : categoryMembers) {
                if (productCategoryId == member.getString("productCategoryId")) {
                    foundCategory = true
                    break
                }
            }
            if (!foundCategory) {
                continue
            }
        }

        List categoryIds = []
        List categoryNames = []
        for (GenericValue member : categoryMembers) {
            String catId = member.getString("productCategoryId")
            categoryIds.add(catId)
            GenericValue category = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryId", catId)
                    .queryOne()
            if (category != null) {
                categoryNames.add(category.getString("categoryName"))
            }
        }
        productMap.categoryIds = categoryIds
        productMap.categoryNames = categoryNames

        // Get features
        List featureAppls = EntityQuery.use(delegator)
                .from("ProductFeatureAndAppl")
                .where("productId", prodId)
                .filterByDate()
                .queryList()

        // Apply feature filters
        if (UtilValidate.isNotEmpty(productFeatureId)) {
            boolean foundFeature = false
            for (GenericValue fa : featureAppls) {
                if (productFeatureId == fa.getString("productFeatureId")) {
                    foundFeature = true
                    break
                }
            }
            if (!foundFeature) {
                continue
            }
        }

        if (UtilValidate.isNotEmpty(productFeatureTypeId)) {
            boolean foundFeatureType = false
            for (GenericValue fa : featureAppls) {
                if (productFeatureTypeId == fa.getString("productFeatureTypeId")) {
                    foundFeatureType = true
                    break
                }
            }
            if (!foundFeatureType) {
                continue
            }
        }

        List features = []
        for (GenericValue fa : featureAppls) {
            Map featureMap = [:]
            featureMap.productFeatureId = fa.getString("productFeatureId")
            featureMap.productFeatureTypeId = fa.getString("productFeatureTypeId")
            featureMap.description = fa.getString("description")
            features.add(featureMap)
        }
        productMap.features = features

        productList.add(productMap)
    }

    Map result = success()
    result.productList = productList
    return result
}

/**
 * createProduct - Creates a new product with unique name enforcement.
 * Checks for existing products with the same name using case-insensitive matching.
 * Also creates ProductPrice (LIST_PRICE) and ProductCategoryMember records.
 */
def createProduct() {
    String productName = parameters.productName
    String productCategoryId = parameters.productCategoryId
    BigDecimal price = parameters.price as BigDecimal
    String internalName = parameters.internalName ?: productName
    String description = parameters.description
    String productTypeId = parameters.productTypeId ?: "FINISHED_GOOD"
    String currencyUomId = parameters.currencyUomId ?: "USD"

    // Check for duplicate product name (case-insensitive)
    List existingProducts = EntityQuery.use(delegator)
            .from("Product")
            .where(EntityCondition.makeCondition("productName",
                    EntityOperator.LIKE, productName))
            .queryList()

    for (GenericValue existing : existingProducts) {
        if (productName.equalsIgnoreCase(existing.getString("productName"))) {
            return error("A product with the name '" + productName + "' already exists (Product ID: "
                    + existing.getString("productId") + "). Product names must be unique.")
        }
    }

    // Generate a product ID
    String productId = delegator.getNextSeqId("Product")

    // Create the Product entity
    def now = UtilDateTime.nowTimestamp()
    GenericValue product = delegator.makeValue("Product", [
            productId: productId,
            productTypeId: productTypeId,
            productName: productName,
            internalName: internalName,
            description: description,
            isVirtual: "N",
            isVariant: "N",
            createdDate: now,
            createdByUserLogin: userLogin.getString("userLoginId"),
            lastModifiedDate: now,
            lastModifiedByUserLogin: userLogin.getString("userLoginId")
    ])
    product.create()

    // Verify category exists
    GenericValue category = EntityQuery.use(delegator)
            .from("ProductCategory")
            .where("productCategoryId", productCategoryId)
            .queryOne()
    if (category == null) {
        return error("Product Category '" + productCategoryId + "' does not exist.")
    }

    // Create ProductCategoryMember
    GenericValue pcm = delegator.makeValue("ProductCategoryMember", [
            productCategoryId: productCategoryId,
            productId: productId,
            fromDate: now
    ])
    pcm.create()

    // Create ProductPrice (LIST_PRICE)
    GenericValue productPrice = delegator.makeValue("ProductPrice", [
            productId: productId,
            productPriceTypeId: "LIST_PRICE",
            productPricePurposeId: "PURCHASE",
            currencyUomId: currencyUomId,
            productStoreGroupId: "_NA_",
            fromDate: now,
            price: price,
            createdDate: now,
            createdByUserLogin: userLogin.getString("userLoginId"),
            lastModifiedDate: now,
            lastModifiedByUserLogin: userLogin.getString("userLoginId")
    ])
    productPrice.create()

    Map result = success()
    result.productId = productId
    return result
}

/**
 * updateProduct - Updates an existing product's price and/or feature assignments.
 * Verifies the product exists before applying any updates.
 */
def updateProduct() {
    String productId = parameters.productId
    String productName = parameters.productName
    String internalName = parameters.internalName
    String description = parameters.description
    BigDecimal price = parameters.price ? parameters.price as BigDecimal : null
    String productFeatureId = parameters.productFeatureId
    String productFeatureApplTypeId = parameters.productFeatureApplTypeId ?: "STANDARD_FEATURE"
    String currencyUomId = parameters.currencyUomId ?: "USD"

    // Verify product exists
    GenericValue product = EntityQuery.use(delegator)
            .from("Product")
            .where("productId", productId)
            .queryOne()

    if (product == null) {
        return error("Product '" + productId + "' does not exist. Cannot update.")
    }

    def now = UtilDateTime.nowTimestamp()

    // Update price if provided
    if (price != null) {
        // Expire existing LIST_PRICE
        List existingPrices = EntityQuery.use(delegator)
                .from("ProductPrice")
                .where("productId", productId,
                        "productPriceTypeId", "LIST_PRICE",
                        "productPricePurposeId", "PURCHASE")
                .filterByDate()
                .queryList()

        for (GenericValue existingPrice : existingPrices) {
            existingPrice.set("thruDate", now)
            existingPrice.set("lastModifiedDate", now)
            existingPrice.set("lastModifiedByUserLogin", userLogin.getString("userLoginId"))
            existingPrice.store()
        }

        // Create new price record
        GenericValue newPrice = delegator.makeValue("ProductPrice", [
                productId: productId,
                productPriceTypeId: "LIST_PRICE",
                productPricePurposeId: "PURCHASE",
                currencyUomId: currencyUomId,
                productStoreGroupId: "_NA_",
                fromDate: now,
                price: price,
                createdDate: now,
                createdByUserLogin: userLogin.getString("userLoginId"),
                lastModifiedDate: now,
                lastModifiedByUserLogin: userLogin.getString("userLoginId")
        ])
        newPrice.create()
    }

    // Add feature if provided
    if (UtilValidate.isNotEmpty(productFeatureId)) {
        // Verify feature exists
        GenericValue feature = EntityQuery.use(delegator)
                .from("ProductFeature")
                .where("productFeatureId", productFeatureId)
                .queryOne()

        if (feature == null) {
            return error("Product Feature '" + productFeatureId + "' does not exist.")
        }

        // Check if feature is already applied
        GenericValue existingAppl = EntityQuery.use(delegator)
                .from("ProductFeatureAppl")
                .where("productId", productId,
                        "productFeatureId", productFeatureId)
                .filterByDate()
                .queryFirst()

        if (existingAppl == null) {
            GenericValue featureAppl = delegator.makeValue("ProductFeatureAppl", [
                    productId: productId,
                    productFeatureId: productFeatureId,
                    productFeatureApplTypeId: productFeatureApplTypeId,
                    fromDate: now
            ])
            featureAppl.create()
        }
    }

    // Update core fields if provided
    if (UtilValidate.isNotEmpty(productName)) {
        product.set("productName", productName)
    }
    if (parameters.containsKey("internalName")) {
        product.set("internalName", internalName)
    }
    if (parameters.containsKey("description")) {
        product.set("description", description)
    }

    // Update last modified on the product itself
    product.set("lastModifiedDate", now)
    product.set("lastModifiedByUserLogin", userLogin.getString("userLoginId"))
    product.store()

    Map result = success()
    result.productId = productId
    return result
}

/**
 * assocProductToVirtual - Creates a PRODUCT_VARIANT association between a
 * virtual product and a variant product. Sets isVirtual/isVariant flags accordingly.
 */
def assocProductToVirtual() {
    String productId = parameters.productId
    String virtualProductId = parameters.virtualProductId

    // Verify variant product exists
    GenericValue variantProduct = EntityQuery.use(delegator)
            .from("Product")
            .where("productId", productId)
            .queryOne()

    if (variantProduct == null) {
        return error("Variant product '" + productId + "' does not exist.")
    }

    // Verify virtual product exists
    GenericValue virtualProduct = EntityQuery.use(delegator)
            .from("Product")
            .where("productId", virtualProductId)
            .queryOne()

    if (virtualProduct == null) {
        return error("Virtual product '" + virtualProductId + "' does not exist.")
    }

    def now = UtilDateTime.nowTimestamp()

    // Check if association already exists
    GenericValue existingAssoc = EntityQuery.use(delegator)
            .from("ProductAssoc")
            .where("productId", virtualProductId,
                    "productIdTo", productId,
                    "productAssocTypeId", "PRODUCT_VARIANT")
            .filterByDate()
            .queryFirst()

    if (existingAssoc != null) {
        return error("A virtual-variant relationship already exists between virtual product '"
                + virtualProductId + "' and variant product '" + productId + "'.")
    }

    // Create the ProductAssoc record
    GenericValue assoc = delegator.makeValue("ProductAssoc", [
            productId: virtualProductId,
            productIdTo: productId,
            productAssocTypeId: "PRODUCT_VARIANT",
            fromDate: now
    ])
    assoc.create()

    // Update the virtual product's isVirtual flag
    virtualProduct.set("isVirtual", "Y")
    virtualProduct.set("lastModifiedDate", now)
    virtualProduct.set("lastModifiedByUserLogin", userLogin.getString("userLoginId"))
    virtualProduct.store()

    // Update the variant product's isVariant flag
    variantProduct.set("isVariant", "Y")
    variantProduct.set("lastModifiedDate", now)
    variantProduct.set("lastModifiedByUserLogin", userLogin.getString("userLoginId"))
    variantProduct.store()

    return success()
}

/**
 * updateProductVariant - Updates an existing virtual-variant relationship.
 * If newVirtualProductId is provided, expires the old association and creates a new one.
 */
def updateProductVariant() {
    String productId = parameters.productId
    String virtualProductId = parameters.virtualProductId
    String newVirtualProductId = parameters.newVirtualProductId

    // Verify variant product exists
    GenericValue variantProduct = EntityQuery.use(delegator)
            .from("Product")
            .where("productId", productId)
            .queryOne()

    if (variantProduct == null) {
        return error("Variant product '" + productId + "' does not exist.")
    }

    // Verify virtual product exists
    GenericValue virtualProduct = EntityQuery.use(delegator)
            .from("Product")
            .where("productId", virtualProductId)
            .queryOne()

    if (virtualProduct == null) {
        return error("Virtual product '" + virtualProductId + "' does not exist.")
    }

    // Find existing association
    GenericValue existingAssoc = EntityQuery.use(delegator)
            .from("ProductAssoc")
            .where("productId", virtualProductId,
                    "productIdTo", productId,
                    "productAssocTypeId", "PRODUCT_VARIANT")
            .filterByDate()
            .queryFirst()

    if (existingAssoc == null) {
        return error("No active virtual-variant relationship exists between virtual product '"
                + virtualProductId + "' and variant product '" + productId + "'.")
    }

    def now = UtilDateTime.nowTimestamp()

    if (UtilValidate.isNotEmpty(newVirtualProductId)) {
        // Verify new virtual product exists
        GenericValue newVirtualProduct = EntityQuery.use(delegator)
                .from("Product")
                .where("productId", newVirtualProductId)
                .queryOne()

        if (newVirtualProduct == null) {
            return error("New virtual product '" + newVirtualProductId + "' does not exist.")
        }

        // Expire the old association
        existingAssoc.set("thruDate", now)
        existingAssoc.store()

        // Create the new association
        GenericValue newAssoc = delegator.makeValue("ProductAssoc", [
                productId: newVirtualProductId,
                productIdTo: productId,
                productAssocTypeId: "PRODUCT_VARIANT",
                fromDate: now
        ])
        newAssoc.create()

        // Update the new virtual product's isVirtual flag
        newVirtualProduct.set("isVirtual", "Y")
        newVirtualProduct.set("lastModifiedDate", now)
        newVirtualProduct.set("lastModifiedByUserLogin", userLogin.getString("userLoginId"))
        newVirtualProduct.store()
    }

    return success()
}
