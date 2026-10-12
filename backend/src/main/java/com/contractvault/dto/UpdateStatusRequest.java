package com.contractvault.dto;

import com.contractvault.model.ContractStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateStatusRequest {

    @NotNull(message = "Status is required and must be DRAFT, REVIEW, or APPROVED")
    private ContractStatus status;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(ContractStatus status) {
        this.status = status;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }
}
