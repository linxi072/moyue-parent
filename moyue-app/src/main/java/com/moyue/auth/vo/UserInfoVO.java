package com.moyue.auth.vo;

/**
 * 用户资料：id / phone / nickname / role / status。
 */
public class UserInfoVO {

    private Long id;

    private String phone;

    private String nickname;

    /** 1 读者 / 2 作者 / 3 管理员 */
    private Integer role;

    /** 0 禁用 / 1 正常 */
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
