package bearmetalcarts;

import java.util.Locale;

public enum ChunkLoadingMode {
	DISALLOW,
	WHILE_MOVING,
	ALLOW;

	@Override
	public String toString() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
