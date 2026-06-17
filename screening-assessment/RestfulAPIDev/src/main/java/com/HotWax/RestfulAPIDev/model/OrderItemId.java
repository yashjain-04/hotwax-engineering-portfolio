package com.HotWax.RestfulAPIDev.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderItemId implements Serializable {
    private Integer orderHeader; // Matches variable name in OrderItem
    private Integer orderItemSeqId;
}
