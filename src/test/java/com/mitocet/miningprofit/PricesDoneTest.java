package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricesDoneTest {
	private static final String JSON = "{\"ts\":1000,\"items\":{\"A\":{\"name\":\"A\",\"v\":1},\"B\":{\"name\":\"B\",\"v\":2,\"fam\":\"x\"}}}";

	@Test
	void reportsItemCountAndAge() {
		Prices p = Prices.parse(JSON);
		assertEquals("Fiyatlar yenilendi: 2 item. Veri 7 dk önce toplanmış.", p.doneText(1000_000 + 7 * 60_000 + 5_000));
		assertEquals("Fiyatlar yenilendi: 2 item. Veri az önce toplanmış.", p.doneText(1000_000 + 30_000));
	}
}
