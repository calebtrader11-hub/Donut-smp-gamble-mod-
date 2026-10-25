package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
	@Test
	void missingSlotUsesDefault() {
		HudLayout layout = new HudLayout();
		HudLayout.Slot s = layout.slot("queue", 6, 6, true);
		assertEquals(6, s.x);
		assertTrue(s.visible);
		assertSame(s, layout.slot("queue", 99, 99, false), "created once, then kept");
	}

	@Test
	void clampKeepsBoxOnScreen() {
		assertArrayEquals(new int[]{0, 0}, HudLayout.clamp(-20, -5, 100, 50, 640, 360));
		assertArrayEquals(new int[]{540, 310}, HudLayout.clamp(600, 400, 100, 50, 640, 360));
		assertArrayEquals(new int[]{0, 0}, HudLayout.clamp(10, 10, 800, 400, 640, 360), "bigger than the screen pins to the corner");
	}

	@Test
	void snapsToEdgesCenterAndGrid() {
		assertEquals(0, HudLayout.snap(3, 100, 640));
		assertEquals(540, HudLayout.snap(537, 100, 640), "right edge");
		assertEquals(270, HudLayout.snap(268, 100, 640), "centered");
		assertEquals(124, HudLayout.snap(123, 100, 640), "4px grid");
	}

	@Test
	void scaleIsBounded() {
		HudLayout.Slot s = new HudLayout.Slot();
		s.scale = 1f;
		s.addScale(5f);
		assertEquals(2f, s.scale);
		s.addScale(-5f);
		assertEquals(0.5f, s.scale);
	}
}
