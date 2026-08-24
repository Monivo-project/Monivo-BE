package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.dto.response.TransactionResDTO;
import com.example.monivobe.domain.transaction.event.TransactionUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TransactionResDTO.UploadResponse uploadFile(
            MultipartFile file,
            Member member
    ) {

        // 1. S3에 원본 파일 저장
        String fileKey = fileStorageService.upload(file);

        // 2. 이벤트 발행
        eventPublisher.publishEvent(
                new TransactionUploadedEvent(
                        member.getId(),
                        fileKey
                )
        );

        // 3. 즉시 응답
        return new TransactionResDTO.UploadResponse(
                "PROCESSING",
                "파일 업로드가 완료되었습니다."
        );
    }
}