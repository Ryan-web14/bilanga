package com.sni.bilanga.mailService.repository;

import com.sni.bilanga.mailService.model.EmailDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long>, JpaSpecificationExecutor<EmailDeliveryLog> {

    Optional<EmailDeliveryLog> findByEmailNumber(String emailNumber);
}
