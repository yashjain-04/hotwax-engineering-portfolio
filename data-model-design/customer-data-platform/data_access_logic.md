# Data Access Logic (Moqui Framework Pseudo-code)

This document contains the pseudo-code for interacting with the Universal Data Model (UDM) Customer database schema using the Moqui Framework's Entity Facade.

## 1. Creating a New Customer Record
This logic handles the creation of a Party, Person, PartyRole (CUSTOMER), and their initial Contact Mechanism (Email).

```groovy
function createCustomer(firstName, lastName, email, phone){
    // Validate Input Data
    if(!firstName || !lastName){
        throw new ValidationError("First name and last name are required.")
    }
    if(!email){
        throw new ValidationError("Email address is required.")
    }
    
    // Creating the party record
    String partyId = ec.entity.sequencedIdPrimary("Party") //Service in EntityFacadeImpl.groovy
    ec.entity.makeValue("Party")
        .setAll([partyId: partyId, partyTypeId: "PERSON", disabled: "N"])
        .create()
        
    // Creating the person record
    ec.entity.makeValue("Person")
        .setAll([partyId: partyId, firstName: firstName, lastName: lastName])
        .create()
        
    // Assigning the 'CUSTOMER' role
    ec.entity.makeValue("PartyRole")
        .setAll([partyId: partyId, roleTypeId: "CUSTOMER"])
        .create()
        
    // Creating the email contact mechanism
    String emailContactMechId = ec.entity.sequencedIdPrimary("ContactMech")
    ec.entity.makeValue("ContactMech")
        .setAll([contactMechId: emailContactMechId, contactMechTypeId: "EMAIL_ADDRESS", infoString: email])
        .create()
        
    // Linking the email to the party with the purpose 'PRIMARY_EMAIL'
    ec.entity.makeValue("PartyContactMech")
        .setAll([
            partyId: partyId, 
            contactMechId: emailContactMechId, 
            contactMechPurposeId: "PRIMARY_EMAIL", 
            fromDate: ec.user.nowTimestamp
        ])
        .create()
    
    // Creating the phone contact mechanism (if provided)
    if (phone){
        String phoneContactMechId = ec.entity.sequencedIdPrimary("ContactMech")
        ec.entity.makeValue("ContactMech")
            .setAll([contactMechId: phoneContactMechId, contactMechTypeId: "TELECOM_NUMBER"])
            .create()
            
        ec.entity.makeValue("TelecomNumber")
            .setAll([contactMechId: phoneContactMechId, contactNumber: phone])
            .create()
            
        // Linking the phone to the party with the purpose 'PRIMARY_PHONE'
        ec.entity.makeValue("PartyContactMech")
            .setAll([
                partyId: partyId, 
                contactMechId: phoneContactMechId, 
                contactMechPurposeId: "PRIMARY_PHONE", 
                fromDate: ec.user.nowTimestamp
            ])
            .create()
    }

    return partyId
}
```

## 2. Retrieving a Customer Record
This logic fetches the customer's personal details and their currently active contact mechanisms.

```groovy
function getCustomerDetails(partyId){
    // Find the Person Record
    GenericValue person = ec.entity.find("Person").condition("partyId", partyId).one()
    if (!person){
        throw new NotFoundError("Customer not found for ID: " + partyId)
    }
    
    // Find Active Contact Mechanisms
    List activeContactMechs = ec.entity.find("PartyContactMech")
        .condition("partyId", partyId)
        .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp) // to filter out expired cm
        .list()
        
    // Find Customer Preferences
    List preferences = ec.entity.find("CustomerPreference")
        .condition("partyId", partyId)
        .list()
        
    // Final customer record
    Map customerInfo = [
        partyId: person.partyId,
        firstName: person.firstName,
        lastName: person.lastName,
        contactInfo: activeContactMechs,
        preferences: preferences
    ]
    
    return customerInfo
}
```

## 3. Updating a Customer Record
This logic updates personal details, handles changes to addresses, contact information (like email), and preferences. 
When updating contact information or addresses, it soft-deletes the old link and creates a new one to preserve historical data integrity.

