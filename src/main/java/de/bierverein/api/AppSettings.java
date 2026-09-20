package de.bierverein.api;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_settings")
public class AppSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String appName = "FFH Verwaltung";

    @Column(nullable = false, length = 20)
    private String primaryColor = "#c51f2d";

    @Column(nullable = false, length = 20)
    private String navColor = "#071827";

    @Column(nullable = false, length = 20)
    private String accentColor = "#1479e9";

    @Column(nullable = false, length = 1000)
    private String menuOrder = "dashboard,members,theke,shopping,purchase,inventory,articles,devices,drivebook,devicebook,material,events,firewehr,training,finance,documents,calendar,donations,admin,admin-members,admin-users,admin-settings";

    @Column(nullable = false, length = 1000)
    private String hiddenMenuItems = "";

    @Column(length = 500)
    private String dashboardWidgets = "messages,dates,stats,stock,finance,quick,offers,status,system";

    @Column(length = 500)
    private String messageEditorRoles = "ADMIN,VORSTAND";

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] logoData;

    @Column(length = 100)
    private String logoContentType;

    public Long getId() { return id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getNavColor() { return navColor; }
    public void setNavColor(String navColor) { this.navColor = navColor; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getMenuOrder() { return menuOrder; }
    public void setMenuOrder(String menuOrder) { this.menuOrder = menuOrder; }
    public String getHiddenMenuItems() { return hiddenMenuItems; }
    public void setHiddenMenuItems(String hiddenMenuItems) { this.hiddenMenuItems = hiddenMenuItems; }
    public String getDashboardWidgets() { return dashboardWidgets; }
    public void setDashboardWidgets(String dashboardWidgets) { this.dashboardWidgets = dashboardWidgets; }
    public String getMessageEditorRoles() { return messageEditorRoles; }
    public void setMessageEditorRoles(String messageEditorRoles) { this.messageEditorRoles = messageEditorRoles; }
    public byte[] getLogoData() { return logoData; }
    public void setLogoData(byte[] logoData) { this.logoData = logoData; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
}
