package com.HotWax.RestfulAPIDev.dto;

import lombok.Data;

@Data
public class ContactMechDTO {
    private Integer contactMechId;
    private String streetAddress;
    private String city;
    private String state;
    private String postalCode;
    private String phoneNumber;
    private String email;
}
