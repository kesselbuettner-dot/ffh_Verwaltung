package de.bierverein.api;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberMasterSeparationTest {
    @Test void onlyAdminMayCreateEditOrDeleteMasterData() throws Exception {
        for (String name : new String[]{"create", "update", "delete"}) {
            Method method = switch (name) {
                case "create" -> MemberController.class.getMethod(name, MemberDtos.MemberRequest.class, Authentication.class);
                case "update" -> MemberController.class.getMethod(name, Long.class, MemberDtos.MemberRequest.class, Authentication.class);
                default -> MemberController.class.getMethod(name, Long.class, Authentication.class);
            };
            assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
        }
    }

    @Test void updatingMemberMasterDataDoesNotDisableLinkedLogin() {
        MemberRepository memberRepo = mock(MemberRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        MemberService service = new MemberService(memberRepo, userRepo, encoder);
        Member member = new Member();
        member.setName("Vorher");
        member.setActive(true);
        AppUser account = new AppUser();
        account.setUsername("vorhanden@example.org");
        account.setEnabled(true);
        account.setRole(Role.MEMBER);
        member.setUser(account);
        when(memberRepo.findById(10L)).thenReturn(Optional.of(member));
        when(memberRepo.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
        MemberDtos.MemberRequest input = new MemberDtos.MemberRequest("Nachher", "kontakt@example.org",
                null, null, true, null, null, null, null);
        var result = service.update(10L, input, null);
        assertEquals("Nachher", result.name());
        assertTrue(account.isEnabled(), "Member editing must not change an existing login");
        verify(userRepo, never()).save(any(AppUser.class));
    }
}
