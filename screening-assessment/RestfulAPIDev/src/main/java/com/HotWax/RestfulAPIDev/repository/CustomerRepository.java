package com.HotWax.RestfulAPIDev.repository;

import com.HotWax.RestfulAPIDev.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Integer> { }
