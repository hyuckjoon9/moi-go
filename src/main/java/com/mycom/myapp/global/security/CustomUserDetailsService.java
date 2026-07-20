package com.mycom.myapp.global.security;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.repository.MemberRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public CustomUserDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        boolean enabled = member.getStatus() == MemberStatus.ACTIVE;
        return new User(
                member.getEmail(),
                member.getPassword(),
                enabled,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }
}
