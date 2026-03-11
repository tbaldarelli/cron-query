# Development Guide

## Cross-Platform Development Practices

### Line Endings
- Git is configured to handle line endings via `.gitattributes`
- All text files use LF (`\n`) line endings for Linux compatibility
- Windows developers: Git will handle CRLF → LF conversion automatically

### File Paths
- Always use `os.path.join()` for building paths
- Use `os.path.sep` when building paths from root
- For system paths (like `/etc/crontab`), use `os.path.join(os.path.sep, "etc", "crontab")`

### Script Permissions
- Shell scripts (*.sh) are set to executable (755) during packaging
- The main runner script is set to executable (755) during packaging
- Windows developers: Permissions are handled by package_for_centos.py

### Development Environment
- Windows: Development with mock data
- Linux: Real crontab access
- Use package_for_centos.py for deployment packaging
- Test deployment script handles file permissions and line endings

### Testing Practices
- Run tests on Windows during development
- Test package on Linux before deployment
- Verify file permissions after deployment
- Check line endings on Linux after deployment

### Version Management

The project uses `bump2version` to manage version numbers across all implementations (Python, Groovy, Java Spring Boot).

**Installation:**
```bash
pip install bump2version
```

**Usage:**
```bash
# Bump patch version (1.3.1 → 1.3.2)
bump2version patch

# Bump minor version (1.3.1 → 1.4.0)
bump2version minor

# Bump major version (1.3.1 → 2.0.0)
bump2version major
```

**What it does:**
- Updates version in `setup.py`, `build.gradle`, `pom.xml`, and other configured files
- Creates a git commit with the version change
- Creates a git tag (e.g., `v1.3.2`)

**Configuration:**
Version management is configured in `.bumpversion.cfg` and updates:
- Python: `setup.py`, `src/python/cron_query/__init__.py`
- Groovy: `build.gradle`
- Java Spring Boot: `src/java/cron-query-service/pom.xml`
- Documentation: `CHANGELOG.md`, `man/man1/cron-query.1`

**Note:** If you encounter issues running `bump2version` directly, you may need to run it via Python CLI: `python -m bumpversion <part>`

### Common Issues
1. **Line Endings**
   - If scripts fail on Linux with "bad interpreter", check line endings
   - Use `dos2unix` if needed: `dos2unix script.sh`

2. **File Permissions**
   - If scripts aren't executable, check umask settings
   - Run `chmod +x script.sh` if needed

3. **Path Separators**
   - Windows uses backslash `\`
   - Linux uses forward slash `/`
   - Use `os.path.join()` to handle this automatically