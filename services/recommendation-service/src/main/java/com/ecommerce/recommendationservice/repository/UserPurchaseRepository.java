package com.ecommerce.recommendationservice.repository;

import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPurchaseRepository extends MongoRepository<UserPurchaseDocument, String> {
}
