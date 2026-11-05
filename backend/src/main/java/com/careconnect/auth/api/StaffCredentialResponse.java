package com.careconnect.auth.api;

import com.careconnect.auth.service.StaffCredential;

/**
 * A staff account with its temporary password. This is the only time the password is shown, so
 * the response is never cached, and it is kept out of logs.
 */
public record StaffCredentialResponse(StaffAccountResponse account, String temporaryPassword) {

    static StaffCredentialResponse from(StaffCredential c) {
        return new StaffCredentialResponse(StaffAccountResponse.from(c.account()), c.temporaryPassword());
    }

    @Override
    public String toString() {
        return "StaffCredentialResponse[account=" + account + ", temporaryPassword=***]";
    }
}
