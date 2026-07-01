package com.happyhearts.repository;

import com.happyhearts.model.SchoolPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolPeriodRepository extends JpaRepository<SchoolPeriod, UUID> {
}
