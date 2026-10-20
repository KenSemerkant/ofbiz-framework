package notification.adapters.rest;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedNotificationJpa extends JpaRepository<ProcessedNotification, String> {}
