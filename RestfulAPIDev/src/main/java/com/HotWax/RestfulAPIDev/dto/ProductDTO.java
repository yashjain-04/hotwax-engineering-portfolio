package com.HotWax.RestfulAPIDev.dto;

import lombok.Data;

@Data
public class ProductDTO {
    private Integer productId;
    private String productName;
    private String color;
    private String size;
}
