package com.walletledger.walletbackend.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.request.AddMoneyRequest;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.request.LoginRequest;
import com.walletledger.walletbackend.dto.request.RegisterRequest;
import com.walletledger.walletbackend.dto.request.SendMoneyRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registerRequestAcceptsValidInputAndRejectsInvalidInput() {
        RegisterRequest valid = new RegisterRequest("Ada Lovelace", "ada@example.com", "9876543210", "password123", "1234");
        RegisterRequest invalid = new RegisterRequest("", "not-an-email", "123", "short", "12");

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void loginRequestRejectsBlankCredentialsOrAnInvalidEmail() {
        LoginRequest invalid = new LoginRequest("not-an-email", "");

        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void addMoneyRequestEnforcesTheMinimumAmount() {
        AddMoneyRequest valid = new AddMoneyRequest(new BigDecimal("1.00"));
        AddMoneyRequest invalid = new AddMoneyRequest(new BigDecimal("0.99"));

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void sendMoneyRequestValidatesAmountAndPin() {
        SendMoneyRequest valid = new SendMoneyRequest("request-123", 7L, new BigDecimal("250.00"), "Dinner", "1234");
        SendMoneyRequest invalid = new SendMoneyRequest("request-123", 7L, BigDecimal.ZERO, "Dinner", "123");

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void fraudReviewRequestRequiresAnAction() {
        FraudReviewRequest valid = new FraudReviewRequest(FraudAction.BLOCK, "Suspicious activity");
        FraudReviewRequest invalid = new FraudReviewRequest(null, "Suspicious activity");

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }
}
