/**
 * Pseudo-code based on the OFBiz ShopifyOrderServices.java implementation.
 */
function parseShopifyOrder(shopifyOrder, systemContext) {
    // 1. Process Order Header Information
    let orderHeader = {
        order_id: generateUniqueId(),
        order_type_id: "SALES_ORDER",
        order_name: shopifyOrder.name,
        external_id: shopifyOrder.id.toString(),
        order_date: formatDateTime(shopifyOrder.created_at),
        currency_uom: shopifyOrder.currency,
        product_store_id: systemContext.productStoreId,
        sales_channel_enum_id: resolveChannel(shopifyOrder.source_name),
        grand_total: parseFloat(shopifyOrder.total_price),
        status_id: resolveOrderStatus(shopifyOrder)
    };
    saveToDb('order_header', orderHeader);

    // 2. Process Customer / Order Role
    if (shopifyOrder.customer) {
        let partyId = findOrCreateCustomer(shopifyOrder.customer);
        saveToDb('order_role', {
            order_id: orderHeader.order_id,
            party_id: partyId,
            role_type_id: "CUSTOMER"
        });
    }

    // 3. Process Billing Address
    if (shopifyOrder.billing_address) {
        let billingContactMechId = createPostalAddress(shopifyOrder.billing_address);
        saveToDb('order_contact_mech', {
            order_id: orderHeader.order_id,
            contact_mech_purpose_type_id: "BILLING_LOCATION",
            contact_mech_id: billingContactMechId
        });
    }

    // 4. Process Contact Info (Email, Phone)
    if (shopifyOrder.email) {
        let emailContactMechId = createEmailAddress(shopifyOrder.email);
        saveToDb('order_contact_mech', {
            order_id: orderHeader.order_id,
            contact_mech_purpose_type_id: "ORDER_EMAIL",
            contact_mech_id: emailContactMechId
        });
    }
    
    // 5. Build Ship Groups
    // In ShopifyOrderServices, items are grouped by facility and shipment method first
    let shipGroups = resolveShipGroups(shopifyOrder.line_items, shopifyOrder.location_id, shopifyOrder.shipping_lines);
    let shipGroupSeqCounter = 1;
    let itemSeqCounter = 1;

    for (let groupKey of Object.keys(shipGroups)) {
        let groupItems = shipGroups[groupKey];
        let shipGroupSeqId = shipGroupSeqCounter.toString().padStart(5, '0');
        let parsedGroupKey = groupKey.split("|"); // Format: facilityId|shipmentMethodTypeId|carrierPartyId
        
        let shippingContactMechId = null;
        let shippingTelecomMechId = null;

        if (shopifyOrder.shipping_address) {
            shippingContactMechId = createPostalAddress(shopifyOrder.shipping_address);
            if (shopifyOrder.shipping_address.phone) {
                shippingTelecomMechId = createTelecomNumber(shopifyOrder.shipping_address.phone);
            }
        }

        saveToDb('order_item_ship_group', {
            order_id: orderHeader.order_id,
            ship_group_seq_id: shipGroupSeqId,
            facility_id: parsedGroupKey[0] !== "_NA_" ? parsedGroupKey[0] : null,
            shipment_method_type_id: parsedGroupKey[1],
            carrier_party_id: parsedGroupKey[2],
            contact_mech_id: shippingContactMechId,
            telecom_contact_mech_id: shippingTelecomMechId
        });

        // 6. Process Items for this Ship Group
        for (let item of groupItems) {
            let orderItemSeqId = itemSeqCounter.toString().padStart(5, '0');
            
            // Calculate actual qty considering refunds
            let validQty = calculateNetQuantity(item, shopifyOrder.refunds);
            if (validQty <= 0) continue;

            let productId = findProductIdByShopifyVariant(item.variant_id);

            saveToDb('order_item', {
                order_id: orderHeader.order_id,
                order_item_seq_id: orderItemSeqId,
                order_item_type_id: "PRODUCT_ORDER_ITEM",
                product_id: productId,
                quantity: validQty,
                unit_price: parseFloat(item.price),
                unit_list_price: parseFloat(item.price),
                item_description: item.title,
                status_id: resolveItemStatus(item, shopifyOrder),
                external_id: item.id.toString()
            });

            saveToDb('order_item_ship_group_assoc', {
                order_id: orderHeader.order_id,
                order_item_seq_id: orderItemSeqId,
                ship_group_seq_id: shipGroupSeqId,
                quantity: validQty
            });

            // 7. Item Adjustments (Discounts)
            if (item.discount_allocations) {
                item.discount_allocations.forEach(discount => {
                    let discountAmount = parseFloat(discount.amount);
                    if (discountAmount > 0) {
                        saveToDb('order_adjustment', {
                            order_adjustment_id: generateUniqueId(),
                            order_adjustment_type_id: "EXT_PROMO_ADJUSTMENT",
                            order_id: orderHeader.order_id,
                            order_item_seq_id: orderItemSeqId,
                            ship_group_seq_id: shipGroupSeqId,
                            amount: -discountAmount, // Discounts are stored as negative
                            description: "External Discount"
                        });
                    }
                });
            }
            itemSeqCounter++;
        }
        shipGroupSeqCounter++;
    }

    // 8. Order Adjustments (Tax, Shipping)
    if (shopifyOrder.total_tax && parseFloat(shopifyOrder.total_tax) > 0) {
        saveToDb('order_adjustment', {
            order_adjustment_id: generateUniqueId(),
            order_adjustment_type_id: "SALES_TAX",
            order_id: orderHeader.order_id,
            amount: parseFloat(shopifyOrder.total_tax),
            description: "Sales Tax"
        });
    }

    if (shopifyOrder.shipping_lines) {
        shopifyOrder.shipping_lines.forEach(shipping => {
            saveToDb('order_adjustment', {
                order_adjustment_id: generateUniqueId(),
                order_adjustment_type_id: "SHIPPING_CHARGES",
                order_id: orderHeader.order_id,
                amount: parseFloat(shipping.price),
                description: shipping.title
            });
        });
    }
}

