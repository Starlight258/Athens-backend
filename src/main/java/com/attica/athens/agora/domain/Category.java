package com.attica.athens.agora.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Category {

    @Id
    private String code;

    @ManyToOne()
    @JoinColumn(name = "parent_code")
    private Category parentCode;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private String name;
}