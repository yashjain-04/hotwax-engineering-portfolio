package com.HotWax.RestfulAPIDev.service;

import com.HotWax.RestfulAPIDev.dto.OrderHeaderDTO;
import com.HotWax.RestfulAPIDev.dto.OrderItemDTO;
import com.HotWax.RestfulAPIDev.model.*;
import com.HotWax.RestfulAPIDev.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
//@Transactional
public class OrderItemService {

    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderHeaderRepository orderHeaderRepository;
    @Autowired private ProductRepository productRepository;

    //Add an Order Item
    public OrderItem addItem(Integer orderId, OrderItemDTO itemDTO) {
        OrderHeader order = orderHeaderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Product product = productRepository.findById(itemDTO.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        OrderItem item = new OrderItem();
        item.setOrderHeader(order);
        item.setProduct(product);
        item.setQuantity(itemDTO.getQuantity());
        item.setStatus(itemDTO.getStatus());

        return orderItemRepository.save(item);
    }

    //Update an Order Item
    public OrderItem updateItem(Integer orderId, Integer seqId, OrderItemDTO itemDTO) {
        OrderItemId id = new OrderItemId();
        id.setOrderHeader(orderId);
        id.setOrderItemSeqId(seqId);

        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order Item not found"));

        item.setQuantity(itemDTO.getQuantity());
        item.setStatus(itemDTO.getStatus());

        return orderItemRepository.save(item);
    }

    //Delete an Order Item
    public void deleteItem(Integer orderId, Integer seqId) {
        OrderItemId id = new OrderItemId();
        id.setOrderHeader(orderId);
        id.setOrderItemSeqId(seqId);

        orderItemRepository.deleteById(id);
    }
}
