package com.donutgamble.client.ui;

/**
 * A value that eases toward a target, frame-rate independent. Call {@link #to} once per frame
 * from render code; it returns the current value.
 */
public final class Anim {
	private float value;
	private long last;

	public Anim(float initial) {
		value = initial;
	}

	/** @param speed higher is snappier; ~12 settles in about a quarter second */
	public float to(float target, float speed) {
		long now = System.nanoTime();
		float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		value += (target - value) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 0.002f) {
			value = target;
		}
		return value;
	}

	public void snap(float v) {
		value = v;
	}

	public float get() {
		return value;
	}

	/** Smoothstep for 0..1 progress values. */
	public static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}
}
