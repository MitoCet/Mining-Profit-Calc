package com.mitocet.miningprofit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads the item blobs of the Hypixel profile API (ender chest, backpacks, personal vault): base64,
 * gzip, then a classic NBT compound whose list "i" holds one compound per slot. The SkyBlock id is
 * at tag.ExtraAttributes.id, the amount at Count. The site reads them the same way (scripts/nbt.mjs).
 */
public final class Nbt {
	private Nbt() {}

	/** Slots of one blob, as a list (index = slot), null for an empty slot. Used to split ender chest pages. */
	public static List<Map.Entry<String, Long>> slots(String base64) throws IOException {
		byte[] raw = Base64.getDecoder().decode(base64);
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw)))) {
			if (in.readByte() != 10) throw new IOException("NBT root is not a compound");
			in.readUTF();
			Object root = read(in, 10);
			List<Map.Entry<String, Long>> out = new ArrayList<>();
			Object list = root instanceof Map<?, ?> m ? m.get("i") : null;
			if (!(list instanceof List<?> all)) return out;
			for (Object slot : all) {
				Map.Entry<String, Long> e = null;
				if (slot instanceof Map<?, ?> s) {
					Object tag = s.get("tag");
					Object extra = tag instanceof Map<?, ?> t ? t.get("ExtraAttributes") : null;
					Object id = extra instanceof Map<?, ?> x ? x.get("id") : null;
					Object count = s.get("Count");
					if (id instanceof String sid) e = Map.entry(sid, count instanceof Number num ? num.longValue() : 1L);
				}
				out.add(e);
			}
			return out;
		}
	}

	private static Object read(DataInputStream in, int type) throws IOException {
		switch (type) {
			case 1: return in.readByte();
			case 2: return in.readShort();
			case 3: return in.readInt();
			case 4: return in.readLong();
			case 5: return in.readFloat();
			case 6: return in.readDouble();
			case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
			case 8: return in.readUTF();
			case 9: {
				int inner = in.readByte();
				int n = in.readInt();
				List<Object> list = new ArrayList<>(Math.max(0, n));
				for (int i = 0; i < n; i++) list.add(read(in, inner));
				return list;
			}
			case 10: {
				Map<String, Object> map = new LinkedHashMap<>();
				while (true) {
					int t = in.readByte();
					if (t == 0) return map;
					String name = in.readUTF();
					map.put(name, read(in, t));
				}
			}
			case 11: { int n = in.readInt(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); return a; }
			case 12: { int n = in.readInt(); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); return a; }
			default: throw new IOException("Unknown NBT tag " + type);
		}
	}
}
