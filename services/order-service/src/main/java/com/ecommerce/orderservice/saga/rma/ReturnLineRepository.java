package com.ecommerce.orderservice.saga.rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnLineRepository extends JpaRepository<ReturnLine, String> {

    List<ReturnLine> findByReturnRequestId(String returnId);
}
