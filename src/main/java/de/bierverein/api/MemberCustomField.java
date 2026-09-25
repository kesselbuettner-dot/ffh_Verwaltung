package de.bierverein.api;
import jakarta.persistence.*;
@Entity @Table(name="member_custom_fields",uniqueConstraints=@UniqueConstraint(columnNames="code"))
public class MemberCustomField {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true,length=48) public String code;
 @Column(nullable=false,length=100) public String title;
 @Column(nullable=false,length=12) public String type; // TEXT,DATE,NUMBER
 public boolean active=true;
 protected MemberCustomField(){}
 public MemberCustomField(String code,String title,String type){this.code=code;this.title=title;this.type=type;}
}
