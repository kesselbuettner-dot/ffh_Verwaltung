package de.bierverein.api;
import jakarta.persistence.*;
@Entity @Table(name="device_locations",uniqueConstraints=@UniqueConstraint(columnNames="name"))
public class DeviceLocation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false) private boolean fireRelevant;
 @Column(nullable=false) private boolean active=true;
 public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
 public boolean isFireRelevant(){return fireRelevant;} public void setFireRelevant(boolean v){fireRelevant=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
