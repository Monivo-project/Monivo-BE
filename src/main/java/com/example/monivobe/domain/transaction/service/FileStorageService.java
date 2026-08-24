package com.example.monivobe.domain.transaction.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {
    String upload(MultipartFile file);
    InputStream download(String fileUrl);
}