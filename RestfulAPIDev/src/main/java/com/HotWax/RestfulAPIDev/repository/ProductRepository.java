package com.HotWax.RestfulAPIDev.repository;

import com.HotWax.RestfulAPIDev.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> { }
