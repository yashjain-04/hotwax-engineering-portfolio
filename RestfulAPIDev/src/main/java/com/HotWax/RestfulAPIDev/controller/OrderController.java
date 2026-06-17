package com.HotWax.RestfulAPIDev.controller;

import com.HotWax.RestfulAPIDev.dto.OrderHeaderDTO;
import com.HotWax.RestfulAPIDev.model.OrderHeader;
import com.HotWax.RestfulAPIDev.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //Create an Order
    @PostMapping
    public ResponseEntity<OrderHeader> createOrder(@RequestBody OrderHeaderDTO orderDTO) {
        OrderHeader createdOrder = orderService.createOrder(orderDTO);
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }

    //Retrieve Order Details
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderHeader> getOrder(@PathVariable Integer orderId) {
        OrderHeader order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    //Update Order
    @PutMapping("/{orderId}")
    public ResponseEntity<OrderHeader> updateOrder(
            @PathVariable Integer orderId,
            @RequestBody OrderHeaderDTO orderDTO) {
        OrderHeader updatedOrder = orderService.updateOrder(orderId, orderDTO);
        return ResponseEntity.ok(updatedOrder);
    }

    //Delete an Order
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok("Order deleted successfully");
    }
}
