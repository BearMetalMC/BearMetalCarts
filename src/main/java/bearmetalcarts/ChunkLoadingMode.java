package bearmetalcarts;

import java.util.Locale;

public enum ChunkLoadingMode {
	DISALLOW,
	WHILE_MOVING,
	ALLOW;

	// Lowercase so the /gamerule command literals and query output read
	// "disallow | while_moving | allow"; the codec still serializes name().
	@Override
	public String toString() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
