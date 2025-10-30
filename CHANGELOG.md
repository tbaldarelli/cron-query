# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions CI/CD workflows for automated testing and releases

## [1.1.2] - 2025-10-30

### Fixed
- Test consolidation: moved tests from root to src/python/tests and updated CI configuration
- Query parser regex escaping bug in "at" preposition normalization
- Query parser minute validation for AM/PM time formats
- Formatter function signatures and test assertions
- JSON output test parsing for multi-line output
- Bumpversion configuration for month/year pattern matching

## [1.1.1] - 2025-09-24

### Added

#### Unicode and Special Character Support
- **Automatic Unicode Detection**: Detects terminal Unicode support via stdout encoding, locale, and environment variables
- **ASCII Fallback**: Graceful degradation to ASCII characters when Unicode is not supported
- **Special Characters**:
  - Bullet points: `•` (Unicode) / `*` (ASCII)  
  - Error symbols: `❌` (Unicode) / `X` (ASCII)
  - Navigation arrows: `→←` (Unicode) / `-><-` (ASCII)
- **Environment Variable Controls**:
  - `CRON_QUERY_FORCE_ASCII=1`: Force ASCII characters
  - `CRON_QUERY_FORCE_UNICODE=1`: Force Unicode characters

#### Enhanced Template System
- **Template Special Character Variables**: `{bullet}`, `{error}`, `{next}`, `{prev}`
- **Updated Predefined Templates**: All templates now use special characters for consistent formatting
- **Template Variables**: Added special character placeholders to all template formats
- **Template Help**: Updated `--template-help` with special character documentation

#### Cross-Platform Compatibility
- **Windows Support**: Full compatibility with Windows PowerShell and Command Prompt
- **Terminal Detection**: Robust Unicode support detection across different terminal environments
- **Cross-Platform Testing**: Comprehensive test suite for Unicode/ASCII modes

### Changed

#### Output Formatting
- **Consistent Character Usage**: All output formats now use centralized special character system
- **Error Messages**: Updated to use appropriate error symbols based on terminal capabilities
- **Pagination**: Navigation arrows automatically adapt to Unicode support
- **Template Rendering**: Special characters in templates now adapt to terminal capabilities

#### Code Architecture
- **New Module**: `special_chars.py` for centralized character management
- **Updated Dependencies**: Better integration between output formatter and character handling
- **Test Coverage**: Added comprehensive tests for Unicode detection and character rendering

### Fixed

#### Bug Fixes
- **Template Error Handling**: Fixed reference to undefined `_get_bullet_point` function
- **Character Encoding**: Resolved character display issues in different terminal environments
- **Template Variables**: Fixed template variable resolution for special characters

#### Compatibility
- **Terminal Support**: Improved compatibility across different terminal types
- **Environment Detection**: More robust detection of Unicode support capabilities
- **Error Handling**: Better error messages with appropriate symbols

### Development

#### Testing
- **Special Characters Module**: Complete test suite with 8 test cases covering all functionality
- **Environment Testing**: Tests for forced ASCII/Unicode modes via environment variables
- **Cross-Platform Testing**: Validated on Windows PowerShell with UTF-8 support
- **Template Testing**: Comprehensive testing of all predefined and custom templates

#### Documentation
- **Planning Documents**: Updated `requirements.md` and `design.md` to reflect Unicode features
- **Man Pages**: Updated both man page files with Unicode support and environment variable documentation
|- **Code Documentation**: Enhanced docstrings for new special character functionality
|
|---
|
|**Note**: This changelog represents the completion of Unicode support and template enhancement features. All changes maintain backward compatibility while adding significant new functionality for improved cross-platform and cross-terminal user experience.
