# HotWax-RESTful-API-Dev-Assignment

## 🛠️ Tech Stack
- **Framework:** Spring Boot 3.5.9
- **ORM:** Spring Data JPA (Hibernate)
- **Database:** MySQL
- **Build Tool:** Maven

---

## 🚀 How to Run

### Database Setup
- Create a MySQL database named `oms`.

### Configuration
- Navigate to `src/main/resources/`.
- Rename `application.properties.example` to `application.properties`.
- Update the following properties with your local MySQL credentials:
  - `spring.datasource.username`
  - `spring.datasource.password`

### Run the Application
- Execute the application.
- The database schema and master data (**Customers, Products, and Addresses**) will be automatically initialized using:
  - `schema.sql`
  - `data.sql`

---

## 🗄️ Database Design

The system implements a relational database schema with the following entities:

- **Customer**  
  Stores customer identity and basic information.

- **ContactMech**  
  Manages shipping and billing addresses.

- **Product**  
  Stores the product catalog.

- **OrderHeader**  
  Links customers to their orders and associated addresses.

- **OrderItem**  
  Manages individual order line items using a composite key  
  (`order_id + order_item_seq_id`).

---

## 📸 API Scenarios & Endpoints

### 1. Create an Order
**Endpoint:** `POST /orders`

<img width="1919" height="1016" alt="Screenshot 2026-01-15 152205" src="https://github.com/user-attachments/assets/32a7aa14-341a-490b-ae07-d58274e8ffde" />


---

### 2. Retrieve Order Details
**Endpoint:** `GET /orders/{order_id}`

<img width="1919" height="1020" alt="Screenshot 2026-01-15 152218" src="https://github.com/user-attachments/assets/f710c2ee-3740-4949-9401-b5c07395fd9c" />


---

### 3. Update an Order
**Endpoint:** `PUT /orders/{order_id}`

<img width="1919" height="1022" alt="Screenshot 2026-01-15 152231" src="https://github.com/user-attachments/assets/e6f441eb-d18a-42d2-b66f-a2742738988c" />


---

### 4. Add an Order Item
**Endpoint:** `POST /orders/{order_id}/items`

<img width="1919" height="1021" alt="Screenshot 2026-01-15 152325" src="https://github.com/user-attachments/assets/b8eb626a-6456-4b9a-abc2-24da92d445d9" />


---

### 5. Update an Order Item
**Endpoint:** `PUT /orders/{order_id}/items/{order_item_seq_id}`

<img width="1919" height="1021" alt="Screenshot 2026-01-15 152347" src="https://github.com/user-attachments/assets/d24760c6-1996-4d7a-9427-b565c6d1d503" />


---

### 6. Delete an Order Item
**Endpoint:** `DELETE /orders/{order_id}/items/{order_item_seq_id}`

<img width="1919" height="1017" alt="Screenshot 2026-01-15 152402" src="https://github.com/user-attachments/assets/915801d9-10b7-483d-b058-305374ef7af2" />


---

### 7. Delete an Order
**Endpoint:** `DELETE /orders/{order_id}`

<img width="1919" height="1020" alt="Screenshot 2026-01-15 152428" src="https://github.com/user-attachments/assets/01a6be50-5a05-4f9c-bfac-34e2e9d5d576" />

