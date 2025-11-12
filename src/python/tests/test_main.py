#!/usr/bin/env python3
"""
Basic tests for the main module.
"""

import pytest
import sys
import shutil
from unittest.mock import patch
from cron_query.main import main, create_parser

# Check if crontab command is available
HAS_CRONTAB = shutil.which('crontab') is not None
requires_crontab = pytest.mark.skipif(not HAS_CRONTAB, reason="crontab command not available")


def test_parser_creation():
    """Test that the argument parser is created correctly."""
    parser = create_parser()
    assert parser.prog == 'cron-query'


def test_version_argument():
    """Test that --version works."""
    with pytest.raises(SystemExit):
        main(['--version'])


def test_help_argument():
    """Test that --help works."""
    with pytest.raises(SystemExit):
        main(['--help'])


@requires_crontab
def test_basic_query():
    """Test that a basic query runs without error."""
    result = main(['which jobs run on Saturday'])
    assert result == 0


@requires_crontab
def test_query_with_format():
    """Test query with different output formats."""
    result = main(['--format', 'json', 'which jobs run on Saturday'])
    assert result == 0


@requires_crontab
def test_query_with_source():
    """Test query with different sources."""
    result = main(['--source', 'system', 'which jobs run on Saturday'])
    assert result == 0


@requires_crontab
def test_verbose_mode():
    """Test verbose logging mode."""
    result = main(['--verbose', 'which jobs run on Saturday'])
    assert result == 0