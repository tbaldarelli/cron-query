#!/usr/bin/env python3
"""Tests for the special_chars module."""

import os
import sys
import unittest
from unittest.mock import patch
from io import StringIO

from cron_query.special_chars import (
    SpecialChars,
    supports_unicode,
    get_bullet_char,
    get_error_symbol,
    get_next_symbol,
    get_prev_symbol,
    _env_truthy,
    _encoding_is_utf8,
    _locale_is_utf8
)


class MockStdout:
    """Mock stdout with settable encoding."""
    def __init__(self, encoding='UTF-8'):
        self.encoding = encoding


class TestSpecialChars(unittest.TestCase):
    """Test cases for special_chars module."""

    def setUp(self):
        # Clear any environment variables that might affect the tests
        for var in ['CRON_QUERY_FORCE_ASCII', 'CRON_QUERY_FORCE_UNICODE',
                   'TERM', 'LC_ALL', 'LC_CTYPE', 'LANG']:
            if var in os.environ:
                del os.environ[var]

    def test_env_truthy(self):
        """Test environment variable truth checking."""
        with patch.dict(os.environ, {'TEST_VAR': '1'}):
            self.assertTrue(_env_truthy('TEST_VAR'))
        
        with patch.dict(os.environ, {'TEST_VAR': '0'}):
            self.assertFalse(_env_truthy('TEST_VAR'))
        
        with patch.dict(os.environ, {'TEST_VAR': 'false'}):
            self.assertFalse(_env_truthy('TEST_VAR'))
        
        with patch.dict(os.environ, {'TEST_VAR': 'true'}):
            self.assertTrue(_env_truthy('TEST_VAR'))
        
        # Non-existent var should be false
        self.assertFalse(_env_truthy('NONEXISTENT_VAR'))

    def test_encoding_is_utf8(self):
        """Test UTF-8 encoding detection."""
        self.assertTrue(_encoding_is_utf8('UTF-8'))
        self.assertTrue(_encoding_is_utf8('utf8'))
        self.assertTrue(_encoding_is_utf8('UTF8'))
        self.assertTrue(_encoding_is_utf8('en_US.UTF-8'))
        self.assertFalse(_encoding_is_utf8('ASCII'))
        self.assertFalse(_encoding_is_utf8(''))
        self.assertFalse(_encoding_is_utf8(None))

    def test_locale_is_utf8(self):
        """Test UTF-8 locale detection."""
        # Test with UTF-8 locale
        with patch.dict(os.environ, {'LANG': 'en_US.UTF-8'}):
            self.assertTrue(_locale_is_utf8())
        
        # Test with non-UTF-8 locale
        # Mock getpreferredencoding to simulate legacy system behavior
        with patch.dict(os.environ, {'LANG': 'C'}), \
             patch('locale.getpreferredencoding', return_value='C'):
            self.assertFalse(_locale_is_utf8())
        
        # Test with multiple locale vars
        with patch.dict(os.environ, {
            'LC_ALL': 'C',
            'LC_CTYPE': 'en_US.UTF-8',
            'LANG': 'C'
        }):
            self.assertTrue(_locale_is_utf8())

    def test_supports_unicode_env_overrides(self):
        """Test environment variable overrides for Unicode support."""
        # Force ASCII
        with patch.dict(os.environ, {'CRON_QUERY_FORCE_ASCII': '1'}):
            self.assertFalse(supports_unicode())
        
        # Force Unicode
        with patch.dict(os.environ, {'CRON_QUERY_FORCE_UNICODE': '1'}):
            self.assertTrue(supports_unicode())
        
        # ASCII takes precedence if both are set
        with patch.dict(os.environ, {
            'CRON_QUERY_FORCE_ASCII': '1',
            'CRON_QUERY_FORCE_UNICODE': '1'
        }):
            self.assertFalse(supports_unicode())

    def test_supports_unicode_stdout_encoding(self):
        """Test Unicode support based on stdout encoding."""
        # Test with UTF-8 encoding
        mock_stdout = MockStdout(encoding='UTF-8')
        with patch('sys.stdout', mock_stdout):
            self.assertTrue(supports_unicode())
        
        # Test with ASCII encoding
        # Mock locale fallback to simulate legacy system
        mock_stdout = MockStdout(encoding='ascii')
        with patch('sys.stdout', mock_stdout), \
             patch('locale.getpreferredencoding', return_value='ascii'):
            self.assertFalse(supports_unicode())

    def test_supports_unicode_term(self):
        """Test Unicode support based on TERM environment variable."""
        # Test with dumb terminal and no other Unicode indicators
        with patch.dict(os.environ, {'TERM': 'dumb'}), \
             patch('sys.stdout', MockStdout(encoding='ascii')), \
             patch('locale.getpreferredencoding', return_value='ascii'):
            self.assertFalse(supports_unicode())
        
        # Test that UTF-8 encoding overrides dumb terminal
        with patch.dict(os.environ, {'TERM': 'dumb'}), \
             patch('sys.stdout', MockStdout(encoding='UTF-8')):
            self.assertTrue(supports_unicode())

    def test_special_chars_class(self):
        """Test SpecialChars class functionality."""
        # Test with forced Unicode
        chars = SpecialChars(use_unicode=True)
        self.assertEqual(chars.bullet, '•')
        self.assertEqual(chars.error, '❌')
        self.assertEqual(chars.next, '→')
        self.assertEqual(chars.prev, '←')
        
        # Test with forced ASCII
        chars = SpecialChars(use_unicode=False)
        self.assertEqual(chars.bullet, '*')
        self.assertEqual(chars.error, 'X')
        self.assertEqual(chars.next, '->')
        self.assertEqual(chars.prev, '<-')
        
        # Test unknown character
        self.assertEqual(chars.get('unknown'), '?')

    def test_convenience_functions(self):
        """Test the convenience functions with both Unicode and ASCII modes."""
        from cron_query.special_chars import _special_chars
        
        # Test with Unicode mode
        unicode_chars = SpecialChars(use_unicode=True)
        with patch.object(_special_chars, '_use_unicode', True), \
             patch.object(_special_chars, '_chars', unicode_chars._chars):
            self.assertEqual(get_bullet_char(), '•')
            self.assertEqual(get_error_symbol(), '❌')
            self.assertEqual(get_next_symbol(), '→')
            self.assertEqual(get_prev_symbol(), '←')
        
        # Test with ASCII mode
        ascii_chars = SpecialChars(use_unicode=False)
        with patch.object(_special_chars, '_use_unicode', False), \
             patch.object(_special_chars, '_chars', ascii_chars._chars):
            self.assertEqual(get_bullet_char(), '*')
            self.assertEqual(get_error_symbol(), 'X')
            self.assertEqual(get_next_symbol(), '->')
            self.assertEqual(get_prev_symbol(), '<-')


if __name__ == '__main__':
    unittest.main()