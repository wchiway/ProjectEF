package moze_intel.projecte.utils.text;

import me.towdium.pinin.PinIn;

/**
 * Chinese pinyin matching for client side text searches, backed by <a href="https://github.com/Towdium/PinIn">PinIn</a> (MIT licensed).
 * <p>
 * Supports full pinyin ("zhongguo"), initials ("zg") and any mix of pinyin, initials and the original characters ("zhong国", "zg国").
 *
 * @apiNote The underlying {@link PinIn} instance is not guaranteed to be thread safe, so only call this from the client thread.
 */
public final class PinyinMatcher {

	private PinyinMatcher() {
	}

	/**
	 * Checks if the given text contains the given key when matching Chinese characters by their pinyin.
	 *
	 * @param text Text to search in, expected to already be lowercase.
	 * @param key  Search key, expected to already be lowercase.
	 *
	 * @return {@code true} if the text contains a Chinese character and the key matches part of the text by pinyin.
	 */
	public static boolean contains(String text, String key) {
		//Skip the pinyin matcher (and loading its dictionary) entirely when there is nothing it could match differently than a plain contains check
		if (key.isEmpty() || !containsHan(text)) {
			return false;
		}
		return Holder.PIN_IN.contains(text, key);
	}

	private static boolean containsHan(String text) {
		for (int i = 0, length = text.length(); i < length; ) {
			int codePoint = text.codePointAt(i);
			if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
				return true;
			}
			i += Character.charCount(codePoint);
		}
		return false;
	}

	/**
	 * Lazily loads the pinyin dictionary the first time a search actually needs it.
	 */
	private static class Holder {

		private static final PinIn PIN_IN = new PinIn();
	}
}
