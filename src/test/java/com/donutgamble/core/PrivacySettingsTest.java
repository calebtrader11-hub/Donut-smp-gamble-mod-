package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrivacySettingsTest {
	@Test
	void nothingHiddenWhileOff() {
		PrivacySettings p = new PrivacySettings();
		for (PrivacySettings.Surface s : PrivacySettings.Surface.values()) {
			assertFalse(p.hides(s, false), s.name());
		}
	}

	@Test
	void streamerModeHidesPrivateSurfacesButNotAuctionByDefault() {
		PrivacySettings p = new PrivacySettings();
		p.streamerMode = true;
		assertTrue(p.hides(PrivacySettings.Surface.DASHBOARD, false));
		assertTrue(p.hides(PrivacySettings.Surface.PAPER_HUD, false));
		assertTrue(p.hides(PrivacySettings.Surface.TOASTS, false));
		assertTrue(p.hides(PrivacySettings.Surface.PLAYER_STATS, false));
		assertTrue(p.hides(PrivacySettings.Surface.FINANCE, false));
		assertFalse(p.hides(PrivacySettings.Surface.AUCTION_HUD, false), "viewers follow the auction");
	}

	@Test
	void perSurfaceSwitches() {
		PrivacySettings p = new PrivacySettings();
		p.streamerMode = true;
		p.set(PrivacySettings.Surface.FINANCE, false);
		assertFalse(p.hides(PrivacySettings.Surface.FINANCE, false));
		p.set(PrivacySettings.Surface.AUCTION_HUD, true);
		assertTrue(p.hides(PrivacySettings.Surface.AUCTION_HUD, false));
	}

	@Test
	void previewAppliesTheRulesWithoutTurningItOn() {
		PrivacySettings p = new PrivacySettings();
		assertTrue(p.hides(PrivacySettings.Surface.FINANCE, true));
		assertFalse(p.streamerMode);
	}
}
