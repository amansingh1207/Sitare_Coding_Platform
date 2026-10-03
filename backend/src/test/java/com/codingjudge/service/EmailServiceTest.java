package com.codingjudge.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Test
    void sendsVerificationOtpThroughMailSender() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailService emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "mailFromName", "CodingJudge Test");

        emailService.sendVerificationOtp("user@example.com", "123456", 10);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("Coding Judge - OTP Verification", captor.getValue().getSubject());
        assertTrue(captor.getValue().getContent().toString().contains("123456"));
    }

    @Test
    void sendsPasswordResetOtpThroughMailSender() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailService emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "mailFromName", "CodingJudge Test");

        emailService.sendPasswordResetOtp("user@example.com", "654321", 10);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getContent().toString().contains("654321"));
    }

    
}
