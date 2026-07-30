# Shopify Customer API to UDM Mapping Document

This document outlines the field mappings between the Shopify Customer API JSON payload and the UDM Party Data Model, along with the integration logic to upsert data efficiently.

## 1. Field Mapping Strategy

### Basic Customer Fields

| Shopify Customer API Field | UDM Target Entity & Field | Transformation / Notes |
| :--- | :--- | :--- |
| `id` (e.g., 207119551) | `PartyIdentification.id_value` | Map with `party_identification_type_id` = 'SHOPIFY_CUST_ID' |
| `first_name` | `Person.first_name` | Direct mapping. |
| `last_name` | `Person.last_name` | Direct mapping. |
| `email` | `ContactMech.info_string` | `contact_mech_type_id` = 'EMAIL_ADDRESS', linked via `PartyContactMech` with purpose 'PRIMARY_EMAIL'. |
| `verified_email` (boolean) | `CustomerPreference.preference_value` | Map to `preference_type_id` = 'EMAIL_VERIFIED', value is converted to 'Y' or 'N'. |
| `phone` | `TelecomNumber.contact_number` | Linked via `PartyContactMech` with purpose 'PRIMARY_PHONE'. |

### Address Array Mapping (`addresses[]`)
Shopify provides addresses as an array. Each object maps as follows:

| Shopify Address Field | UDM Target Entity & Field | Transformation / Notes |
| :--- | :--- | :--- |
| `address1` | `PostalAddress.address1` | Direct mapping. |
| `address2` | `PostalAddress.address2` | Direct mapping. |
| `city` | `PostalAddress.city` | Direct mapping. |
| `province` | `PostalAddress.state_province_geo_id` | Requires lookup mapping from Shopify Province Name/Code to standard GeoId. |
| `zip` | `PostalAddress.postal_code` | Direct mapping. |
| `country` | `PostalAddress.country_geo_id` | Requires lookup mapping from Shopify Country to standard ISO country code GeoId. |
| `phone` | `TelecomNumber.contact_number` | Linked directly to the specific address or as a standalone `TelecomNumber`. |
| `default` (boolean) | `PartyContactMech.contact_mech_purpose_id` | If `true`, map to 'DEFAULT_SHIPPING'. If `false`, map to 'SHIPPING_LOCATION'. |

### Handling Data Type Differences & Multi-valued Fields

**Data Type Differences**: Shopify JSON data represents booleans natively (`true`/`false`) and IDs as numbers. Our MySQL UDM schema relies on `VARCHAR` and `CHAR` data types for many of these concepts (e.g. `party_id` is a `VARCHAR`, booleans are often represented as `CHAR(1)` like 'Y' or 'N'). We transform IDs by converting them to strings (`.toString()`). Booleans, such as `verified_email`, are translated to 'Y'/'N' indicators before storage. 

**Multi-valued Fields**: Fields like `addresses` are provided as arrays. In a normalized UDM schema, these cannot be stored in a single field. Instead, we iterate over the `addresses` array and create a new row in the `ContactMech`, `PostalAddress`, and `PartyContactMech` tables for each address object in the array. This allows one customer (`Party`) to be linked to multiple addresses.

**Transformation Example**:
*Shopify JSON Input:*
```json
{
  "id": 1234567,
  "verified_email": true,
  "addresses": [
    {"address1": "123 Main St", "city": "Austin", "default": true}
  ]
}
```
*UDM Database Output:*
- `PartyIdentification`: `party_id`="1001", `party_identification_type_id`="SHOPIFY_CUST_ID", `id_value`="1234567"
- `CustomerPreference`: `party_id`="1001", `preference_type_id`="EMAIL_VERIFIED", `preference_value`="Y"
- `ContactMech`: `contact_mech_id`="2001", `contact_mech_type_id`="POSTAL_ADDRESS"
- `PostalAddress`: `contact_mech_id`="2001", `address1`="123 Main St", `city`="Austin"
- `PartyContactMech`: `party_id`="1001", `contact_mech_id`="2001", `contact_mech_purpose_id`="DEFAULT_SHIPPING"

