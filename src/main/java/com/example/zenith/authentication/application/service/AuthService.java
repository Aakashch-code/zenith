package com.example.zenith.authentication.application.service;

import com.example.zenith.authentication.application.dto.AuthResponse;
import com.example.zenith.authentication.application.dto.LoginRequest;
import com.example.zenith.authentication.application.dto.RegisterRequest;
import com.example.zenith.authentication.infrastructure.persistence.UserRepository;
import com.example.zenith.authentication.infrastructure.security.JwtUtil;
import com.example.zenith.entity.Account;
import com.example.zenith.entity.AccountStatus;
import com.example.zenith.entity.User;
import com.example.zenith.repositoy.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final AccountRepository accountRepository;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public void registerUser(RegisterRequest request) {
        if (repo.existsByUsername(request.getUsername())
                || repo.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("User registration failed: Username or Email already exists.");
        }

        User user = new User(
                null,
                request.getUsername(),
                request.getEmail(),
                encoder.encode(request.getPassword()),
                "USER"
        );

        User savedUser = repo.save(user);

        Account newAccount = Account.builder()
                .userId(savedUser.getId())
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(newAccount);
    }

    public AuthResponse loginUser(LoginRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getLogin(),
                        request.getPassword()
                )
        );

        User user = repo.findByUsernameOrEmail(request.getLogin())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String token = jwtUtil.generateToken(user.getId());

        return new AuthResponse(token);
    }
}