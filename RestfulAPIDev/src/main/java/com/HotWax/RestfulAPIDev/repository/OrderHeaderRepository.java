package com.HotWax.RestfulAPIDev.repository;

import com.HotWax.RestfulAPIDev.model.OrderHeader;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHeaderRepository extends JpaRepository<OrderHeader, Integer> { }
