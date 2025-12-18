package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {

    Optional<NotificationTemplate> findByCode(String code);

    List<NotificationTemplate> findByType(NotificationType type);

    List<NotificationTemplate> findByActiveTrue();

    boolean existsByCode(String code);
}
