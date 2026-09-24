package com.example.zenith.service;

import com.example.zenith.entity.Account;
import com.example.zenith.entity.AccountStatus;
import com.example.zenith.repositoy.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledInterestService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.04");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");
    private static final Set<Long> SYSTEM_ACCOUNTS = Set.of(1L, 2L, 4L);

    @Scheduled(cron = "0 0 0 1 * ?")
    public void calculateAndApplyMonthlyInterest() {
        log.info("Starting monthly interest calculation job...");

        List<Account> eligibleAccounts = accountRepository
                .findByStatusAndIdNotIn(AccountStatus.ACTIVE, SYSTEM_ACCOUNTS);

        int successCount = 0;

        for (Account account : eligibleAccounts) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {

                // Formula: (Balance * 0.04) / 12
                BigDecimal interest = account.getBalance()
                        .multiply(ANNUAL_RATE)
                        .divide(MONTHS_IN_YEAR, 4, RoundingMode.HALF_EVEN);

                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        ledgerService.applyInterestToAccount(account.getId(), interest);
                        successCount++;
                    } catch (Exception e) {
                        log.error("Failed to process interest for account ID: {}", account.getId(), e);
                    }
                }
            }
        }

        log.info("Monthly interest job finished. Successfully applied interest to {} accounts.", successCount);
    }
}