package com.mycom.myapp.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// TODO(auth 담당): MemberRepository로 email 조회해서 UserDetails 구현체 반환
// 지금은 로그인 로직 없이 컴파일만 되게
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