---

## 2. Integration Pseudo-code (Upsert Logic)

This Moqui-based pseudo-code handles retrieving, transforming, and upserting the Shopify Customer JSON data into the UDM MySQL database.

```groovy
// --- Step 1: Retrieve Customer Data from Shopify ---
function fetchShopifyCustomers(){
    String shopUrl = "https://notnaked.myshopify.com/admin/api/2024-01/customers.json"
    String apiToken = ec.env.get("SHOPIFY_ACCESS_TOKEN")
    
    if (!apiToken){
        throw new ConfigurationError("Shopify API access token is not configured.")
    }
    
    try {
        // Make authenticated GET request to Shopify Customer API
        HttpResponse response = httpClient.get(shopUrl)
            .header("X-Shopify-Access-Token", apiToken)
            .execute()
            
        if (response.statusCode != 200){
            throw new IntegrationError("Shopify API returned status: " + response.statusCode + " - " + response.body)
        }
        
        // Parse the JSON response
        def jsonResponse = new JsonSlurper().parseText(response.body)
        List customers = jsonResponse.customers
        
        if (!customers || customers.isEmpty()){
            ec.logger.info("No customers found from Shopify.")
            return
        }
        
        // Process each customer through the sync pipeline
        for (def customer : customers){
            try {
                syncShopifyCustomer(customer)
            } catch (Exception e) {
                // Log error but continue processing remaining customers
                ec.logger.error("Failed to sync Shopify customer ID: " + customer.id + " - " + e.message)
            }
        }
        
        ec.logger.info("Successfully processed " + customers.size() + " Shopify customers.")
        
    } catch (Exception e){
        throw new IntegrationError("Failed to fetch customers from Shopify: " + e.message)
    }
}

// --- Step 2: Transform and Store Customer Data (Upsert) ---
function syncShopifyCustomer(shopifyCustomerJson){
    try {
        // 1. Check if customer already exists using Shopify ID
        GenericValue partyIdent = ec.entity.find("PartyIdentification")
            .condition([partyIdentificationTypeId: "SHOPIFY_CUST_ID", idValue: shopifyCustomerJson.id.toString()])
            .one()
            
        String partyId
        if (partyIdent){
            // Customer Exists: Proceed with Update
            partyId = partyIdent.partyId
            // updateCustomer is referenced from data_access_logic.md
            updateCustomer(partyId, [
                firstName: shopifyCustomerJson.first_name,
                lastName: shopifyCustomerJson.last_name,
                newEmail: shopifyCustomerJson.email
            ])
        }
        else{
            // Customer Doesn't Exist: Proceed with Create
            // createCustomer is referenced from data_access_logic.md
            partyId = createCustomer(
                shopifyCustomerJson.first_name, 
                shopifyCustomerJson.last_name, 
                shopifyCustomerJson.email, 
                shopifyCustomerJson.phone
            )
            
            // Link the new Party with their Shopify ID
            ec.entity.makeValue("PartyIdentification")
                .setAll([
                    partyId: partyId, 
                    partyIdentificationTypeId: "SHOPIFY_CUST_ID", 
                    idValue: shopifyCustomerJson.id.toString()
                ])
                .create()
        }
        
        // 2. Sync Addresses
        if (shopifyCustomerJson.addresses) {
            for (def address : shopifyCustomerJson.addresses){
                syncShopifyAddress(partyId, address)
            }
        }
        
        // 3. Sync Verified Email Preference
        if (shopifyCustomerJson.verified_email != null){
            String verifiedValue = shopifyCustomerJson.verified_email ? "Y" : "N"
            GenericValue existingPref = ec.entity.find("CustomerPreference")
                .condition([partyId: partyId, preferenceTypeId: "EMAIL_VERIFIED"])
                .one()
                
            if (existingPref){
                existingPref.preferenceValue = verifiedValue
                existingPref.update()
            } else {
                ec.entity.makeValue("CustomerPreference")
                    .setAll([partyId: partyId, preferenceTypeId: "EMAIL_VERIFIED", preferenceValue: verifiedValue])
                    .create()
            }
        }
        
        // 4. Sync Phone Number
        if (shopifyCustomerJson.phone){
            // Expire any existing primary phone link
            GenericValue currentPhoneRel = ec.entity.find("PartyContactMech")
                .condition([partyId: partyId, contactMechPurposeId: "PRIMARY_PHONE"])
                .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp)
                .one()
                
            if (currentPhoneRel){
                currentPhoneRel.thruDate = ec.user.nowTimestamp
                currentPhoneRel.update()
            }
            
            // Create new phone contact mechanism
            String phoneContactMechId = ec.entity.sequencedIdPrimary("ContactMech")
            ec.entity.makeValue("ContactMech")
                .setAll([contactMechId: phoneContactMechId, contactMechTypeId: "TELECOM_NUMBER"])
                .create()
                
            ec.entity.makeValue("TelecomNumber")
                .setAll([contactMechId: phoneContactMechId, contactNumber: shopifyCustomerJson.phone])
                .create()
                
            ec.entity.makeValue("PartyContactMech")
                .setAll([
                    partyId: partyId,
                    contactMechId: phoneContactMechId,
                    contactMechPurposeId: "PRIMARY_PHONE",
                    fromDate: ec.user.nowTimestamp
                ])
                .create()
        }
    } catch (Exception e){
        ec.logger.error("Error syncing Shopify customer: " + e.message)
        throw new IntegrationError("Failed to sync customer ID: " + shopifyCustomerJson.id + " - " + e.message)
    }
}


function syncShopifyAddress(partyId, shopifyAddress){
    // 1. De-duplication Check: Skip if this exact address already exists for the party
    List existingAddresses = ec.entity.find("PartyContactMech")
        .condition("partyId", partyId)
        .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp)
        .list()
    
    for (def pcm : existingAddresses){
        GenericValue existingAddr = ec.entity.find("PostalAddress")
            .condition("contactMechId", pcm.contactMechId)
            .one()
        if (existingAddr && existingAddr.address1 == shopifyAddress.address1 
            && existingAddr.city == shopifyAddress.city 
            && existingAddr.postalCode == shopifyAddress.zip){
            ec.logger.info("Duplicate address found for party " + partyId + ", skipping.")
            return // Skip creating duplicate address
        }
    }
    
    // 2. Create Base Contact Mechanism
    String contactMechId = ec.entity.sequencedIdPrimary("ContactMech")
    ec.entity.makeValue("ContactMech")
        .setAll([contactMechId: contactMechId, contactMechTypeId: "POSTAL_ADDRESS"])
        .create()
    
    // 3. Create Postal Address
    ec.entity.makeValue("PostalAddress")
        .setAll([
            contactMechId: contactMechId,
            address1: shopifyAddress.address1,
            address2: shopifyAddress.address2,
            city: shopifyAddress.city,
            // Assume mapProvinceToGeoId and mapCountryToGeoId are utility functions 
            // that convert "Texas" to "TX" or "USA" to "USA" respectively based on internal Geo data.
            stateProvinceGeoId: mapProvinceToGeoId(shopifyAddress.province),
            postalCode: shopifyAddress.zip,
            countryGeoId: mapCountryToGeoId(shopifyAddress.country)
        ])
        .create()
        
    // 4. Determine Purpose based on Shopify's 'default' flag
    String purpose = shopifyAddress.default ? "DEFAULT_SHIPPING" : "SHIPPING_LOCATION"
    
    // 5. Link Address to Party
    ec.entity.makeValue("PartyContactMech")
        .setAll([
            partyId: partyId, 
            contactMechId: contactMechId, 
            contactMechPurposeId: purpose, 
            fromDate: ec.user.nowTimestamp
        ])
        .create()
}
```
