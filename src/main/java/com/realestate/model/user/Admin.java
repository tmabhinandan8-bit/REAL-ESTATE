package com.realestate.model.user;

public class Admin extends User {
    private String adminDepartment;

    public Admin() {
        super();
        this.setUserRole("ADMIN");
        this.setVerified(true);
        this.adminDepartment = "Platform Operations";
    }

    public Admin(int userId, String fullName, String email, String mobileNumber, String department) {
        super(userId, fullName, email, mobileNumber, "ADMIN");
        this.setVerified(true);
        this.adminDepartment = department;
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Full system administration, approve/reject property listings, verify owners, view all financial transactions and audit logs.";
    }

    public boolean canApproveListings() {
        return true;
    }

    public boolean canVerifyUsers() {
        return true;
    }

    public String getAdminDepartment() {
        return adminDepartment;
    }

    public void setAdminDepartment(String adminDepartment) {
        this.adminDepartment = adminDepartment;
    }
}
