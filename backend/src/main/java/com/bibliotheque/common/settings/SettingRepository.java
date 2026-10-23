package com.bibliotheque.common.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, SettingCode> {
}
