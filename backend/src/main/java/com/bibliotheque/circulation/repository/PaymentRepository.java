package com.bibliotheque.circulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bibliotheque.circulation.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
