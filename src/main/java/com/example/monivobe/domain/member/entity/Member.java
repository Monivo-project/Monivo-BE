package com.example.monivobe.domain.member.entity;

import com.example.monivobe.domain.member.enums.SocialType;
import com.example.monivobe.global.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true)
    private String name;

    @NotBlank
    private String socialUid;

    @NotNull
    @Enumerated(EnumType.STRING)
    private SocialType socialType;

    @Email
    @NotBlank
    private String email;

    // 닉네임 설정
    public void updateName(String nickname) {
        this.name = nickname;
    }
}
