package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListLayoutTest {
	@Test
	void fullSizeWhenItFits() {
		assertEquals(1f, ListHud.Layout.fit(150, 100, 300, 400));
	}

	@Test
	void shrinksToTheRoomLeftOfTheMenu() {
		// 1920x1080 at GUI scale 4: 480 wide, chest at 152; 148 px room for a 200 px list.
		assertEquals(0.74f, ListHud.Layout.fit(200, 100, 148, 400), 1e-4);
		// Too tall also shrinks.
		assertEquals(0.5f, ListHud.Layout.fit(100, 400, 300, 200), 1e-4);
	}

	@Test
	void hiddenWhenTooSmall() {
		assertTrue(ListHud.Layout.fit(300, 100, 100, 400) < ListHud.MIN_SCALE);
		assertEquals(0f, ListHud.Layout.fit(300, 100, -2, 400));
	}

	@Test
	void levelWithMenuTopButOnScreen() {
		assertEquals(40, ListHud.Layout.top(40, 100, 300, 2));
		assertEquals(198, ListHud.Layout.top(250, 100, 300, 2));
		assertEquals(2, ListHud.Layout.top(-10, 100, 300, 2));
	}
}
