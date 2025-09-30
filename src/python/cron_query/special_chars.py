#!/usr/bin/env python3
"""
Special Characters - Handles Unicode vs ASCII character selection based on terminal capabilities.

This module provides functionality to select appropriate special characters (bullets, arrows, etc.)
based on terminal Unicode support, with fallbacks to ASCII when needed.
"""

import os
import sys
import locale
from typing import Dict, Optional


def _env_truthy(name: str) -> bool:
    """Check if environment variable indicates true."""
    v = os.getenv(name, "")
    return v not in ("", "0", "false", "False", "no", "No")


def _encoding_is_utf8(enc: str) -> bool:
    """Check if an encoding string indicates UTF-8."""
    if not enc:
        return False
    enc_low = enc.lower()
    return "utf-8" in enc_low or "utf8" in enc_low


def _locale_is_utf8() -> bool:
    """Check if the current locale indicates UTF-8 support."""
    for name in ("LC_ALL", "LC_CTYPE", "LANG"):
        val = os.getenv(name, "")
        if val and ("utf-8" in val.lower() or "utf8" in val.lower()):
            return True
    # Fall back to Python's preferred encoding
    try:
        pref = locale.getpreferredencoding(False)
        if _encoding_is_utf8(pref):
            return True
    except Exception:
        pass
    return False


def _wcwidth_supports(samples=("•", "❌", "→", "←")) -> bool:
    """Check if wcwidth supports our special characters."""
    try:
        import wcwidth
    except Exception:
        return True  # If wcwidth isn't available, don't block Unicode solely on this
    total = 0
    for ch in samples:
        w = wcwidth.wcwidth(ch)
        if w is None or w < 0:
            return False
        total += w
    return total > 0


def supports_unicode() -> bool:
    """
    Determine if the current environment supports Unicode output.
    
    This checks multiple factors including explicit overrides via environment
    variables, stdout encoding, locale settings, and terminal type.
    
    Returns:
        bool: True if Unicode is supported, False if ASCII should be used
    """
    # Explicit overrides take precedence
    if _env_truthy("CRON_QUERY_FORCE_ASCII"):
        return False
    if _env_truthy("CRON_QUERY_FORCE_UNICODE"):
        return True

    # stdout encoding is the strongest signal
    if hasattr(sys.stdout, "encoding") and _encoding_is_utf8(sys.stdout.encoding):
        # Optionally validate with wcwidth if present
        return _wcwidth_supports()

    # Locale signal
    if _locale_is_utf8():
        return _wcwidth_supports()

    # Weak TERM heuristic: some truly minimal terms are often unsafe
    term = os.getenv("TERM", "").lower()
    if term in ("dumb",):
        return False

    # No positive indicators of Unicode support found
    return False


class SpecialChars:
    """Container for special characters used in formatting."""
    def __init__(self, use_unicode: Optional[bool] = None):
        self._use_unicode = supports_unicode() if use_unicode is None else use_unicode
        
        # Define character mappings
        self._chars: Dict[str, tuple[str, str]] = {
            'bullet': ('•', '*'),
            'error': ('❌', 'X'),
            'next': ('→', '->'),
            'prev': ('←', '<-')
        }
    
    def get(self, name: str) -> str:
        """Get the appropriate character (Unicode or ASCII) for the given name."""
        unicode_char, ascii_char = self._chars.get(name, ('?', '?'))
        return unicode_char if self._use_unicode else ascii_char
    
    @property
    def bullet(self) -> str:
        """Get the bullet point character."""
        return self.get('bullet')
    
    @property
    def error(self) -> str:
        """Get the error symbol character."""
        return self.get('error')
    
    @property
    def next(self) -> str:
        """Get the "next" arrow character."""
        return self.get('next')
    
    @property
    def prev(self) -> str:
        """Get the "previous" arrow character."""
        return self.get('prev')


# Global instance for convenience
_special_chars = SpecialChars()


# Convenience functions for backwards compatibility
def get_bullet_char() -> str:
    """Get the appropriate bullet point character for the current environment."""
    return _special_chars.bullet


def get_error_symbol() -> str:
    """Get the appropriate error symbol for the current environment."""
    return _special_chars.error


def get_next_symbol() -> str:
    """Get the appropriate "next" arrow symbol for the current environment."""
    return _special_chars.next


def get_prev_symbol() -> str:
    """Get the appropriate "previous" arrow symbol for the current environment."""
    return _special_chars.prev