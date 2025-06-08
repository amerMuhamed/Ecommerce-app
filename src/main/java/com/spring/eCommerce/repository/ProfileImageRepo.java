package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileImageRepo extends JpaRepository<ProfileImage, Long> {
}
