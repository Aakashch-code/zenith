package com.example.zenith.authentication.application.service;

import com.example.zenith.authentication.infrastructure.persistence.UserRepository;
import com.example.zenith.entity.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {        try {
            Long userId = Long.parseLong(identifier);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User ID not found: " + userId));
        } catch (NumberFormatException e) {
            return userRepository.findByUsernameOrEmail(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
        }
    }
}