package de.bierverein.api;
import jakarta.persistence.*;
@Entity @Table(name="fire_qualification_types",uniqueConstraints=@UniqueConstraint(columnNames="code"))
public class FireQualificationType {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true,length=64) public String code;
 @Column(nullable=false,length=120) public String title;
 @Column(length=80) public String icon="📋";
 @Column(length=10) public String shortLabel;
 @Column(length=32) public String category;
 public boolean tracked=false;
 public boolean sensitive=false;
 public int warningDays=30;
 public int intervalMonths=0;
 public FireQualificationType(){}
 public FireQualificationType(String code,String title,String icon,boolean tracked,boolean sensitive,int warningDays,int intervalMonths){
  this.code=code;this.title=title;this.icon=icon;this.tracked=tracked;this.sensitive=sensitive;this.warningDays=warningDays;this.intervalMonths=intervalMonths;
 }
}
