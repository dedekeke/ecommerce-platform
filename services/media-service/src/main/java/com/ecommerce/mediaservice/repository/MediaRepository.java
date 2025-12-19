package com.ecommerce.mediaservice.repository;

import com.ecommerce.mediaservice.document.Media;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface MediaRepository extends MongoRepository<Media, String> {

    /**
     * Find all media uploaded by a specific user
     */
    List<Media> findByUploadedBy(String uploadedBy);

    /**
     * Find media by content type
     */
    List<Media> findByContentTypeStartingWith(String contentTypePrefix);
}
