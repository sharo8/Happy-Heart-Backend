package com.happyhearts.repository;

import com.happyhearts.model.PdfImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PdfImportRepository extends JpaRepository<PdfImport, UUID> {
}
