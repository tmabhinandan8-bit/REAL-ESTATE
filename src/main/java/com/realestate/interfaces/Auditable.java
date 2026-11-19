package com.realestate.interfaces;

import java.sql.Timestamp;

/**
 * Interface defining audit tracking for compliance and logs.
 */
public interface Auditable {
    Timestamp getCreatedAt();
    String getAuditSummary();
}
