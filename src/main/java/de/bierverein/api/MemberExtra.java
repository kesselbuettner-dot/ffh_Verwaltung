package de.bierverein.api;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="member_extra")
public class MemberExtra {
 @Id public Long memberId;
 public LocalDate birthDate;
 public LocalDate joinedOn;
 @Column(name="avatar_mime",length=20) public String avatarMime;
 @Column(name="avatar_data",columnDefinition="bytea") public byte[] avatarData;
 @Column(columnDefinition="TEXT") public String customJson="{}";
 protected MemberExtra(){}
 public MemberExtra(Long memberId){this.memberId=memberId;}
}
