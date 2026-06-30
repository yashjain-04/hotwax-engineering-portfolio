package com.customer.management;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

public class CustomerServices {

    public static final String module = CustomerServices.class.getName();

    public static Map<String, Object> findCustomer(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String partyId = (String) context.get("partyId");
        String emailAddress = (String) context.get("emailAddress");
        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        String contactNumber = (String) context.get("contactNumber");
        String address1 = (String) context.get("address1");
        String city = (String) context.get("city");

        List<EntityCondition> conditions = new LinkedList<>();

        if (UtilValidate.isNotEmpty(partyId)) {
            conditions.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId));
        }
        if (UtilValidate.isNotEmpty(emailAddress)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("emailAddress"), EntityOperator.LIKE, "%" + emailAddress.toUpperCase() + "%"));
        }
        if (UtilValidate.isNotEmpty(firstName)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("firstName"), EntityOperator.LIKE, "%" + firstName.toUpperCase() + "%"));
        }
        if (UtilValidate.isNotEmpty(lastName)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("lastName"), EntityOperator.LIKE, "%" + lastName.toUpperCase() + "%"));
        }
        if (UtilValidate.isNotEmpty(contactNumber)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("contactNumber"), EntityOperator.LIKE, "%" + contactNumber.toUpperCase() + "%"));
        }
        if (UtilValidate.isNotEmpty(address1)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("address1"), EntityOperator.LIKE, "%" + address1.toUpperCase() + "%"));
        }
        if (UtilValidate.isNotEmpty(city)) {
            conditions.add(EntityCondition.makeCondition(org.apache.ofbiz.entity.condition.EntityFunction.upperField("city"), EntityOperator.LIKE, "%" + city.toUpperCase() + "%"));
        }

        // Only fetch active contact mechs
        conditions.add(EntityCondition.makeCondition("emailThruDate", EntityOperator.EQUALS, null));
        conditions.add(EntityCondition.makeCondition("phoneThruDate", EntityOperator.EQUALS, null));
        conditions.add(EntityCondition.makeCondition("addressThruDate", EntityOperator.EQUALS, null));

        try {
            EntityCondition mainCond = EntityCondition.makeCondition(conditions, EntityOperator.AND);
            List<GenericValue> customers = delegator.findList("FindCustomerView", mainCond, null, null, null, false);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("customers", customers);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError("Error finding customer: " + e.getMessage());
        }
    }

    public static Map<String, Object> createCustomer(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        String emailAddress = (String) context.get("emailAddress");
        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        String contactNumber = (String) context.get("contactNumber");
        String address1 = (String) context.get("address1");
        String city = (String) context.get("city");
        String postalCode = (String) context.get("postalCode");

        try {
            // Check if exists
            Map<String, Object> findCtx = UtilMisc.toMap("emailAddress", emailAddress, "userLogin", userLogin);
            Map<String, Object> findResult = dispatcher.runSync("findCustomer", findCtx);
            List<GenericValue> existing = (List<GenericValue>) findResult.get("customers");
            
            if (UtilValidate.isNotEmpty(existing)) {
                // Exact match check for email
                for(GenericValue cust : existing) {
                    if (emailAddress.equals(cust.getString("emailAddress"))) {
                        return ServiceUtil.returnError("Customer with email " + emailAddress + " already exists.");
                    }
                }
            }

            // Create Person
            Map<String, Object> createPersonCtx = UtilMisc.toMap(
                    "firstName", firstName,
                    "lastName", lastName,
                    "userLogin", userLogin
            );
            Map<String, Object> personResult = dispatcher.runSync("createPerson", createPersonCtx);
            String partyId = (String) personResult.get("partyId");

            // Create Party Role
            dispatcher.runSync("createPartyRole", UtilMisc.toMap("partyId", partyId, "roleTypeId", "CUSTOMER", "userLogin", userLogin));

            // Create Email Contact Mech
            Map<String, Object> emailCtx = UtilMisc.toMap("emailAddress", emailAddress, "userLogin", userLogin);
            Map<String, Object> emailResult = dispatcher.runSync("createEmailAddress", emailCtx);
            String emailContactMechId = (String) emailResult.get("contactMechId");

            // Link Email to Party with Purpose EmailPrimary
            Map<String, Object> emailPurposeCtx = UtilMisc.toMap(
                    "partyId", partyId,
                    "contactMechId", emailContactMechId,
                    "contactMechTypeId", "EMAIL_ADDRESS",
                    "contactMechPurposeTypeId", "EmailPrimary",
                    "userLogin", userLogin
            );
            dispatcher.runSync("createPartyContactMech", emailPurposeCtx);

            // Create Postal Address if provided
            if (UtilValidate.isNotEmpty(address1) && UtilValidate.isNotEmpty(city)) {
                Map<String, Object> addressCtx = UtilMisc.toMap(
                        "address1", address1,
                        "city", city,
                        "postalCode", postalCode,
                        "userLogin", userLogin
                );
                Map<String, Object> addressResult = dispatcher.runSync("createPostalAddress", addressCtx);
                String addressContactMechId = (String) addressResult.get("contactMechId");

                Map<String, Object> addressPurposeCtx = UtilMisc.toMap(
                        "partyId", partyId,
                        "contactMechId", addressContactMechId,
                        "contactMechTypeId", "POSTAL_ADDRESS",
                        "contactMechPurposeTypeId", "PRIMARY_LOCATION",
                        "userLogin", userLogin
                );
                dispatcher.runSync("createPartyContactMech", addressPurposeCtx);
            }

            // Create Telecom Number if provided
            if (UtilValidate.isNotEmpty(contactNumber)) {
                Map<String, Object> telecomCtx = UtilMisc.toMap(
                        "contactNumber", contactNumber,
                        "userLogin", userLogin
                );
                Map<String, Object> telecomResult = dispatcher.runSync("createTelecomNumber", telecomCtx);
                String telecomContactMechId = (String) telecomResult.get("contactMechId");

                Map<String, Object> telecomPurposeCtx = UtilMisc.toMap(
                        "partyId", partyId,
                        "contactMechId", telecomContactMechId,
                        "contactMechTypeId", "TELECOM_NUMBER",
                        "contactMechPurposeTypeId", "PRIMARY_PHONE",
                        "userLogin", userLogin
                );
                dispatcher.runSync("createPartyContactMech", telecomPurposeCtx);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("partyId", partyId);
            return result;
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError("Error creating customer: " + e.getMessage());
        }
    }

    public static Map<String, Object> updateCustomer(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        String emailAddress = (String) context.get("emailAddress");
        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        String contactNumber = (String) context.get("contactNumber");
        String address1 = (String) context.get("address1");
        String city = (String) context.get("city");
        String postalCode = (String) context.get("postalCode");

        try {
            Map<String, Object> findCtx = UtilMisc.toMap("emailAddress", emailAddress, "userLogin", userLogin);
            Map<String, Object> findResult = dispatcher.runSync("findCustomer", findCtx);
            List<GenericValue> existing = (List<GenericValue>) findResult.get("customers");
            
            GenericValue customer = null;
            if (UtilValidate.isNotEmpty(existing)) {
                for(GenericValue cust : existing) {
                    if (emailAddress.equals(cust.getString("emailAddress"))) {
                        customer = cust;
                        break;
                    }
                }
            }

            if (customer == null) {
                return ServiceUtil.returnError("Customer with email " + emailAddress + " not found.");
            }

            String partyId = customer.getString("partyId");
            
            // Update Person if provided
            if (UtilValidate.isNotEmpty(firstName) || UtilValidate.isNotEmpty(lastName)) {
                Map<String, Object> updatePersonCtx = UtilMisc.toMap("partyId", partyId, "userLogin", userLogin);
                if (UtilValidate.isNotEmpty(firstName)) updatePersonCtx.put("firstName", firstName);
                if (UtilValidate.isNotEmpty(lastName)) updatePersonCtx.put("lastName", lastName);
                dispatcher.runSync("updatePerson", updatePersonCtx);
            }

            // Update Phone if provided
            if (UtilValidate.isNotEmpty(contactNumber)) {
                // Find existing phone to update or create new
                List<GenericValue> phones = delegator.findByAnd("PartyContactMechPurpose", 
                    UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_PHONE"), null, false);
                phones = org.apache.ofbiz.entity.util.EntityUtil.filterByDate(phones);
                if (UtilValidate.isNotEmpty(phones)) {
                    String contactMechId = phones.get(0).getString("contactMechId");
                    Map<String, Object> updateTelecomCtx = UtilMisc.toMap(
                            "partyId", partyId,
                            "contactMechId", contactMechId,
                            "contactNumber", contactNumber,
                            "userLogin", userLogin
                    );
                    dispatcher.runSync("updatePartyTelecomNumber", updateTelecomCtx);
                } else {
                    Map<String, Object> telecomCtx = UtilMisc.toMap("contactNumber", contactNumber, "userLogin", userLogin);
                    Map<String, Object> telecomResult = dispatcher.runSync("createTelecomNumber", telecomCtx);
                    String telecomContactMechId = (String) telecomResult.get("contactMechId");

                    Map<String, Object> telecomPurposeCtx = UtilMisc.toMap(
                            "partyId", partyId,
                            "contactMechId", telecomContactMechId,
                            "contactMechTypeId", "TELECOM_NUMBER",
                            "contactMechPurposeTypeId", "PRIMARY_PHONE",
                            "userLogin", userLogin
                    );
                    dispatcher.runSync("createPartyContactMech", telecomPurposeCtx);
                }
            }

            // Update Postal Address if provided
            if (UtilValidate.isNotEmpty(address1) && UtilValidate.isNotEmpty(city)) {
                List<GenericValue> addresses = delegator.findByAnd("PartyContactMechPurpose", 
                    UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_LOCATION"), null, false);
                addresses = org.apache.ofbiz.entity.util.EntityUtil.filterByDate(addresses);
                if (UtilValidate.isNotEmpty(addresses)) {
                    String contactMechId = addresses.get(0).getString("contactMechId");
                    Map<String, Object> updateAddressCtx = UtilMisc.toMap(
                            "partyId", partyId,
                            "contactMechId", contactMechId,
                            "address1", address1,
                            "city", city,
                            "postalCode", postalCode,
                            "userLogin", userLogin
                    );
                    dispatcher.runSync("updatePartyPostalAddress", updateAddressCtx);
                } else {
                    Map<String, Object> addressCtx = UtilMisc.toMap(
                            "address1", address1,
                            "city", city,
                            "postalCode", postalCode,
                            "userLogin", userLogin
                    );
                    Map<String, Object> addressResult = dispatcher.runSync("createPostalAddress", addressCtx);
                    String addressContactMechId = (String) addressResult.get("contactMechId");

                    Map<String, Object> addressPurposeCtx = UtilMisc.toMap(
                            "partyId", partyId,
                            "contactMechId", addressContactMechId,
                            "contactMechTypeId", "POSTAL_ADDRESS",
                            "contactMechPurposeTypeId", "PRIMARY_LOCATION",
                            "userLogin", userLogin
                    );
                    dispatcher.runSync("createPartyContactMech", addressPurposeCtx);
                }
            }

            return ServiceUtil.returnSuccess();
        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError("Error updating customer: " + e.getMessage());
        }
    }

    public static Map<String, Object> createCustomerRelationship(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            Map<String, Object> relCtx = UtilMisc.toMap(
                    "partyIdTo", context.get("partyIdTo"),
                    "partyIdFrom", context.get("partyIdFrom"),
                    "roleTypeIdFrom", "_NA_",
                    "roleTypeIdTo", "_NA_",
                    "partyRelationshipTypeId", context.get("partyRelationshipTypeId"),
                    "userLogin", userLogin
            );
            dispatcher.runSync("createPartyRelationship", relCtx);
            return ServiceUtil.returnSuccess();
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError("Error creating relationship: " + e.getMessage());
        }
    }

    public static Map<String, Object> updateCustomerRelationship(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            // Need fromDate to update, let's find the active one
            Delegator delegator = dctx.getDelegator();
            List<GenericValue> rels = delegator.findByAnd("PartyRelationship", 
                UtilMisc.toMap("partyIdTo", context.get("partyIdTo"), 
                               "partyIdFrom", context.get("partyIdFrom"), 
                               "partyRelationshipTypeId", context.get("partyRelationshipTypeId")), 
                null, false);
            rels = org.apache.ofbiz.entity.util.EntityUtil.filterByDate(rels);
            
            if (UtilValidate.isEmpty(rels)) {
                return ServiceUtil.returnError("Relationship not found to update.");
            }
            
            GenericValue rel = rels.get(0);

            Map<String, Object> relCtx = UtilMisc.toMap(
                    "partyIdTo", rel.getString("partyIdTo"),
                    "partyIdFrom", rel.getString("partyIdFrom"),
                    "roleTypeIdFrom", rel.getString("roleTypeIdFrom"),
                    "roleTypeIdTo", rel.getString("roleTypeIdTo"),
                    "fromDate", rel.getTimestamp("fromDate"),
                    "statusId", context.get("statusId"),
                    "userLogin", userLogin
            );
            dispatcher.runSync("updatePartyRelationship", relCtx);
            return ServiceUtil.returnSuccess();
        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError("Error updating relationship: " + e.getMessage());
        }
    }
}
