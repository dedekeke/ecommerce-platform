package com.ecommerce.reviewservice.repository;

import com.ecommerce.reviewservice.domain.ReviewDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends MongoRepository<ReviewDocument, String> {

    Page<ReviewDocument> findByProductId(String productId, Pageable pageable);

    List<ReviewDocument> findByProductId(String productId);

    long countByProductId(String productId);
}
