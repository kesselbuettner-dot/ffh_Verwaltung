package de.bierverein.api;
import java.math.BigDecimal;
public final class MemberDtos { private MemberDtos(){}
 public record MemberResponse(Long id,String name,String firstName,String lastName,String email,String phone,String address,boolean active,BigDecimal balance,Long userId,String username,Role role,boolean loginEnabled){}
 public record MemberRequest(String name,String email,String phone,String address,boolean active,String username,String password,Role role,Boolean loginEnabled){}
 public record BalanceRequest(BigDecimal amount,String reason){}
}
