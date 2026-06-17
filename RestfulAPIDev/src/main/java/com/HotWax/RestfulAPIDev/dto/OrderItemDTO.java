package com.HotWax.RestfulAPIDev.dto;

import lombok.Data;

@Data
public class OrderItemDTO {
    private Integer orderItemSeqId; // For updates/retrieval
    private Integer productId;
    private Integer quantity;
    private String status;
}