// ---------------------------------------------------------
// Helper functions (Pseudo-implementations mocking OFBiz)
// ---------------------------------------------------------
function generateUniqueId() { return Math.random().toString(36).substr(2, 9); }
function formatDateTime(dateStr) { return new Date(dateStr).toISOString(); }
function saveToDb(table, data) { console.log(`Saving to ${table}:`, data); }
function resolveChannel(sourceName) { return sourceName === "pos" ? "POS_SALES_CHANNEL" : "WEB_SALES_CHANNEL"; }
function resolveOrderStatus(order) { 
    if (order.cancelled_at) return "ORDER_CANCELLED";
    if (order.closed_at) return "ORDER_COMPLETED";
    return "ORDER_CREATED"; 
}
function resolveItemStatus(item, order) {
    if (order.cancelled_at || item.fulfillment_status === "cancelled") return "ITEM_CANCELLED";
    if (item.fulfillment_status === "fulfilled") return "ITEM_COMPLETED";
    return "ITEM_CREATED";
}
function calculateNetQuantity(item, refunds) { return item.quantity; /* Mocked */ }
function findOrCreateCustomer(customerData) { return "PARTY_123"; }
function createPostalAddress(addressData) { return "CONTACT_MECH_456"; }
function createEmailAddress(email) { return "CONTACT_MECH_789"; }
function createTelecomNumber(phone) { return "CONTACT_MECH_012"; }
function findProductIdByShopifyVariant(variantId) { return "PROD_345"; }
function resolveShipGroups(lineItems, locationId, shippingLines) {
    // In reality, this checks for pre-selected facilities via item properties, POS logic, etc.
    // Returns a map grouping items by {facilityId}|{shipmentMethodTypeId}|{carrierPartyId}
    let groups = {};
    let shipMethod = shippingLines && shippingLines.length > 0 ? shippingLines[0].title : "STANDARD";
    groups[`${locationId || '_NA_'}|${shipMethod}|_NA_`] = lineItems;
    return groups;
}
