package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PaymentDetectorTest {
	private final PaymentDetector detector = new PaymentDetector(java.util.List.of());

	@Test
	void detectsPaidYou() {
		Optional<PaymentDetector.Payment> p = detector.detect("Steve paid you $50M.");
		assertTrue(p.isPresent());
		assertEquals("Steve", p.get().player());
		assertEquals(50_000_000L, p.get().amount());
	}

	@Test
	void detectsDonutSmpFormat() {
		Optional<PaymentDetector.Payment> p = detector.detect("jimbo01 paid you $ 75M");
		assertTrue(p.isPresent());
		assertEquals("jimbo01", p.get().player());
		assertEquals(75_000_000L, p.get().amount());
	}

	@Test
	void detectsOutgoingPayment() {
		Optional<PaymentDetector.Payment> p = detector.detectOutgoing("You paid BeanznBread $ 1B");
		assertTrue(p.isPresent());
		assertEquals("BeanznBread", p.get().player());
		assertEquals(1_000_000_000L, p.get().amount());
		assertTrue(detector.detect("You paid BeanznBread $ 1B").isEmpty());
	}

	@Test
	void detectsReceivedFrom() {
		Optional<PaymentDetector.Payment> p = detector.detect("You received $1.2B from .BedrockGuy");
		assertTrue(p.isPresent());
		assertEquals(".BedrockGuy", p.get().player());
		assertEquals(1_200_000_000L, p.get().amount());
	}

	@Test
	void stripsColorCodes() {
		Optional<PaymentDetector.Payment> p = detector.detect("§aAlex_§r paid you §e$45M");
		assertTrue(p.isPresent());
		assertEquals("Alex_", p.get().player());
		assertEquals(45_000_000L, p.get().amount());
	}

	@Test
	void ignoresOutgoingPayments() {
		assertTrue(detector.detect("You paid Steve $100M.").isEmpty());
	}

	@Test
	void ignoresSpoofedChat() {
		assertTrue(detector.detect("[VIP] Bob: Notch paid you $500M").isEmpty());
		assertTrue(detector.detect("Bob » Notch paid you $500M").isEmpty());
	}

	@Test
	void skipsInvalidPatterns() {
		PaymentDetector d = new PaymentDetector(java.util.List.of("((("));
		assertTrue(d.detect("Steve paid you $5M").isPresent());
	}
}
