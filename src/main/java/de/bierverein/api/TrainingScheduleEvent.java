package de.bierverein.api;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "training_schedule_events")
public class TrainingScheduleEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 20) private String type;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 3000) private String notes;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    @Column(nullable = false) private boolean recurring;
    @Column(nullable=false) private boolean lastWeekdayOfMonth;
    @Column(length = 100) private String weekdays;
    @Column(nullable = false) private boolean allDay;
    private LocalTime startTime;
    private LocalTime endTime;
    @Column(length = 2000) private String responsibleMemberIds;
    @Column(nullable = false, length = 20) private String audienceType = "ALL";
    @Column(length = 64) private String audienceRole;
    @Column(nullable = false) private boolean registrationRequired;
    @Column(nullable=false) private boolean deviceInspection;
    @Column(length=2000) private String deviceLocations;
    @Column(length=2000) private String deviceCategories;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false, length = 320) private String createdBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId(){return id;} public String getType(){return type;} public void setType(String v){type=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;} public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;}
    public boolean isRecurring(){return recurring;} public void setRecurring(boolean v){recurring=v;} public String getWeekdays(){return weekdays;} public void setWeekdays(String v){weekdays=v;}
    public boolean isLastWeekdayOfMonth(){return lastWeekdayOfMonth;} public void setLastWeekdayOfMonth(boolean v){lastWeekdayOfMonth=v;}
    public boolean isAllDay(){return allDay;} public void setAllDay(boolean v){allDay=v;} public LocalTime getStartTime(){return startTime;} public void setStartTime(LocalTime v){startTime=v;} public LocalTime getEndTime(){return endTime;} public void setEndTime(LocalTime v){endTime=v;}
    public String getResponsibleMemberIds(){return responsibleMemberIds;} public void setResponsibleMemberIds(String v){responsibleMemberIds=v;}
    public String getAudienceType(){return audienceType;} public void setAudienceType(String v){audienceType=v;} public String getAudienceRole(){return audienceRole;} public void setAudienceRole(String v){audienceRole=v;}
    public boolean isRegistrationRequired(){return registrationRequired;} public void setRegistrationRequired(boolean v){registrationRequired=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public boolean isDeviceInspection(){return deviceInspection;} public void setDeviceInspection(boolean v){deviceInspection=v;} public String getDeviceLocations(){return deviceLocations;} public void setDeviceLocations(String v){deviceLocations=v;} public String getDeviceCategories(){return deviceCategories;} public void setDeviceCategories(String v){deviceCategories=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public void touch(){updatedAt=Instant.now();}
}
