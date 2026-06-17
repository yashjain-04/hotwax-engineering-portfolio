import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue

ExecutionContext ec = context.ec

// 1. Find the Party AND check its type
EntityValue party = ec.entity.find("party.Party").condition("partyId", partyId).one()

if (!party) {
    ec.message.addError("Cannot create Person. Party with ID [${partyId}] does not exist.")
    return
}

// NEW: Validate that the Party is actually a PERSON
if (party.partyTypeEnumId != "PERSON") {
    ec.message.addError("Cannot create Person. Party [${partyId}] is a ${party.partyTypeEnumId}, not a PERSON.")
    return
}

// 2. Create the Person record
// This will now only run if the Party exists AND is the correct type
Map createResult = ec.service.sync().name("create", "party.Person").parameters(context).call()

// 3. Success Response
message = "Person ${firstName} ${lastName} created successfully!"
return [message: message]


//import org.moqui.context.ExecutionContext
//import org.moqui.entity.EntityValue
//
//ExecutionContext ec = context.ec
//
//// 1. Verify that a Party record exists
//EntityValue party = ec.entity.find("party.Party").condition("partyId", partyId).one()
//
//if (!party) {
//    ec.message.addError("Cannot create Person. Party with ID [${partyId}] does not exist.")
//    return
//}
//
//// 2. Create the Person record using the implicit entity-auto service
//Map createResult = ec.service.sync().name("create", "party.Person").parameters(context).call()
//
//// 3. Return the exact response string requested
//message = "Person ${firstName} ${lastName} created successfully!"
//return [message: message]