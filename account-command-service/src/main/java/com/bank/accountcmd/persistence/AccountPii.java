package com.bank.accountcmd.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Vertical-fragmentation "sensitive" fragment — docs/DATA_MODEL.md §4, v2 §7.2.
 * Candidate for stricter access control / encryption at rest.
 */
@Entity
@Table(name = "account_pii")
public class AccountPii {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "holder_name")
    private String holderName;

    /** JSONB KYC document references. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kyc_documents", columnDefinition = "jsonb")
    private String kycDocuments;

    @Column(name = "registration_ip")
    private String registrationIp;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    protected AccountPii() {
    }

    public AccountPii(UUID accountId, String holderName, String kycDocuments,
                      String registrationIp, String address) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.kycDocuments = kycDocuments;
        this.registrationIp = registrationIp;
        this.address = address;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getKycDocuments() {
        return kycDocuments;
    }

    public String getRegistrationIp() {
        return registrationIp;
    }

    public String getAddress() {
        return address;
    }
}
