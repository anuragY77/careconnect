package com.careconnect.auth.service;

/** A staff account together with its temporary password, shown once and never stored in clear. */
public record StaffCredential(StaffAccountView account, String temporaryPassword) {

    @Override
    public String toString() {
        return "StaffCredential[account=" + account + ", temporaryPassword=***]";
    }
}
