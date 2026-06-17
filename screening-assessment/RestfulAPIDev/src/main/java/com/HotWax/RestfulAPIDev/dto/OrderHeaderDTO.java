package com.HotWax.RestfulAPIDev.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class OrderHeaderDTO {
    private Integer orderId;
    private LocalDate orderDate;
    private Integer customerId;
    private Integer shippingContactMechId;
    private Integer billingContactMechId;
    private List<OrderItemDTO> orderItems;
}
