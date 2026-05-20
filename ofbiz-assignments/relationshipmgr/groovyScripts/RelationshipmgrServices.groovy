import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue

// Service to create a Person and its Supertype in one step
def createRmPerson() {
    // Generate a new ID if the user didn't provide one
    String partyId = parameters.partyId ?: delegator.getNextSeqId("RmParty")

    // 1. Create the RmParty (Supertype)
    GenericValue newParty = delegator.makeValue("RmParty", [
            partyId: partyId,
            partyTypeId: "PERSON",
            statusId: parameters.statusId ?: "PARTY_ENABLED",
            description: parameters.description,
            createdDate: UtilDateTime.nowTimestamp()
    ])
    newParty.create()

    // 2. Create the RmPerson (Subtype)
    GenericValue newPerson = delegator.makeValue("RmPerson", [
            partyId: partyId,
            firstName: parameters.firstName,
            lastName: parameters.lastName,
            gender: parameters.gender,
            birthDate: parameters.birthDate
    ])
    newPerson.create()

    Map result = success()
    result.partyId = partyId
    return result
}

// Service to create a PartyGroup and its Supertype in one step
def createRmPartyGroup() {
    String partyId = parameters.partyId ?: delegator.getNextSeqId("RmParty")

    // 1. Create the RmParty (Supertype)
    GenericValue newParty = delegator.makeValue("RmParty", [
            partyId: partyId,
            partyTypeId: "PARTY_GROUP",
            statusId: parameters.statusId ?: "PARTY_ENABLED",
            description: parameters.description,
            createdDate: UtilDateTime.nowTimestamp()
    ])
    newParty.create()

    // 2. Create the RmPartyGroup (Subtype)
    GenericValue newGroup = delegator.makeValue("RmPartyGroup", [
            partyId: partyId,
            groupName: parameters.groupName,
            comments: parameters.comments
    ])
    newGroup.create()

    Map result = success()
    result.partyId = partyId
    return result
}

// Service to create a Telecom Number and link it to a Party
def createRmTelecomNumber() {
    String contactMechId = delegator.getNextSeqId("RmContactMech")

    // 1. Create Supertype
    GenericValue newContactMech = delegator.makeValue("RmContactMech", [
            contactMechId: contactMechId,
            contactMechTypeId: "TELECOM_NUMBER"
    ])
    newContactMech.create()

    // 2. Create Subtype
    GenericValue newTelecom = delegator.makeValue("RmTelecomNumber", [
            contactMechId: contactMechId,
            countryCode: parameters.countryCode,
            areaCode: parameters.areaCode,
            contactNumber: parameters.contactNumber
    ])
    newTelecom.create()

    // 3. Link to Party
    GenericValue newPartyLink = delegator.makeValue("RmPartyContactMech", [
            partyId: parameters.partyId,
            contactMechId: contactMechId,
            contactMechPurposeId: parameters.contactMechPurposeId,
            fromDate: UtilDateTime.nowTimestamp(),
            roleTypeId: parameters.roleTypeId
    ])
    newPartyLink.create()

    return success()
}

// Service to create a Postal Address and link it to a Party
def createRmPostalAddress() {
    String contactMechId = delegator.getNextSeqId("RmContactMech")

    // 1. Create Supertype
    GenericValue newContactMech = delegator.makeValue("RmContactMech", [
            contactMechId: contactMechId,
            contactMechTypeId: "POSTAL_ADDRESS"
    ])
    newContactMech.create()

    // 2. Create Subtype
    GenericValue newAddress = delegator.makeValue("RmPostalAddress", [
            contactMechId: contactMechId,
            toName: parameters.toName,
            address1: parameters.address1,
            city: parameters.city,
            postalCode: parameters.postalCode
    ])
    newAddress.create()

    // 3. Link to Party
    GenericValue newPartyLink = delegator.makeValue("RmPartyContactMech", [
            partyId: parameters.partyId,
            contactMechId: contactMechId,
            contactMechPurposeId: parameters.contactMechPurposeId,
            fromDate: UtilDateTime.nowTimestamp(),
            roleTypeId: parameters.roleTypeId
    ])
    newPartyLink.create()

    return success()
}


// Service to create an Email Address and link it to a Party
def createRmEmailAddress() {
    String contactMechId = delegator.getNextSeqId("RmContactMech")

    // 1. Create Supertype with the Email String
    GenericValue newContactMech = delegator.makeValue("RmContactMech", [
            contactMechId: contactMechId,
            contactMechTypeId: "EMAIL_ADDRESS",
            infoString: parameters.infoString
    ])
    newContactMech.create()

    // 2. Link to Party
    GenericValue newPartyLink = delegator.makeValue("RmPartyContactMech", [
            partyId: parameters.partyId,
            contactMechId: contactMechId,
            contactMechPurposeId: parameters.contactMechPurposeId,
            fromDate: UtilDateTime.nowTimestamp(),
            roleTypeId: parameters.roleTypeId
    ])
    newPartyLink.create()

    return success()
}