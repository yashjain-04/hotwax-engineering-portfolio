package com.HotWax.RestfulAPIDev.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Contact_Mech")
@Data
public class ContactMech {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer contactMechId;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 100)
    private String streetAddress;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false, length = 50)
    private String state;

    @Column(nullable = false, length = 20)
    private String postalCode;

    private String phoneNumber;
    private String email;
}
