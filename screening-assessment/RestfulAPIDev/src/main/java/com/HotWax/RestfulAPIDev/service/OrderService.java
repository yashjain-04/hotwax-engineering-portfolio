package com.HotWax.RestfulAPIDev.service;

import com.HotWax.RestfulAPIDev.dto.OrderHeaderDTO;
import com.HotWax.RestfulAPIDev.dto.OrderItemDTO;
import com.HotWax.RestfulAPIDev.model.*;
import com.HotWax.RestfulAPIDev.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
//@Transactional
public class OrderService {

    @Autowired
    private OrderHeaderRepository orderHeaderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ContactMechRepository contactMechRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    // Create an Order
    @Transactional
    public OrderHeader createOrder(OrderHeaderDTO orderDTO) {
        Customer customer = customerRepository.findById(orderDTO.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + orderDTO.getCustomerId()));

        ContactMech shipping = contactMechRepository.findById(orderDTO.getShippingContactMechId())
                .orElseThrow(() -> new RuntimeException("Shipping address not found with ID: " + orderDTO.getShippingContactMechId()));

        ContactMech billing = contactMechRepository.findById(orderDTO.getBillingContactMechId())
                .orElseThrow(() -> new RuntimeException("Billing address not found with ID: " + orderDTO.getBillingContactMechId()));

        OrderHeader order = new OrderHeader();
        order.setOrderDate(orderDTO.getOrderDate());
        order.setCustomer(customer);
        order.setShippingContactMech(shipping);
        order.setBillingContactMech(billing);

        OrderHeader savedOrder = orderHeaderRepository.save(order);

        if (orderDTO.getOrderItems() != null && !orderDTO.getOrderItems().isEmpty()) {
            int seqId = 1;
            for (OrderItemDTO itemDTO : orderDTO.getOrderItems()) {
                Product product = productRepository.findById(itemDTO.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found ID: " + itemDTO.getProductId()));

                OrderItem item = new OrderItem();
                item.setOrderHeader(savedOrder);
                item.setProduct(product);
                item.setQuantity(itemDTO.getQuantity());
                item.setStatus(itemDTO.getStatus());

                item.setOrderItemSeqId(seqId++);

                orderItemRepository.save(item);
            }
        }

        return savedOrder;
    }

    // Retrieve Order
    public OrderHeader getOrder(Integer orderId) {
        return orderHeaderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
    }

    // Update Order
    public OrderHeader updateOrder(Integer orderId, OrderHeaderDTO dto) {
        OrderHeader order = getOrder(orderId); // Reuses your existing getOrder logic

        ContactMech shipping = contactMechRepository.findById(dto.getShippingContactMechId())
                .orElseThrow(() -> new RuntimeException("Shipping address not found"));
        ContactMech billing = contactMechRepository.findById(dto.getBillingContactMechId())
                .orElseThrow(() -> new RuntimeException("Billing address not found"));

        order.setShippingContactMech(shipping);
        order.setBillingContactMech(billing);

        return orderHeaderRepository.save(order);
    }

    // Delete Order
    public void deleteOrder(Integer orderId) {
        orderHeaderRepository.deleteById(orderId);
    }
}
