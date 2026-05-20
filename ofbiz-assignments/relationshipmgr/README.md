# Relationship Manager Component (Apache OFBiz)

A custom Apache OFBiz component built to manage complex Party and Contact Mechanism relationships, demonstrating enterprise data modeling and unified Groovy service orchestration.

## 🏗️ Architecture & Data Modeling

This component implements advanced relational database concepts using the OFBiz Entity Engine:

* **Supertype/Subtype Modeling:** * Modeled `RmParty` (Supertype) extending into `RmPerson` and `RmPartyGroup` (Subtypes).
    * Modeled `RmContactMech` (Supertype) extending into `RmPostalAddress` and `RmTelecomNumber`.
* **Intersection / Association Entities:** * Implemented `RmPartyRole` and `RmPartyContactMech` to handle many-to-many relationships securely, using composite primary keys (including `contactMechPurposeId`).
* **Data Classifications:** Utilized OFBiz seed data to establish lookup tables (`RmPartyType`, `RmRoleType`, `RmContactMechPurpose`) for strict data integrity.

## ⚙️ Backend Logic (Groovy Services)

Instead of relying on fragmented auto-generated UI workflows, I developed unified Groovy services (`RelationshipmgrServices.groovy`) to handle complex entity creation in a single transaction:
* **Transactional Integrity:** Services simultaneously generate the ID, insert the Supertype record, insert the Subtype record, and map the Intersection entity.
* **Streamlined UX:** Allows the frontend to submit a single, clean form payload while the backend orchestrates the multi-table inserts safely.

## 🖥️ UI / UX Implementation

* **Tabbed Interface:** Refactored standard OFBiz screen decorators to utilize `CommonTabBarMenu`, providing a clean, modern workspace divided into Persons, Organizations, and Contact Mechanisms.
* **Unified Forms:** Replaced multi-step data entry with unified XML forms, utilizing dynamic `<drop-down>` and `<entity-options>` tags to enforce relational constraints directly in the UI.
