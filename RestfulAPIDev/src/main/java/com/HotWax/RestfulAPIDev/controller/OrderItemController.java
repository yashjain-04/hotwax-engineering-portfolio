package com.HotWax.RestfulAPIDev.controller;

import com.HotWax.RestfulAPIDev.dto.OrderItemDTO;
import com.HotWax.RestfulAPIDev.model.OrderItem;
import com.HotWax.RestfulAPIDev.service.OrderItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/{orderId}/items")
public class OrderItemController {

    private final OrderItemService orderItemService;

    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    // Add an Order Item
    @PostMapping
    public ResponseEntity<OrderItem> addItem(@PathVariable Integer orderId, @RequestBody OrderItemDTO itemDTO) {
        return ResponseEntity.ok(orderItemService.addItem(orderId, itemDTO));
    }

    // Update an Order Item
    @PutMapping("/{seqId}")
    public ResponseEntity<OrderItem> updateItem(
            @PathVariable Integer orderId,
            @PathVariable Integer seqId,
            @RequestBody OrderItemDTO itemDTO) {
        return ResponseEntity.ok(orderItemService.updateItem(orderId, seqId, itemDTO));
    }

    //Delete an Order Item
    @DeleteMapping("/{seqId}")
    public ResponseEntity<String> deleteItem(@PathVariable Integer orderId, @PathVariable Integer seqId) {
        orderItemService.deleteItem(orderId, seqId);
        return ResponseEntity.ok("Order item deleted successfully");
    }
}