```groovy
function updateCustomer(partyId, updateData){
    // UpdateData is a map/object containing potential updates:
    // { firstName, lastName, newEmail, newAddress: {address1, city, ...}, newPreferences: [{type, value}] }

    // Update Basic Person Info
    GenericValue person = ec.entity.find("Person").condition("partyId", partyId).one()
    if (!person) throw new NotFoundError("Customer not found.")
    
    if (updateData.firstName) person.firstName = updateData.firstName
    if (updateData.lastName) person.lastName = updateData.lastName
    person.update()
    
    // Handle Email Updates
    if (updateData.newEmail){
        // Find the currently active PRIMARY_EMAIL relationship
        GenericValue currentEmailRel = ec.entity.find("PartyContactMech")
            .condition([partyId: partyId, contactMechPurposeId: "PRIMARY_EMAIL"])
            .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp)
            .one()
            
        // Expire the old email relationship (Soft Delete)
        if (currentEmailRel){
            currentEmailRel.thruDate = ec.user.nowTimestamp
            currentEmailRel.update()
        }
        
        // Create the new Email Contact Mechanism
        String emailContactMechId = ec.entity.sequencedIdPrimary("ContactMech")
        ec.entity.makeValue("ContactMech")
            .setAll([contactMechId: emailContactMechId, contactMechTypeId: "EMAIL_ADDRESS", infoString: updateData.newEmail])
            .create()
            
        // Link the new Email to the Party
        ec.entity.makeValue("PartyContactMech")
            .setAll([
                partyId: partyId, 
                contactMechId: emailContactMechId, 
                contactMechPurposeId: "PRIMARY_EMAIL", 
                fromDate: ec.user.nowTimestamp
            ])
            .create()
    }
    
    // Handle Address Updates (Assuming updating the Default Shipping Address here for example)
    if (updateData.newAddress){
        // Find current default shipping address link
        GenericValue currentAddrRel = ec.entity.find("PartyContactMech")
            .condition([partyId: partyId, contactMechPurposeId: "DEFAULT_SHIPPING"])
            .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp)
            .one()
            
        // Expire old address relationship (Soft Delete)
        if (currentAddrRel){
            currentAddrRel.thruDate = ec.user.nowTimestamp
            currentAddrRel.update()
        }
        
        // Create new ContactMech & PostalAddress
        String contactMechId = ec.entity.sequencedIdPrimary("ContactMech")
        ec.entity.makeValue("ContactMech")
            .setAll([contactMechId: contactMechId, contactMechTypeId: "POSTAL_ADDRESS"])
            .create()
            
        ec.entity.makeValue("PostalAddress")
            .setAll([
                contactMechId: contactMechId,
                address1: updateData.newAddress.address1,
                city: updateData.newAddress.city,
                postalCode: updateData.newAddress.zip,
                countryGeoId: updateData.newAddress.country
            ])
            .create()
            
        // Link new Address to Party
        ec.entity.makeValue("PartyContactMech")
            .setAll([
                partyId: partyId, 
                contactMechId: contactMechId, 
                contactMechPurposeId: "DEFAULT_SHIPPING", 
                fromDate: ec.user.nowTimestamp
            ])
            .create()
    }

    // Handle Preferences Update
    if (updateData.newPreferences){
        for (def pref : updateData.newPreferences){
            GenericValue existingPref = ec.entity.find("CustomerPreference")
                .condition([partyId: partyId, preferenceTypeId: pref.type])
                .one()
                
            if (existingPref) {
                // Update existing preference
                existingPref.preferenceValue = pref.value
                existingPref.update()
            } else {
                // Create new preference
                ec.entity.makeValue("CustomerPreference")
                    .setAll([partyId: partyId, preferenceTypeId: pref.type, preferenceValue: pref.value])
                    .create()
            }
        }
    }
}
```


## 4. Deleting a Customer Record (Soft Delete)
Deleting a customer record outright (hard delete) violates data integrity rules if they have existing orders or invoices. Instead, we use a "Soft Delete" approach.

```groovy
function deleteCustomer(partyId){
    // Mark the base Party record as disabled
    GenericValue party = ec.entity.find("Party").condition("partyId", partyId).one()
    if (party){
        party.disabled = "Y" 
        party.update()
        
        // Expire all active contact mechanisms associated with the party
        List activeContactMechs = ec.entity.find("PartyContactMech")
            .condition("partyId", partyId)
            .conditionDate("fromDate", "thruDate", ec.user.nowTimestamp)
            .list()
            
        for (GenericValue pcm in activeContactMechs) {
            pcm.thruDate = ec.user.nowTimestamp // Setting thruDate to expire the record
            pcm.update()
        }
    } else {
        throw new NotFoundError("Customer not found for ID: " + partyId)
    }
}
```
